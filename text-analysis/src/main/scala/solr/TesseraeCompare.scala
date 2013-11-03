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

import collection.mutable.{Map => MutableMap, Set => MutableSet,
                           HashMap => MutableHashMap, HashSet => MutableHashSet}

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
          val _stoplist: MutableSet[String] = if (stopWords <= 0) {
            new MutableHashSet
          } else {
            if (callerStartListString != null) {
              val normalized = callerStartListString.trim
              if (!normalized.isEmpty) {
                val tmp = new MutableHashSet[String]
                SPLIT_REGEX.split(normalized).toList.foreach { stopWord =>
                  if (tmp.size < stopWords) {
                    tmp += stopWord
                  }
                }

                tmp
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

  private def deduplicate(docPairs: MutableMap[DocumentPair, DocumentPairInfo]): Map[DocumentPair, DocumentPairInfo] = {
    val deduped = new MutableHashMap[DocumentPair, (DocumentPair, DocumentPairInfo)]
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

    deduped.map { case (_, (pair, set)) => pair -> set }.toMap
  }

  private def getSortedFrequencies(freqInfo: AggregateTermInfo): (SortedFrequencies, FrequencyMap) = {
    var frequencies: SortedFrequencies = Nil
    var freqMap: FrequencyMap = new MutableHashMap
    val total = freqInfo.totalTermCount.toDouble
    freqInfo.termCounts.foreach { case (term, count) =>
      val frequency = count.toDouble / total
      frequencies = TermFrequencyEntry(term, frequency) :: frequencies
      freqMap += term -> frequency
    }

    val sorted = frequencies.sortWith { case (a, b) => a.frequency < b.frequency }
    (sorted, freqMap)
  }

  private def compare(source: QueryInfo, target: QueryInfo, maxDistance: Int, stoplist: MutableSet[String],
                      minCommonTerms: Int, distanceMetric: DistanceMetrics.Value): List[CompareResult] = {

    // A mash maps one term to a set of document ids. Build them in parallel.
    val parvector = ParVector(source, target)
    parvector.tasksupport = new ForkJoinTaskSupport(workerPool)

    val mashAndFreqResults = parvector.map { qi: QueryInfo =>
      val mash = buildMash(qi)
      val frequencyInfo = buildTermFrequencies(qi)
      val sorted = getSortedFrequencies(frequencyInfo)
      (mash, sorted._1, sorted._2)
    }.toList

    val (sourceMash, sortedSourceFrequencies, sourceFrequencies) = mashAndFreqResults(0)
    val (targetMash, sortedTargetFrequencies, targetFrequencies) = mashAndFreqResults(1)

    // Find the overlapping documents
    val fpTimer = CommonMetrics.findDocumentPairs.time()
    val startTime = System.currentTimeMillis
    val foundPairs = try {
      findDocumentPairs(sourceMash, targetMash, stoplist)
    } finally {
      fpTimer.stop()
      val endTime = System.currentTimeMillis
      logger.info("Spent " + (endTime - startTime) + " ms in findDocumentPairs")
    }

    // Only consider documents with 2 or more unique terms in common
    val filteredPairs = foundPairs.filter { case (_, dpi) => {
      dpi.targetTerms.size >= minCommonTerms && dpi.sourceTerms.size >= minCommonTerms
    }}

    // If there's an (a, b) and a (b, a) match, remove one
    val docPairs = deduplicate(filteredPairs)

    val distMetric = getMetric(distanceMetric, maxDistance)

    // Build up information about the source and target frequencies
    val frequencyInfo = SortedFrequencyInfo(sortedSourceFrequencies, sortedTargetFrequencies)

    // Calculate the scores and distances in parallel
    val parallelPairs = docPairs.par
    parallelPairs.tasksupport = new ForkJoinTaskSupport(workerPool)

    val mappedResults = parallelPairs.map { case (pair: DocumentPair, pairInfo: DocumentPairInfo) =>
      distMetric.calculateDistance(DistanceParameters(pair, source, target, frequencyInfo)) match {
        case None => None
        case Some(distance) => {
          if (distance <= maxDistance || maxDistance <= 0) {
            var score = 0.0
            var skip = false

            val seenNonForms: MutableSet[String] = new MutableHashSet

            pairInfo.sourceTerms.foreach { case (TermPosition(term, _, _, _), nonForms) =>
              seenNonForms += nonForms.toList.sorted.mkString("-")
              val sourceScore = 1.0 / sourceFrequencies.getOrElse(term, -1.0)
              if (sourceScore < 0.0) {
                logger.warn("No source term frequency information available for term: `" + term + "'")
                skip = true
              } else {
                score += sourceScore
              }
            }

            pairInfo.targetTerms.foreach { case (TermPosition(term, _, _, _), nonForms) =>
              seenNonForms += nonForms.toList.sorted.mkString("-")
              val targetScore = 1.0 / targetFrequencies.getOrElse(term, -1.0)
              if (targetScore < 0.0) {
                logger.warn("No target term frequency information available for term: `" + term + "'")
                skip = true
              } else {
                score += targetScore
              }
            }

            val finalScore = math.log(score / distance.toDouble)
            if (skip || finalScore < 0.0 || finalScore.isNaN) {
              None
            } else {
              val result = CompareResult(pair, seenNonForms, finalScore, distance)
              Some(result)
            }
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
    val termCounts: MutableMap[String, Int] = new MutableHashMap
    var totalWords = 0

    queryInfo.termInfo.foreach { case (docId, dti) =>
      dti.nonFormTermCounts.foreach { case (term, count) =>
        dti.nf2f.get(term).map { form =>
          totalWords += count
          termCounts.get(form.term) match {
            case None => termCounts += form.term -> count
            case Some(lastCount) => termCounts += form.term -> (lastCount + count)
          }
        }
      }
    }

    AggregateTermInfo(termCounts, totalWords)
  }

  private def findDocumentPairs(sourceMash: Mash, targetMash: Mash,
                                stoplist: MutableSet[String]): MutableMap[DocumentPair, DocumentPairInfo] = {

    val match_target: TargetToSourceToF2NF = new MutableHashMap
    val match_source: TargetToSourceToF2NF = new MutableHashMap

    sourceMash.nonFormsToDocs.foreach { case (term, sourceDocs) =>
      if (!targetMash.nonFormsToDocs.contains(term)) {
        // continue
      } else if (stoplist.contains(term)) {
        // continue
      } else {
        val targetDocs = targetMash.nonFormsToDocs(term)
        sourceDocs.foreach { sourceDocId =>
          val sourceInfo = sourceMash.docInfo(sourceDocId)
          val sourceForm = sourceInfo.nf2f.get(term) match {
            case Some(form) => form
            case None => {
              logger.warn("Unable to map source term `" + term + "' to a form")
              TermPosition(term, sourceDocId, -1, (-1, -1))
            }
          }

          targetDocs.foreach { targetDocId =>
            val targetInfo = targetMash.docInfo(targetDocId)

            val targetForm = targetInfo.nf2f.get(term) match {
              case Some(form) => form
              case None => {
                logger.warn("Unable to map target term `" + term + "' to a form")
                TermPosition(term, targetDocId, -1, (-1, -1))
              }
            }

            val mtSet = match_target.getOrElseUpdate(targetDocId, new MutableHashMap).
              getOrElseUpdate(sourceDocId, new MutableHashMap).
              getOrElseUpdate(targetForm, new MutableHashSet)

            val msSet = match_source.getOrElseUpdate(targetDocId, new MutableHashMap).
              getOrElseUpdate(sourceDocId, new MutableHashMap).
              getOrElseUpdate(sourceForm, new MutableHashSet)

            mtSet += term
            msSet += term
          }
        }
      }
    }

    val pairInfo: MutableMap[DocumentPair, DocumentPairInfo] = new MutableHashMap

    match_target.foreach { case (targetDocId, sourceToF2NF) =>
      sourceToF2NF.foreach { case (sourceDocId, f2nf) =>
        if (sourceDocId == targetDocId) {
          // continue
        } else {
          val l1: SourceToF2NF = match_source.getOrElse(targetDocId, new MutableHashMap)
          if (f2nf.size < 2) {
            sourceToF2NF.remove(sourceDocId)
            l1.remove(sourceDocId)
            // continue
          } else {
            val l2: FormToNonForms = l1.getOrElse(sourceDocId, new MutableHashMap)
            if (l2.size < 2) {
              sourceToF2NF.remove(sourceDocId)
              l1.remove(sourceDocId)
              // continue
            } else {
              val seenForms: MutableSet[String] = new MutableHashSet
              f2nf.keySet.foreach { form =>
                seenForms += form.term
              }

              if (seenForms.size < 2) {
                // continue
              } else {
                seenForms.clear()
                l2.keySet.foreach { form =>
                  seenForms += form.term
                }

                if (seenForms.size < 2) {
                  // continue
                } else {
                  val pair = DocumentPair(sourceDocId, targetDocId)
                  val dpi = pairInfo.getOrElseUpdate(pair, DocumentPairInfo(new MutableHashMap, new MutableHashMap))
                  dpi.targetTerms ++= f2nf
                  dpi.sourceTerms ++= l2
                }
              }
            }
          }
        }
      }
    }

    pairInfo
  }

  private def buildMash(qi: QueryInfo): Mash = {
    val formMap: TermDocumentMap = new MutableHashMap
    val nonFormMap: TermDocumentMap = new MutableHashMap

    qi.termInfo.foreach { case (docId, termInfo) =>
      termInfo.formTermCounts.keySet.foreach { term =>
        val docSet = formMap.getOrElseUpdate(term, new MutableHashSet[Int])
        docSet += docId
      }
      termInfo.nonFormTermCounts.keySet.foreach { term =>
        val docSet = nonFormMap.getOrElseUpdate(term, new MutableHashSet[Int])
        docSet += docId
      }
    }

    Mash(formMap, nonFormMap, qi.termInfo)
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

    var termInfo: QueryTermInfo = new MutableHashMap

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

  private def mapOneVector(reader: IndexReader, docId: Int, termsEnum: TermsEnum, field: String): DocumentTermInfo = {
    var rawText: BytesRef = termsEnum.next()
    var dpEnum: DocsAndPositionsEnum = null

    val formCounts: TermCountMap = new MutableHashMap
    val nonFormCounts: TermCountMap = new MutableHashMap
    val termPos: TermPositionsMap = new MutableHashMap
    val posInfo: MutableMap[(Int, Int), TermPositionInfo] = new MutableHashMap

    while (rawText != null) {
      val termText = rawText.utf8ToString
      val freq = termsEnum.totalTermFreq.toInt
      val (term, isForm) = if (termText.startsWith("_")) {
        (termText.substring(1), true)
      } else {
        (termText, false)
      }

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

        val info = posInfo.getOrElseUpdate(posTuple, TermPositionInfo(None, new MutableHashSet[String]()))
        if (isForm) {
          info.form = Some(entry)
        } else {
          info.nonForms += term
        }

        val termPosSet: MutableSet[TermPosition] = termPos.getOrElseUpdate(term, new MutableHashSet[TermPosition])

        termPosSet += entry

        if (isForm) {
          val oldCount = formCounts.getOrElse(term, 0)
          formCounts += term -> (oldCount + 1)
        } else {
          val oldCount = nonFormCounts.getOrElse(term, 0)
          nonFormCounts += term -> (oldCount + 1)
        }
      }

      rawText = termsEnum.next()
    }

    val nf2f: NonFormToFormMap = new MutableHashMap
    posInfo.foreach { case (position, info) =>
      info.form match {
        case Some(termForm) =>
          info.nonForms.foreach { term =>
            nf2f += term -> termForm
          }
        case None =>
          logger.warn("Missing a form: " + info)
      }
    }

    DocumentTermInfo(docId, formCounts, nonFormCounts, termPos, posInfo, nf2f)
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
