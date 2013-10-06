package org.apache.solr.handler.tesserae

import org.apache.solr.search.DocList
import collection.mutable.{Map => MutableMap, Set => MutableSet}

object DataTypes {
  // Countains a list of term + position tuples
  type TermPositionsMap = MutableMap[TextTerm, MutableSet[TermPosition]]
  type PositionsTermMap = MutableMap[(Int, Int), MutableSet[TermPosition]]

  // Maps term text -> count
  type TermCountMap = MutableMap[TextTerm, Int]

  // Maps term text -> frequency
  type FrequencyMap = MutableMap[String, Double]

  // Maps term text -> matching documents
  type TermDocumentMap = MutableMap[TextTerm, MutableSet[Int]]

  // Maps doc id -> DocumentTermInfo
  type QueryTermInfo = MutableMap[Int, DocumentTermInfo]

  // A list of sorted frequencies
  type SortedFrequencies = List[TermFrequencyEntry]

  type TermList = java.util.LinkedList[String]
  type DocFields = java.util.HashMap[String, AnyRef]
  type TesseraeDoc = java.util.HashMap[String, AnyRef]
  type TesseraeMatch = java.util.HashMap[String, AnyRef]
  type TesseraeMatches = java.util.LinkedList[TesseraeMatch]
  type StopList = java.util.LinkedList[String]
}

import DataTypes._

final case class TextTerm(text: String, form: Boolean, resolved: Boolean)
final case class MatchPart(info: DocumentTermInfo, position: (Int, Int))
final case class Match(sourcePart: MatchPart, targetPart: MatchPart)

final case class TermPosition(term: TextTerm, docId: Int, numeric: Int, position: (Int, Int))
final case class Mash(termsToDocs: TermDocumentMap, docInfo: QueryTermInfo)

final case class DocumentTermInfo(docID: Int, termCounts: TermCountMap,
                                  termPositions: TermPositionsMap, positionTerms: PositionsTermMap)
final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class AggregateTermInfo(termCounts: MutableMap[String, Int], totalTermCount: Int) // used for calculating frequency
final case class FrequencyInfo(sourceTerms: AggregateTermInfo, targetTerms: AggregateTermInfo)
final case class SortedFrequencyInfo(sourceFrequencies: SortedFrequencies, targetFrequencies: SortedFrequencies)
final case class DistanceParameters(pair: DocumentPair, commonTerms: MutableMap[String, ResolvedTextTerms],
                                    source: QueryInfo, target: QueryInfo, frequencies: SortedFrequencyInfo)
final case class CompareResult(pair: DocumentPair, commonTerms: MutableSet[String], score: Double, distance: Int)
final case class TermFrequencyEntry(term: String, frequency: Double)

final case class CacheKey(md: Int, mct: Int, metric: DistanceMetrics.Value,
                          sq: String, sf: String, sfl: String,
                          tq: String, tf: String, tfl: String,
                          sw: Int, sl: String)
final case class CacheValue(results: List[CompareResult], sourceFieldList: List[String],
                            targetFieldList: List[String], stoplist: MutableSet[String])
final case class ResolvedTextTerms(nonForm: MutableSet[TextTerm], form: MutableSet[TextTerm])
final case class DocumentPairInfo(commonTerms: MutableMap[String, ResolvedTextTerms], sourcePositions: TermPositionsMap,
                                  targetPositions: TermPositionsMap)
