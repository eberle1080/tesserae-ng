package org.apache.solr.handler.tesserae

import org.apache.solr.search.DocList
import org.apache.solr.handler.tesserae.TesseraeCompareHandler.TermCountMap

final case class TermPositionsListEntry(term: String, position: Int)
final case class DocumentTermInfo(docID: Int, termCounts: TermCountMap,
                                  termPositions: TesseraeCompareHandler.TermPositionsList)
final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: TesseraeCompareHandler.QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class AggregateTermInfo(termCounts: TermCountMap, totalTermCount: Int) // used for calculating frequency
final case class FrequencyInfo(sourceTerms: AggregateTermInfo, targetTerms: AggregateTermInfo)
final case class DistanceParameters(pair: DocumentPair, commonTerms: Set[String], source: QueryInfo, target: QueryInfo,
                                    frequencies: FrequencyInfo)
final case class CompareResult(pair: DocumentPair, commonTerms: Set[String], score: Double, distance: Int)
