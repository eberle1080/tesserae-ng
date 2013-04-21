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
import org.apache.lucene.index.{DocsAndPositionsEnum, TermsEnum, IndexReader}
import org.apache.lucene.util.{Attribute, BytesRef}
import org.slf4j.{LoggerFactory, Logger}
import org.apache.lucene.analysis.tokenattributes.TypeAttribute

final case class TermPositionsListEntry(term: String, position: Int)
final case class DocumentTermInfo(docID: Int, termCounts: TesseraeCompareHandler.TermCountMap,
                                  termPositions: TesseraeCompareHandler.TermPositionsList)
final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: TesseraeCompareHandler.QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class DistanceParameters(pair: DocumentPair, commonTerms: Set[String], source: QueryInfo, target: QueryInfo)
final case class CompareResult(pair: DocumentPair, commonTerms: Set[String], score: Double, distance: Int)

object TesseraeCompareHandler {
  val DEFAULT_MAX_DISTANCE = 0 // 0 = no max
  val DEFAULT_MIN_COMMON_TERMS = 2 // can't be less than 2
  val DEFAULT_HIGHLIGHT = false
  val SPLIT_REGEX = ",| ".r

  // Countains a list of term + position tuples
  type TermPositionsList = List[TermPositionsListEntry]

  // Maps term text -> count
  type TermCountMap = Map[String, Int]

  // Maps doc id -> DocumentTermInfo
  type QueryTermInfo = Map[Int, DocumentTermInfo]

  type TermList = NamedList[String]
  type DocFields = NamedList[AnyRef]
  type TesseraeDoc = NamedList[AnyRef]
  type TesseraeMatch = NamedList[AnyRef]
  type TesseraeMatches = NamedList[TesseraeMatch]
}

object DistanceMetrics extends Enumeration {
  val FREQ, FREQ_TARGET, FREQ_SOURCE, SPAN, SPAN_TARGET, SPAN_SOURCE = Value
  val DEFAULT_METRIC = FREQ

  def apply(str: String): Option[Value] = {
    if (str == null) {
      return None
    }
    str.trim.toLowerCase match {
      case "freq" => Some(FREQ)
      case "freq_target" => Some(FREQ_TARGET)
      case "freq_source" => Some(FREQ_SOURCE)
      case "span" => Some(SPAN)
      case "span_target" => Some(SPAN_TARGET)
      case "span_source" => Some(SPAN_SOURCE)
      case _ => None
    }
  }

  def apply(metric: Value): Option[Distance] = {
    metric match {
      case FREQ => Some(new FreqDistance)
      case FREQ_TARGET => Some(new FreqTargetDistance)
      case FREQ_SOURCE => Some(new FreqSourceDistance)
      case SPAN => Some(new SpanDistance)
      case SPAN_TARGET => Some(new SpanTargetDistance)
      case SPAN_SOURCE => Some(new SpanSourceDistance)
      case _ => None
    }
  }
}

trait Distance {
  def calculateDistance(params: DistanceParameters): Option[Int]
}

// Some re-usable distance functions
trait DistanceMixin {
  import TesseraeCompareHandler.TermPositionsList

  protected def sortedFreq(id: Int, info: QueryInfo): List[(Int, String)] = {
    val terms = info.termInfo(id).termCounts
    var counts: List[(Int, String)] = Nil
    terms.foreach { case (term, freq) =>
      counts = (freq, term) :: counts
    }

    counts.sortWith { (a, b) => a._1 < b._1 }
  }

  protected def filterPositions(tpl: TermPositionsList): TermPositionsList = {
    var entriesMap: Map[Int, String] = Map.empty
    tpl.foreach { entry =>
      val termLen = entry.term.length
      entriesMap.get(entry.position) match {
        case None =>
          entriesMap += entry.position -> entry.term
        case Some(priorTerm) =>
          val prLen = priorTerm.length
          if (termLen > prLen) {
            entriesMap += entry.position -> entry.term
          } else if (termLen == prLen) {
            // choose alphabetically
            if (entry.term.compareTo(priorTerm) > 0) {
              entriesMap += entry.position -> entry.term
            }
          } else {
            // leave the old one alone
          }
      }
    }

    var list: TermPositionsList = Nil
    entriesMap.foreach { case (pos, term) =>
      list = TermPositionsListEntry(term, pos) :: list
    }

    list
  }

  protected def sortedPositions(id: Int, info: QueryInfo, filter: Boolean = true): TermPositionsList = {
    val terms = info.termInfo(id).termPositions
    val filtered = if (filter) { filterPositions(terms) } else { terms }
    terms.sortWith { (a, b) => a.position < b.position }
  }

  protected def distanceBetween(p0: TermPositionsListEntry, p1: TermPositionsListEntry) = {
    import math.abs
    // the perl one worries about words vs. non-words. I don't have this problem
    // because everything in the term vector is guaranteed to be a real word
    abs(p1.position - p0.position) + 1
  }
}

class FreqDistance extends Distance with DistanceMixin {

  protected def internalDistance(docID: Int, info: QueryInfo): Option[Int] = {
    val terms = sortedFreq(docID, info)
    if (terms.length < 2) {
      None
    } else {
      val positions = sortedPositions(docID, info)
      val (tt0, tt1) = (terms(0)._2, terms(1)._2)
      val (t0, t1) = try {
        val _t0 = positions.find(tp => tp.term == tt0).get
        val _t1 = positions.find(tp => tp.term == tt1).get
        (_t0, _t1)
      } catch {
        case e: NoSuchElementException =>
          // one of the "find" methods failed
          return None
      }

      Some(distanceBetween(t0, t1))
    }
  }

  def calculateDistance(params: DistanceParameters): Option[Int] = {
    internalDistance(params.pair.sourceDoc, params.source) match {
      case None => None
      case Some(sourceDist) =>
        internalDistance(params.pair.targetDoc, params.target) match {
          case None => None
          case Some(targetDist) => Some(sourceDist + targetDist)
        }
    }
  }
}

class FreqTargetDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.targetDoc, params.target)
}

class FreqSourceDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.sourceDoc, params.source)
}

class SpanDistance extends Distance with DistanceMixin {
  protected def internalDistance(docID: Int, info: QueryInfo): Option[Int] = {
    val positions = sortedPositions(docID, info)
    if (positions.length < 2) {
      None
    } else {
      val first = positions.head
      val last = positions.takeRight(1).head
      Some(distanceBetween(first, last))
    }
  }

  def calculateDistance(params: DistanceParameters) = {
    internalDistance(params.pair.sourceDoc, params.source) match {
      case None => None
      case Some(sourceDist) =>
        internalDistance(params.pair.targetDoc, params.target) match {
          case None => None
          case Some(targetDist) => Some(sourceDist + targetDist)
        }
    }
  }
}

class SpanTargetDistance extends SpanDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.targetDoc, params.target)
}

class SpanSourceDistance extends SpanDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.sourceDoc, params.source)
}

object TesseraeScoring {

}

final class TesseraeCompareHandler extends RequestHandlerBase {

  import TesseraeCompareHandler._

  private lazy val logger = LoggerFactory.getLogger(getClass)

  override def init(args: NamedList[_]) {
    super.init(args)
  }

  def handleRequestBody(req: SolrQueryRequest, rsp: SolrQueryResponse) {
    val params = req.getParams
    val returnFields = new SolrReturnFields(req)
    rsp.setReturnFields(returnFields)

    rsp.add("params", req.getParams().toNamedList())

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

    val includeSourceMatch = params.getBool(TesseraeCompareParams.SOURCE_INCLUDE, false)
    val includeHighlightInfo = params.getBool(TesseraeCompareParams.HIGHLIGHT, DEFAULT_HIGHLIGHT)

    val sourceParams = QueryParameters(TesseraeCompareParams.SQ, TesseraeCompareParams.SF, TesseraeCompareParams.SFL)
    val sourceInfo = gatherInfo(req, rsp, sourceParams)

    if (includeSourceMatch) {
      val sourceCount = params.getInt(TesseraeCompareParams.SOURCE_COUNT, 10)
      val sourceOffset = params.getInt(TesseraeCompareParams.SOURCE_OFFSET, 0)
      rsp.add("source-matches", sourceInfo.searcher(sourceOffset, sourceCount))
    }

    val targetParams = QueryParameters(TesseraeCompareParams.TQ, TesseraeCompareParams.TF, TesseraeCompareParams.TFL)
    val targetInfo = gatherInfo(req, rsp, targetParams)

    val results = {
      val sortedResults = compare(sourceInfo, targetInfo, maxDistance, minCommonTerms, metric)
      sortedResults.drop(start).take(rows)
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
          populate.add(fieldName, fieldValue)
        }
      }

      found
    }

    val matches = new TesseraeMatches
    results.foreach { result =>
      val m = new TesseraeMatch
      m.add("score", new java.lang.Double(result.score))
      m.add("distance", new java.lang.Double(result.distance))

      val terms = new TermList
      result.commonTerms.toList.sorted.foreach { term =>
        terms.add("term", term)
      }

      m.add("terms", terms)

      val sdoc = new TesseraeDoc
      val tdoc = new TesseraeDoc

      val sourceFields = new DocFields
      if (processResult(result, sourceIfTrue=true, sourceFields) > 0) {
        sdoc.add("fields", sourceFields)
      }

      val targetFields = new DocFields
      if (processResult(result, sourceIfTrue=false, targetFields) > 0) {
        tdoc.add("fields", targetFields)
      }

      m.add("source", sdoc)
      m.add("target", tdoc)

      matches.add("match", m)
    }

    rsp.add("matches", matches)
  }

  private def getMetric(distanceMetric: DistanceMetrics.Value, maxDistance: Int): Distance = {
    val dm = DistanceMetrics(distanceMetric)
    dm match {
      case None => throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "invalid metric: " + distanceMetric.toString)
      case Some(d) => d
    }
  }

  private def compare(source: QueryInfo, target: QueryInfo, maxDistance: Int,
                      minCommonTerms: Int, distanceMetric: DistanceMetrics.Value): List[CompareResult] = {

    // A mash maps one term to a set of document ids
    val sourceMash = buildMash(source)
    val targetMash = buildMash(target)

    // Get all document pairs with two or more terms in common
    val docPairs = findDocumentPairs(sourceMash, targetMash).filter { case (_, terms) => terms.size >= minCommonTerms }
    val metric = getMetric(distanceMetric, maxDistance)

    // Distance and scoring
    var results: List[CompareResult] = Nil
    docPairs.foreach { case (pair, terms) =>
      val params = DistanceParameters(pair, terms, source, target)
      metric.calculateDistance(params).map { distance =>
        if (distance <= maxDistance || maxDistance <= 0) {

          logger.info("Distance is " + distance)

          var score = 0.0
          terms.foreach { term =>
            val sourceCount = source.termInfo(pair.sourceDoc).termCounts(term)
            val targetCount = target.termInfo(pair.targetDoc).termCounts(term)
            val sFreq = ((1.0) / (sourceCount.toDouble))
            val tFreq = ((1.0) / (targetCount.toDouble))
            score += sFreq + tFreq
          }

          val finalScore = math.log(score / distance.toDouble)
          val result = CompareResult(pair, terms, finalScore, distance)
          results = result :: results
        }
      }
    }

    // Return the results sorted by score (descending)
    results.sortWith { (a, b) => a.score > b.score }
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
      ((pair, terms.toSet))
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
      ((term, set.toSet))
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
    val sorter = parser.getSort(true)
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

    logger.info("Using query '" + queryStr + "' found " + listAndSet.docSet.size + " documents")

    var termInfo: QueryTermInfo = Map.empty

    while (dlit.hasNext) {
      val docId = dlit.nextDoc()
      val vec = reader.getTermVector(docId, searchField)

      if (vec != null) {
        val dti = mapOneVector(reader, docId, vec.iterator(null), searchField)
        termInfo += docId -> dti
      }
    }

    logger.info("Using query '" + queryStr + "' found " + termInfo.size + " actual results")

    QueryInfo(termInfo, returnFields, (offset, count) => listAndSet.docList)
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
      var termType: Option[Attribute] = None

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

object TesseraeCompareParams {
  val TESS = "tess"
  val PREFIX = TESS + "."

  // source query
  val SQ = PREFIX + "sq"

  // source text field
  val SF = PREFIX + "sf"

  // source field list
  val SFL = PREFIX + "sfl"

  // target query
  val TQ = PREFIX + "tq"

  // target text field
  val TF = PREFIX + "tf"

  // target field list
  val TFL = PREFIX + "tfl"

  // max distance
  val MD = PREFIX + "md"

  // minimum common terms
  val MCT = PREFIX + "mct"

  // distance metric
  val METRIC = PREFIX + "metric"

  // include the source matches?
  val SOURCE_INCLUDE = PREFIX + "source.include"

  // how many source docs to include
  val SOURCE_COUNT = PREFIX + "source.count"

  // an offset into the source list
  val SOURCE_OFFSET = PREFIX + "source.offset"

  // include highlight info?
  val HIGHLIGHT = PREFIX + "highlight"
}
