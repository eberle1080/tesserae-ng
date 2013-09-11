package org.apache.solr.handler.tesserae

import org.apache.solr.search.DocList

object DataTypes {
  // Countains a list of term + position tuples
  type TermPositionsList = List[TermPositionsListEntry]

  // Maps term text -> count
  type TermCountMap = Map[String, Int]

  // Maps term text -> frequency
  type FrequencyMap = Map[String, Double]

  // Maps doc id -> DocumentTermInfo
  type QueryTermInfo = Map[Int, DocumentTermInfo]

  // A list of sorted frequencies
  type SortedFrequencies = List[TermFrequencyEntry]

  type TermList = java.util.LinkedList[String]
  type DocFields = java.util.HashMap[String, AnyRef]
  type TesseraeDoc = java.util.HashMap[String, AnyRef]
  type TesseraeMatch = java.util.HashMap[String, AnyRef]
  type TesseraeMatches = java.util.LinkedList[TesseraeMatch]
}

import DataTypes._

final case class TermPositionsListEntry(term: String, position: Int)
final case class DocumentTermInfo(docID: Int, termCounts: TermCountMap,
                                  termPositions: TermPositionsList)
final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class AggregateTermInfo(termCounts: TermCountMap, totalTermCount: Int) // used for calculating frequency
final case class FrequencyInfo(sourceTerms: AggregateTermInfo, targetTerms: AggregateTermInfo)
final case class SortedFrequencyInfo(sourceFrequencies: SortedFrequencies, targetFrequencies: SortedFrequencies)
final case class DistanceParameters(pair: DocumentPair, commonTerms: Set[String], source: QueryInfo, target: QueryInfo,
                                    frequencies: SortedFrequencyInfo)
final case class CompareResult(pair: DocumentPair, commonTerms: Set[String], score: Double, distance: Int)
final case class TermFrequencyEntry(term: String, frequency: Double)

final case class CacheKey(md: Int, mct: Int, metric: DistanceMetrics.Value,
                          sq: String, sf: String, sfl: String,
                          tq: String, tf: String, tfl: String)
final case class CacheValue(results: List[CompareResult], sourceFieldList: List[String], targetFieldList: List[String])
