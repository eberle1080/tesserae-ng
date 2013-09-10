/**
 * Author: Chris Eberle <eberle1080@gmail.com>
 *
 * Compare one set of solr documents to another
 */

package org.apache.solr.handler.tesserae

import org.apache.solr.handler._
import org.apache.solr.request.SolrQueryRequest
import org.apache.solr.response.SolrQueryResponse
import org.apache.solr.common.util.NamedList
import org.apache.solr.search._
import org.apache.solr.common.params.CommonParams
import org.apache.solr.common.SolrException
import org.apache.lucene.index.{Terms, DocsAndPositionsEnum, TermsEnum, IndexReader}
import org.apache.lucene.util.BytesRef
import org.slf4j.LoggerFactory
import collection.parallel.immutable.ParVector
import collection.parallel.mutable.{ParArray => MutableParArray}
import collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool

final class TesseraeCompareHandler extends RequestHandlerBase {

  import DataTypes._
  import TesseraeCompareHandler._

  private lazy val logger = LoggerFactory.getLogger(getClass)
  private val DEFAULT_MAX_THREADS =
    Runtime.getRuntime.availableProcessors() + 1

  private var maximumThreads = DEFAULT_MAX_THREADS
  private var workerPool: ForkJoinPool = null

  override def init(args: NamedList[_]) {
    super.init(args)

    val threadCount = args.get("threads")
    maximumThreads = threadCount match {
      case null => DEFAULT_MAX_THREADS
      case str: String => str.toInt
      case i: Int => i
    }

    if (workerPool != null) {
      workerPool.shutdown()
    }

    workerPool = new ForkJoinPool(maximumThreads)
    logger.info("Initialized worker pool with a max of " + plural(maximumThreads, "thread", "threads"))
  }

  def handleRequestBody(req: SolrQueryRequest, rsp: SolrQueryResponse) {
    val params = req.getParams
    val returnFields = new SolrReturnFields(req)
    rsp.setReturnFields(returnFields)

    rsp.add("params", req.getParams.toNamedList)

    var flags = 0
    if (returnFields.wantsScore) {
      flags |= SolrIndexSearcher.GET_SCORES
    }

    val start = params.getInt(CommonParams.START, 0)
    val rows = params.getInt(CommonParams.ROWS, 10)
    val maxDistance = params.getInt(TesseraeCompareParams.MD, DEFAULT_MAX_DISTANCE)
    val minCommonTerms = params.getInt(TesseraeCompareParams.MCT, DEFAULT_MIN_COMMON_TERMS)
    if (minCommonTerms < 2) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "min common terms can't be less than 2")
    }

    val metricStr = params.get(TesseraeCompareParams.METRIC)
    val metric = if (metricStr == null) {
      DistanceMetrics.DEFAULT_METRIC
    } else {
      DistanceMetrics(metricStr) match {
        case None => throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid metric: " + metricStr)
        case Some(m) => m
      }
    }

    val sourceParams = QueryParameters(TesseraeCompareParams.SQ, TesseraeCompareParams.SF, TesseraeCompareParams.SFL)
    val targetParams = QueryParameters(TesseraeCompareParams.TQ, TesseraeCompareParams.TF, TesseraeCompareParams.TFL)

    val paramsVector = ParVector(sourceParams, targetParams)
    paramsVector.tasksupport = new ForkJoinTaskSupport(workerPool)
    val gatherInfoResults = paramsVector.map(qp => gatherInfo(req, rsp, qp))
    val sourceInfo = gatherInfoResults(0)
    val targetInfo = gatherInfoResults(1)

    val (results, totalResultCount) = {
      // The bulk of the work happens here
      val sortedResults = compare(sourceInfo, targetInfo, maxDistance, minCommonTerms, metric)
      val total = sortedResults.length
      (sortedResults.drop(start).take(rows), total)
    }

    val searcher = req.getSearcher
    val reader = searcher.getIndexReader

    def processResult(result: CompareResult, sourceIfTrue: Boolean, populate: DocFields) = {
      val (docId, fieldList) = sourceIfTrue match {
        case true =>
          (result.pair.sourceDoc, sourceInfo.fieldList)
        case false =>
          (result.pair.targetDoc, targetInfo.fieldList)
      }

      val doc = reader.document(docId)
      var found = 0
      fieldList.foreach { fieldName =>
        val fieldValue = doc.get(fieldName)
        if (fieldValue != null) {
          found += 1
          populate.put(fieldName, fieldValue)
        }
      }

      found
    }

    val matches = new TesseraeMatches
    results.foreach { result =>
      val m = new TesseraeMatch
      m.put("score", new java.lang.Double(result.score))
      m.put("distance", new java.lang.Double(result.distance))

      val terms = new TermList
      result.commonTerms.toList.sorted.foreach { term =>
        terms.add(term)
      }

      m.put("terms", terms)

      val sdoc = new TesseraeDoc
      val tdoc = new TesseraeDoc

      val sourceFields = new DocFields
      if (processResult(result, sourceIfTrue=true, sourceFields) > 0) {
        sdoc.put("fields", sourceFields)
      }

      val targetFields = new DocFields
      if (processResult(result, sourceIfTrue=false, targetFields) > 0) {
        tdoc.put("fields", targetFields)
      }

      m.put("source", sdoc)
      m.put("target", tdoc)

      matches.add(m)
    }

    rsp.add("matchTotal", totalResultCount)
    rsp.add("matchCount", results.length)
    rsp.add("matchOffset", start)

    rsp.add("matches", matches)
  }

  private def getMetric(distanceMetric: DistanceMetrics.Value, maxDistance: Int): Distance = {
    val dm = DistanceMetrics(distanceMetric)
    dm match {
      case None => throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid metric: " + distanceMetric.toString)
      case Some(d) => d
    }
  }

  private def deduplicate(docPairs: Map[DocumentPair, Set[String]]) = {
    var deduped = Map.empty[DocumentPair, (DocumentPair, Set[String])]
    docPairs.foreach { case (pair, set) =>
      val first = pair.sourceDoc
      val second = pair.targetDoc
      val (a, b) = if (first > second) {
        (second, first)
      } else {
        (first, second)
      }

      val key = DocumentPair(a, b)
      if (!deduped.contains(key)) {
        deduped += key -> (pair, set)
      }
    }

    deduped.map { case (_, (pair, set)) => pair -> set }
  }

  private def getSortedFrequencies(freqInfo: AggregateTermInfo): (SortedFrequencies, FrequencyMap) = {
    var frequencies: SortedFrequencies = Nil
    var freqMap: FrequencyMap = Map.empty
    val total = freqInfo.totalTermCount.toDouble
    freqInfo.termCounts.foreach { case (term, count) =>
      val frequency = count.toDouble / total
      frequencies = TermFrequencyEntry(term, frequency) :: frequencies
      freqMap += term -> frequency
    }

    val sorted = frequencies.sortWith { case (a, b) => a.frequency < b.frequency }
    (sorted, freqMap)
  }

  private def compare(source: QueryInfo, target: QueryInfo, maxDistance: Int,
                      minCommonTerms: Int, distanceMetric: DistanceMetrics.Value): List[CompareResult] = {

    // A mash maps one term to a set of document ids. Build them in parallel.
    val parvector = ParVector(source, target)
    parvector.tasksupport = new ForkJoinTaskSupport(workerPool)
    val mashResults = parvector.map(qi => (buildMash(qi), buildTermFrequencies(qi)))
    val (sourceMash, sourceAggregateInfo) = mashResults(0)
    val (targetMash, targetAggregateInfo) = mashResults(1)

    // Get all document pairs with two or more terms in common
    val docPairs = deduplicate(findDocumentPairs(sourceMash, targetMash).filter { case (_, terms) => terms.size >= minCommonTerms })
    val metric = getMetric(distanceMetric, maxDistance)

    // Build up information about the source and target frequencies
    val sortJobs = ParVector(sourceAggregateInfo, targetAggregateInfo)
    sortJobs.tasksupport = new ForkJoinTaskSupport(workerPool)
    val sortResults = sortJobs.map { ai => getSortedFrequencies(ai) }
    val sortedSourceFrequencies = sortResults(0)._1
    val sortedTargetFrequencies = sortResults(1)._1
    val sourceFrequencies = sortResults(0)._2
    val targetFrequencies = sortResults(1)._2
    val frequencyInfo = SortedFrequencyInfo(sortedSourceFrequencies, sortedTargetFrequencies)

    // Calculate the scores and distances in parallel
    val parallelPairs = docPairs.par
    parallelPairs.tasksupport = new ForkJoinTaskSupport(workerPool)
    val mappedResults = parallelPairs.map { case (pair, terms) =>
      val params = DistanceParameters(pair, terms, source, target, frequencyInfo)
      metric.calculateDistance(params) match {
        case None => None
        case Some(distance) => {
          if (distance <= maxDistance || maxDistance <= 0) {
            var score = 0.0
            terms.foreach { term =>
              val sourceScore = 1.0 / sourceFrequencies.getOrElse(term, -1.0)
              val targetScore = 1.0 / targetFrequencies.getOrElse(term, -1.0)
              score += sourceScore + targetScore
            }

            val finalScore = math.log(score / distance.toDouble)
            val result = CompareResult(pair, terms, finalScore, distance)
            Some(result)
          } else {
            None
          }
        }
      }
    }.filter(_.isDefined).map(_.get)

    val results = mappedResults.toList

    // Return the results sorted by score (descending)
    results.sortWith { (a, b) => a.score > b.score }
  }

  private def buildTermFrequencies(queryInfo: QueryInfo): AggregateTermInfo = {
    var termCounts: TermCountMap = Map.empty
    var totalWords = 0

    queryInfo.termInfo.foreach { case (docId, dti) =>
      dti.termCounts.foreach { case (term, count) =>
        totalWords += count
        termCounts.get(term) match {
          case None => termCounts += term -> count
          case Some(lastCount) => termCounts += term -> (lastCount + count)
        }
      }
    }

    AggregateTermInfo(termCounts, totalWords)
  }

  private def findDocumentPairs(sourceMash: Map[String, Set[Int]], targetMash: Map[String, Set[Int]]): Map[DocumentPair, Set[String]] = {
    // Only get terms common to both source and target
    val sharedMash = intersetMashes(sourceMash, targetMash)

    import scala.collection.mutable
    var pairCounts: Map[DocumentPair, mutable.Set[String]] = Map.empty
    sharedMash.foreach { case (term, _) =>
      val ss = sourceMash.getOrElse(term, Set.empty)
      val ts = targetMash.getOrElse(term, Set.empty)

      ss.foreach { sourceDocument =>
        ts.foreach { targetDocument =>
          if (sourceDocument != targetDocument) {
            val pair = DocumentPair(sourceDocument, targetDocument)
            val termSet: mutable.Set[String] = if (pairCounts.contains(pair)) {
              pairCounts(pair)
            } else {
              val tmp = new mutable.HashSet[String]
              pairCounts += pair -> tmp
              tmp
            }

            termSet += term
          }
        }
      }
    }

    // Make it immutable
    pairCounts.map { case (pair, terms) =>
      (pair, terms.toSet)
    }
  }

  private def intersetMashes(sourceMash: Map[String, Set[Int]], targetMash: Map[String, Set[Int]]): Map[String, Set[Int]] = {
    var intersection: Map[String, Set[Int]] = Map.empty
    sourceMash.foreach { case (term, sourceDocSet) =>
      targetMash.get(term) match {
        case None => // oh well
        case Some(targetDocSet) => {
          val docsSharingTerm = sourceDocSet.intersect(targetDocSet)
          intersection += term -> docsSharingTerm
        }
      }
    }

    intersection
  }

  private def buildMash(qi: QueryInfo): Map[String, Set[Int]] = {
    import scala.collection.mutable
    var sourceTermMash: Map[String, mutable.Set[Int]] = Map.empty

    qi.termInfo.foreach { case (docId, termInfo) =>
      termInfo.termCounts.keySet.foreach { term =>
        val docSet: mutable.Set[Int] = if (sourceTermMash.contains(term)) {
          sourceTermMash(term)
        } else {
          val tmp = new mutable.HashSet[Int]
          sourceTermMash += term -> tmp
          tmp
        }

        docSet += docId
      }
    }

    // Make it immutable
    sourceTermMash.map { case (term, set) =>
      (term, set.toSet)
    }
  }

  private def gatherInfo(req: SolrQueryRequest, rsp: SolrQueryResponse, qParams: QueryParameters): QueryInfo = {
    val params = req.getParams
    val defType = params.get(QueryParsing.DEFTYPE, QParserPlugin.DEFAULT_QTYPE)

    val queryStr = params.get(qParams.qParamName)
    if (queryStr == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "missing parameter: " + qParams.qParamName)
    }

    val parser = QParser.getParser(queryStr, defType, req)
    val query = parser.getQuery
    //val sorter = parser.getSort(true)
    val searcher = req.getSearcher
    val reader = searcher.getIndexReader

    val searchField = params.get(qParams.searchFieldParamName)
    if (searchField == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "missing parameter: " + qParams.searchFieldParamName)
    }

    val fieldList = params.get(qParams.fieldListParamName)
    if (fieldList == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "missing parameter: " + qParams.fieldListParamName)
    }

    val schema = req.getSchema
    val sf = schema.getFieldOrNull(searchField)
    if (sf == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid search field: " + searchField)
    }
    if (!sf.storeTermVector()) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "field " + searchField + " doesn't store term vectors")
    }
    if (!sf.storeTermPositions()) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "field " + searchField + " doesn't store term positions")
    }
    if (!sf.storeTermOffsets()) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "field " + searchField + " doesn't store term offsets")
    }

    val returnFields = SPLIT_REGEX.split(fieldList).toList
    if (returnFields.isEmpty) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "field list can't be empty: " + qParams.fieldListParamName)
    }

    returnFields.foreach { fieldName =>
      val field = schema.getFieldOrNull(fieldName)
      if (field == null) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid field in field list: " + fieldName)
      }
    }

    val secondParam: java.util.List[org.apache.lucene.search.Query] = null
    val listAndSet = searcher.getDocListAndSet(query, secondParam, null, 0, 10)
    val dlit = listAndSet.docSet.iterator()

    var termInfo: QueryTermInfo = Map.empty

    var jobs: List[(Int, Terms)] = Nil
    while (dlit.hasNext) {
      val docId = dlit.nextDoc()
      val vec = reader.getTermVector(docId, searchField)
      if (vec != null) {
        jobs = jobs ::: List((docId, vec))
      }
    }

    val parvector = ParVector(jobs.toSeq :_*)
    parvector.tasksupport = new ForkJoinTaskSupport(workerPool)
    val mappedResults = parvector.map { case (docId, vec) =>
      (docId, mapOneVector(reader, docId, vec.iterator(null), searchField))
    }

    mappedResults.toList.foreach { case (docId, dti) =>
      termInfo += docId -> dti
    }

    logger.info("Using query `" + queryStr + "' found " + plural(termInfo.size, "result", "results"))
    QueryInfo(termInfo, returnFields, (offset, count) => listAndSet.docList)
  }

  private def plural(i: Int, singular: String, plural: String): String = {
    i match {
      case 1 => "1 " + singular
      case n => n + " " + plural
    }
  }

  private def mapOneVector(reader: IndexReader, docId: Int, termsEnum: TermsEnum, field: String): DocumentTermInfo = {
    var text: BytesRef = termsEnum.next()
    var dpEnum: DocsAndPositionsEnum = null
    var positions: TermPositionsList = Nil
    var counts: TermCountMap = Map.empty

    // TODO: Fix this. This right here is a damn shame. Basically here's the story:
    // each position can have more than one term. In the latin analyzer, this strategy
    // is used to store, say, a verb-stem and noun-stem of an ambiguous word.
    // as it is currently, this might pick up the noun, this might pick up the verb.
    var knownPositions: Set[Int] = Set.empty

    while(text != null) {
      val term = text.utf8ToString
      val freq = termsEnum.totalTermFreq.toInt
      //var termType: Option[Attribute] = None

      dpEnum = termsEnum.docsAndPositions(null, dpEnum)
      if (dpEnum == null) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "termsEnum.docsAndPositions returned null")
      }

      dpEnum.nextDoc()
      for (i <- 0 until freq) {
        val pos = dpEnum.nextPosition
        if (!knownPositions.contains(pos)) {
          val entry = TermPositionsListEntry(term, pos)
          positions = entry :: positions
          knownPositions += pos
          val oldCount = counts.getOrElse(term, 0)
          counts += term -> (oldCount + 1)
        }
      }

      text = termsEnum.next()
    }

    DocumentTermInfo(docId, counts, positions)
  }

  def getDescription =
    "Tesserae two-document comparison"

  def getSource =
    "$URL: https://raw.github.com/eberle1080/tesserae-ng/master/text-analysis/src/main/scala/solr/TesseraeCompare.scala $"
}

object TesseraeCompareHandler {
  val DEFAULT_MAX_DISTANCE = 0 // 0 = no max
  val DEFAULT_MIN_COMMON_TERMS = 2 // can't be less than 2
  val DEFAULT_HIGHLIGHT = false
  val SPLIT_REGEX = ",| ".r
}
