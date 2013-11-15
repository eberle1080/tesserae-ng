package org.apache.solr.handler.tesserae

import org.apache.solr.search.DocList
import collection.mutable.{Map => MutableMap, Set => MutableSet}

object DataTypes {
  // Countains a list of term + position tuples
  type TermPositionsMap = MutableMap[String, MutableSet[TermPosition]]
  type PositionsTermMap = MutableMap[(Int, Int), TermPositionInfo]

  // Maps term text -> count
  type TermCountMap = MutableMap[String, Int]

  // Maps non-form text -> form text
  type NonFormToFormMap = MutableMap[String, TermPosition]

  // Maps term text -> frequency
  type FrequencyMap = MutableMap[String, Double]

  // Maps term text -> matching documents
  type TermDocumentMap = MutableMap[String, MutableSet[Int]]

  // Map a form token to one or more non-form tokens
  type FormToNonForms = MutableMap[TermPosition, MutableSet[String]]

  // Map a source document ID to a FormToNonForms
  type SourceToF2NF = MutableMap[Int, FormToNonForms]

  // Map a target document ID to a SourceToF2NF
  type TargetToSourceToF2NF = MutableMap[Int, SourceToF2NF]

  // Maps doc id -> DocumentTermInfo
  type QueryTermInfo = MutableMap[Int, DocumentTermInfo]

  type TermList = java.util.LinkedList[String]
  type DocFields = java.util.HashMap[String, AnyRef]
  type TesseraeDoc = java.util.HashMap[String, AnyRef]
  type TesseraeMatch = java.util.HashMap[String, AnyRef]
  type TesseraeMatches = java.util.LinkedList[TesseraeMatch]
  type StopList = java.util.LinkedList[String]
}

import DataTypes._

final case class TermPositionInfo(var form: Option[TermPosition], nonForms: MutableSet[String])

final case class TermPosition(term: String, docId: Int, numeric: Int, position: (Int, Int))
final case class DocumentTermInfo(docID: Int, formTermCounts: TermCountMap, nonFormTermCounts: TermCountMap,
                                  termPositions: TermPositionsMap, positionTerms: PositionsTermMap,
                                  nf2f: NonFormToFormMap)
final case class Mash(formsToDocs: TermDocumentMap, nonFormsToDocs: TermDocumentMap, docInfo: QueryTermInfo)

final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class AggregateTermInfo(termCounts: MutableMap[String, Int], totalTermCount: Int) // used for calculating frequency
final case class FrequencyInfo(sourceTerms: AggregateTermInfo, targetTerms: AggregateTermInfo)
final case class DigestedFrequencyInfo(sourceFrequencies: FrequencyMap, targetFrequencies: FrequencyMap)
final case class DistanceParameters(pair: DocumentPair, source: QueryInfo,
                                    target: QueryInfo, frequencies: DigestedFrequencyInfo,
                                    sourceTerms: Set[String], targetTerms: Set[String])
final case class CompareResult(pair: DocumentPair, commonTerms: MutableSet[String], score: Double, distance: Int)
final case class TermFrequencyEntry(term: String, frequency: Double)

final case class CacheKey(md: Int, mct: Int, metric: DistanceMetrics.Value,
                          sq: String, sf: String, sfl: String,
                          tq: String, tf: String, tfl: String,
                          sw: Int, sl: String)
final case class CacheValue(results: List[CompareResult], sourceFieldList: List[String],
                            targetFieldList: List[String], stoplist: MutableSet[String])

final case class DocumentPairInfo(sourceTerms: FormToNonForms, targetTerms: FormToNonForms)
final case class RequestContext(id: String)
