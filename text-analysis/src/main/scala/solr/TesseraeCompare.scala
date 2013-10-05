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
import collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import net.sf.ehcache.{Element, Ehcache}
import org.apache.solr.handler.tesserae.metrics.CommonMetrics
import org.tesserae.EhcacheManager
import java.io.File
import org.apache.solr.analysis.corpus.LatinCorpusDatabase

final class TesseraeCompareHandler extends RequestHandlerBase {

  import DataTypes._
  import TesseraeCompareHandler._

  private lazy val logger = LoggerFactory.getLogger(getClass)
  private val DEFAULT_MAX_THREADS =
    Runtime.getRuntime.availableProcessors() + 1

  private var maximumThreads = DEFAULT_MAX_THREADS
  private var workerPool: ForkJoinPool = null
  private var corpusDB: LatinCorpusDatabase = null
  private var cache: Ehcache = null
  private var filterPositions = false

  override def init(args: NamedList[_]) {
    super.init(args)

    val corpusDbLoc = args.get("corpusFreqDBLocation") match {
      case null => throw new IllegalArgumentException("Can't initialize TesseraeCompareHandler, missing 'corpusFreqDBLocation' parameter")
      case str: String => str
    }

    args.get("filterPositions") match {
      case null => // do nothing
      case str: String => filterPositions = str.toBoolean
      case b: Boolean => filterPositions = b
    }

    maximumThreads = args.get("threads") match {
      case null => DEFAULT_MAX_THREADS
      case str: String => str.toInt
      case i: Int => i
    }

    if (workerPool != null) {
      workerPool.shutdown()
    }

    workerPool = new ForkJoinPool(maximumThreads)
    logger.info("Initialized worker pool with a max of " + plural(maximumThreads, "thread", "threads"))

    val ehcacheStr = args.get("cacheName").asInstanceOf[String]
    cache = EhcacheManager.compareCache(Option(ehcacheStr))

    val corpusEhcacheStr = Option(args.get("corpusCacheName").asInstanceOf[String])
    val corpusDir = new File(corpusDbLoc)

    corpusDB = new LatinCorpusDatabase(corpusEhcacheStr, corpusDir)

    logger.info("Initialized Ehcache")
  }

  def handleRequestBody(req: SolrQueryRequest, rsp: SolrQueryResponse) {
    CommonMetrics.compareOps.mark()
    val ctx = CommonMetrics.compareTime.time()
    try {
      internalHandleRequestBody(req, rsp)
    } catch {
      case e: Exception =>
        CommonMetrics.compareExceptions.mark()
        logger.error("Unhandled exception: " + e.getMessage, e)
        throw e
    } finally {
      ctx.stop()
    }
  }

  private def internalHandleRequestBody(req: SolrQueryRequest, rsp: SolrQueryResponse) {
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
    val stopWords = params.getInt(TesseraeCompareParams.SW, DEFAULT_STOP_WORDS)
    val minCommonTerms = params.getInt(TesseraeCompareParams.MCT, DEFAULT_MIN_COMMON_TERMS)
    if (minCommonTerms < 2) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "min common terms can't be less than 2")
    }

    val callerStartListString = params.get(TesseraeCompareParams.SL, null)

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

    val readCache = params.getBool(TesseraeCompareParams.RC, true)
    val writeCache = params.getBool(TesseraeCompareParams.WC, true)

    val cacheKey =
      CacheKey(maxDistance, minCommonTerms, metric,
        params.get(TesseraeCompareParams.SQ), params.get(TesseraeCompareParams.SF), params.get(TesseraeCompareParams.SFL),
        params.get(TesseraeCompareParams.TQ), params.get(TesseraeCompareParams.TF), params.get(TesseraeCompareParams.TFL),
        stopWords, callerStartListString)

    var cachedResults: Option[CacheValue] = None
    if (readCache) {
      cache.get(cacheKey) match {
        case null => // not found
        case elem: Element => {
          // found maybe
          elem.getObjectValue match {
            case null =>
            // oh well
            case cv: CacheValue =>
              cachedResults = Some(cv)
            case _ =>
            // too bad
          }
        }
      }
    }

    val (sortedResults, sourceFieldList, targetFieldList, stoplist, fromCache) = cachedResults match {
      case None => {
        val ctx = CommonMetrics.uncachedCompareTime.time()
        try {
          val _stoplist: Set[String] = if (stopWords <= 0) {
            Set.empty
          } else {
            if (callerStartListString != null) {
              val normalized = callerStartListString.trim
              if (!normalized.isEmpty) {
                SPLIT_REGEX.split(normalized).toList.take(stopWords).toSet
              } else {
                corpusDB.getTopN(stopWords)
              }
            } else {
              corpusDB.getTopN(stopWords)
            }
          }

          val paramsVector = ParVector(sourceParams, targetParams)
          paramsVector.tasksupport = new ForkJoinTaskSupport(workerPool)
          val gatherInfoResults = paramsVector.map { qp: QueryParameters => gatherInfo(req, rsp, qp) }.toList
          val sourceInfo = gatherInfoResults(0)
          val targetInfo = gatherInfoResults(1)
          (compare(sourceInfo, targetInfo, maxDistance, _stoplist, minCommonTerms, metric),
            sourceInfo.fieldList, targetInfo.fieldList, _stoplist, false)
        } finally {
          ctx.stop()
        }
      }
      case Some(cv) => {
        (cv.results, cv.sourceFieldList, cv.targetFieldList, cv.stoplist, true)
      }
    }

    if (writeCache && !fromCache) {
      val value = CacheValue(sortedResults, sourceFieldList, targetFieldList, stoplist)
      val elem = new Element(cacheKey, value)
      cache.put(elem)
    }

    val timer = CommonMetrics.resultsFormattingTime.time()
    try {
      val results = sortedResults.drop(start).take(rows)
      val totalResultCount = sortedResults.length

      val searcher = req.getSearcher
      val reader = searcher.getIndexReader

      def processResult(result: CompareResult, sourceIfTrue: Boolean, populate: DocFields) = {
        val (docId, fieldList) =
          if (sourceIfTrue) (result.pair.sourceDoc, sourceFieldList)
          else (result.pair.targetDoc, targetFieldList)

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

      val stoplistList = new StopList
      stoplist.foreach { term =>
        stoplistList.add(term)
      }

      val matches = new TesseraeMatches
      var rank = start
      results.foreach { result =>
        rank += 1
        val m = new TesseraeMatch
        m.put("rank", new java.lang.Integer(rank))
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

      rsp.add("stopList", stoplistList)

      rsp.add("matchTotal", totalResultCount)
      rsp.add("matchCount", results.length)
      rsp.add("matchOffset", start)

      rsp.add("matches", matches)
      rsp.add("cached", fromCache)
    } finally {
      timer.stop()
    }
  }

  private def getMetric(distanceMetric: DistanceMetrics.Value, maxDistance: Int): Distance = {
    val dm = DistanceMetrics(distanceMetric)
    dm match {
      case None => throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid metric: " + distanceMetric.toString)
      case Some(d) => d
    }
  }

  private def deduplicate(docPairs: Map[DocumentPair, DocumentPairInfo]) = {
    var deduped = Map.empty[DocumentPair, (DocumentPair, DocumentPairInfo)]
    docPairs.foreach { case (pair, info) =>
      val first = pair.sourceDoc
      val second = pair.targetDoc
      val (a, b) = if (first > second) {
        (second, first)
      } else {
        (first, second)
      }

      val key = DocumentPair(a, b)
      if (!deduped.contains(key)) {
        deduped += key -> (pair, info)
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

  private def compare(source: QueryInfo, target: QueryInfo, maxDistance: Int, stoplist: Set[String],
                      minCommonTerms: Int, distanceMetric: DistanceMetrics.Value): List[CompareResult] = {

    // A mash maps one term to a set of document ids. Build them in parallel.
    val parvector = ParVector(source, target)
    parvector.tasksupport = new ForkJoinTaskSupport(workerPool)
    val mashResults = parvector.map { qi: QueryInfo => (buildMash(qi), buildTermFrequencies(qi)) }.toList
    val (sourceMash, sourceAggregateInfo) = mashResults(0)
    val (targetMash, targetAggregateInfo) = mashResults(1)

    // Get all document pairs with two or more terms in common
    val docPairs = deduplicate(findDocumentPairs(sourceMash, targetMash, stoplist).
      filter { case (_, terms) => terms.commonTerms.size >= minCommonTerms })
    val metric = getMetric(distanceMetric, maxDistance)

    // Build up information about the source and target frequencies
    val sortJobs = ParVector(sourceAggregateInfo, targetAggregateInfo)
    sortJobs.tasksupport = new ForkJoinTaskSupport(workerPool)
    val sortResults = sortJobs.map { ai: AggregateTermInfo => getSortedFrequencies(ai) }.toList
    val sortedSourceFrequencies = sortResults(0)._1
    val sortedTargetFrequencies = sortResults(1)._1
    val sourceFrequencies = sortResults(0)._2
    val targetFrequencies = sortResults(1)._2
    val frequencyInfo = SortedFrequencyInfo(sortedSourceFrequencies, sortedTargetFrequencies)

    // Calculate the scores and distances in parallel
    val parallelPairs = docPairs.par
    parallelPairs.tasksupport = new ForkJoinTaskSupport(workerPool)

    val mappedResults = parallelPairs.map { case (pair, pairInfo) =>
      val params = DistanceParameters(pair, pairInfo.commonTerms, source, target, frequencyInfo)
      metric.calculateDistance(params) match {
        case None => None
        case Some(distance) => {
          if (distance <= maxDistance || maxDistance <= 0) {
            var score = 0.0
            pairInfo.commonTerms.foreach { term =>
              val sourceScore = 1.0 / sourceFrequencies.getOrElse(term, -1.0)
              val targetScore = 1.0 / targetFrequencies.getOrElse(term, -1.0)
              score += sourceScore + targetScore
            }

            val finalScore = math.log(score / distance.toDouble)
            val result = CompareResult(pair, pairInfo.commonTerms, finalScore, distance)
            Some(result)
          } else {
            None
          }
        }
      }
    }.filter(_.isDefined).map(_.get)

    // Sort by score
    mappedResults.toList.sortWith { (a, b) => a.score > b.score }
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

  private def findDocumentPairs(sourceMash: Mash, targetMash: Mash,
                                stoplist: Set[String]): Map[DocumentPair, DocumentPairInfo] = {
    // Only get terms common to both source and target
    val commonTerms = findCommonTerms(sourceMash, targetMash, stoplist)

    import scala.collection.mutable
    val matchingPositions: mutable.Set[Match] = new mutable.HashSet[Match]

    commonTerms.foreach { case term =>
      val sourceDocs = sourceMash.termsToDocs.getOrElse(term, Set.empty)
      val targetDocs = targetMash.termsToDocs.getOrElse(term, Set.empty)
      sourceDocs.foreach { sourceDocument =>
        targetDocs.foreach { targetDocument =>
          if (sourceDocument != targetDocument) {
            val sourceInfo = sourceMash.docInfo(sourceDocument)
            val targetInfo = targetMash.docInfo(targetDocument)
            val sourceTermPositions = sourceInfo.termPositions(term)
            val targetTermPositions = targetInfo.termPositions(term)

            sourceTermPositions.foreach { stp =>
              val sourcePart = MatchPart(sourceInfo, stp.position)
              targetTermPositions.foreach { ttp =>
                val targetPart = MatchPart(targetInfo, ttp.position)
                val m = Match(sourcePart, targetPart)
                matchingPositions += m
              }
            }
          }
        }
      }
    }

    val pairInfo: mutable.Map[DocumentPair, (mutable.Set[String], mutable.Map[String, mutable.Set[TermPosition]],
      mutable.Map[String, mutable.Set[TermPosition]])] = new mutable.HashMap

    matchingPositions.foreach { matchPair =>
      val sourceInfo = matchPair.sourcePart.info
      val targetInfo = matchPair.targetPart.info
      val pair = DocumentPair(sourceInfo.docID, targetInfo.docID)

      val sourceTermInfo = sourceInfo.positionTerms(matchPair.sourcePart.position)
      val targetTermInfo = targetInfo.positionTerms(matchPair.targetPart.position)

      val (_commonTerms, _sourceTermPositions, _targetTermPositions) = pairInfo.get(pair) match {
        case Some((_ct, _spm, _tpm)) =>  (_ct, _spm, _tpm)
        case None => {
          val _ct = new mutable.HashSet[String]
          val _spm = new mutable.HashMap[String, mutable.Set[TermPosition]]
          val _tpm = new mutable.HashMap[String, mutable.Set[TermPosition]]
          pairInfo += pair -> (_ct, _spm, _tpm)
          (_ct, _spm, _tpm)
        }
      }

      val sourceTerms = sourceTermInfo.map { tp: TermPosition => tp.term }.toSet
      val targetTerms = targetTermInfo.map { tp: TermPosition => tp.term }.toSet
      val intersection = sourceTerms.intersect(targetTerms)

      if (!intersection.isEmpty) {
        val resolvedTerm = intersection.toList.sorted.mkString("-")

        _commonTerms += resolvedTerm

        val stp: mutable.Set[TermPosition] = _sourceTermPositions.get(resolvedTerm) match {
          case Some(_stp) => _stp
          case None => {
            val tmp = new mutable.HashSet[TermPosition]
            _sourceTermPositions += resolvedTerm -> tmp
            tmp
          }
        }

        val ttp: mutable.Set[TermPosition] = _targetTermPositions.get(resolvedTerm) match {
          case Some(_ttp) => _ttp
          case None => {
            val tmp = new mutable.HashSet[TermPosition]
            _targetTermPositions += resolvedTerm -> tmp
            tmp
          }
        }

        stp ++= sourceTermInfo
        ttp ++= targetTermInfo
      }
    }

    // Make it immutable
    pairInfo.map { case (pair, (commonTermSet, sourcePositionsMap, targetPositionsMap)) =>
      val sp = sourcePositionsMap.map { case (term: String, positions: mutable.Set[TermPosition]) => (term, positions.toSet) }
      val tp = targetPositionsMap.map { case (term: String, positions: mutable.Set[TermPosition]) => (term, positions.toSet) }
      val dpi = DocumentPairInfo(commonTermSet.toSet, sp.toMap, tp.toMap)
      pair -> dpi
    }.toMap
  }

  private def findCommonTerms(sourceMash: Mash, targetMash: Mash,
                              stoplist: Set[String]): Set[String] = {
    var intersection: Set[String] = Set.empty
    sourceMash.termsToDocs.foreach { case (term, _) =>
      if (targetMash.termsToDocs.contains(term) && !stoplist.contains(term)) {
        intersection += term
      }
    }

    intersection
  }

  private def buildMash(qi: QueryInfo): Mash = {
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
    val map: TermDocumentMap = sourceTermMash.map { case (term: String, set: mutable.Set[Int]) =>
      (term, set.toSet)
    }.toMap

    Mash(map, qi.termInfo)
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
    val listAndSet = searcher.getDocListAndSet(query, secondParam, null, 0, 100000)
    val dlit = listAndSet.docSet.iterator()

    var termInfo: QueryTermInfo = Map.empty

    var jobs: List[(Int, Terms)] = Nil
    while (dlit.hasNext) {
      val docId = dlit.nextDoc()
      val vec = reader.getTermVector(docId, searchField)
      if (vec != null) {
        jobs = (docId, vec) :: jobs
      }
    }

    val parvector = ParVector(jobs.toSeq :_*)
    parvector.tasksupport = new ForkJoinTaskSupport(workerPool)


    /**val mappedResults = if (filterPositions) {
      parvector.map { case (docId, vec) =>
        (docId, mapOneVector(reader, docId, vec.iterator(null), searchField))
      }
    } else {
      parvector.map { case (docId, vec) =>
        (docId, mapOneVectorUnfiltered(reader, docId, vec.iterator(null), searchField))
      }
    }*/

    val mappedResults = parvector.map { case (docId: Int, vec: Terms) =>
      (docId, mapOneVector(reader, docId, vec.iterator(null), searchField))
    }

    mappedResults.toList.foreach { case (docId: Int, dti: DocumentTermInfo) =>
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

  /*
  private def mapOneVector(reader: IndexReader, docId: Int, termsEnum: TermsEnum, field: String): DocumentTermInfo = {
    var rawText: BytesRef = termsEnum.next()
    var dpEnum: DocsAndPositionsEnum = null
    var positions: TermPositionsList = Nil
    var counts: TermCountMap = Map.empty

    // TODO: Fix this. This right here is a damn shame. Basically here's the story:
    // each position can have more than one term. In the latin analyzer, this strategy
    // is used to store, say, a verb-stem and noun-stem of an ambiguous word.
    // as it is currently, this might pick up the noun, this might pick up the verb.
    var knownPositions: Set[Int] = Set.empty

    while(rawText != null) {
      //val posInfo = PartOfSpeech.unapply(rawText.utf8ToString)
      val term = rawText.utf8ToString
      val freq = termsEnum.totalTermFreq.toInt

      /*
      if (logger.isInfoEnabled) {
        if (PartOfSpeech.isKeyword(posInfo)) {
          logger.info("Keyword: " + term)
        } else if (PartOfSpeech.isNoun(posInfo)) {
          logger.info("Noun: " + term)
        } else if (PartOfSpeech.isVerb(posInfo)) {
          logger.info("Verb: " + term)
        } else {
          logger.info("Unknown: " + term)
        }
      }
      */

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

      rawText = termsEnum.next()
    }

    DocumentTermInfo(docId, counts, positions)
  }
  */

  private def mapOneVector(reader: IndexReader, docId: Int, termsEnum: TermsEnum, field: String): DocumentTermInfo = {
    var rawText: BytesRef = termsEnum.next()
    var dpEnum: DocsAndPositionsEnum = null

    var counts: TermCountMap = Map.empty

    import scala.collection.mutable
    var termPos: Map[String, mutable.Set[TermPosition]] = Map.empty
    var posTerm: Map[(Int, Int), mutable.Set[TermPosition]] = Map.empty

    while (rawText != null) {
      val term = rawText.utf8ToString
      val freq = termsEnum.totalTermFreq.toInt

      dpEnum = termsEnum.docsAndPositions(null, dpEnum)
      if (dpEnum == null) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "termsEnum.docsAndPositions returned null")
      }

      dpEnum.nextDoc()
      for (i <- 0 until freq) {
        val pos = dpEnum.nextPosition
        val start = dpEnum.startOffset()
        val end = dpEnum.endOffset()
        val posTuple = (start, end)
        val entry = TermPosition(term, docId, pos, posTuple)

        val termPosSet: mutable.Set[TermPosition] = termPos.get(term) match {
          case Some(set) => set
          case None => {
            val tmp = new mutable.HashSet[TermPosition]
            termPos += term -> tmp
            tmp
          }
        }

        val posTermSet: mutable.Set[TermPosition] = posTerm.get(posTuple) match {
          case Some(set) => set
          case None => {
            val tmp = new mutable.HashSet[TermPosition]
            posTerm += posTuple -> tmp
            tmp
          }
        }

        termPosSet += entry
        posTermSet += entry

        val oldCount = counts.getOrElse(term, 0)
        counts += term -> (oldCount + 1)
      }

      rawText = termsEnum.next()
    }

    val imutTermPos: TermPositionsMap =
      termPos.map { case (term: String, set: mutable.Set[TermPosition]) => (term, set.toSet) }.toMap
    val imutPosTerm: PositionsTermMap =
      posTerm.map { case (posTuple: (Int, Int), set: mutable.Set[TermPosition]) => (posTuple, set.toSet) }.toMap

    DocumentTermInfo(docId, counts, imutTermPos, imutPosTerm)
  }

  def getDescription =
    "Tesserae two-document comparison"

  def getSource =
    "$URL: https://raw.github.com/eberle1080/tesserae-ng/master/text-analysis/src/main/scala/solr/TesseraeCompare.scala $"
}

object TesseraeCompareHandler {
  val DEFAULT_MAX_DISTANCE = 0 // 0 = no max
  val DEFAULT_STOP_WORDS = 10
  val DEFAULT_MIN_COMMON_TERMS = 2 // can't be less than 2
  val DEFAULT_HIGHLIGHT = false
  val SPLIT_REGEX = ",| ".r
}
