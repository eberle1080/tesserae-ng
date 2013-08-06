package org.apache.solr.handler.tesserae

import org.apache.solr.search.DocList
import org.apache.solr.request.SolrQueryRequest
import org.apache.solr.response.SolrQueryResponse
import akka.actor.{Actor, ActorRef}
import org.apache.lucene.index.{TermsEnum, IndexReader}

final case class TermPositionsListEntry(term: String, position: Int)
final case class DocumentTermInfo(docID: Int, termCounts: TesseraeCompareHandler.TermCountMap,
                                  termPositions: TesseraeCompareHandler.TermPositionsList)
final case class QueryParameters(qParamName: String, searchFieldParamName: String, fieldListParamName: String)
final case class QueryInfo(termInfo: TesseraeCompareHandler.QueryTermInfo, fieldList: List[String], searcher: (Int, Int) => DocList)
final case class DocumentPair(sourceDoc: Int, targetDoc: Int)
final case class DistanceParameters(pair: DocumentPair, commonTerms: Set[String], source: QueryInfo, target: QueryInfo)
final case class CompareResult(pair: DocumentPair, commonTerms: Set[String], score: Double, distance: Int)



//case class MapOneVectorArgs(reader: IndexReader, docId: Int, termsEnum: TermsEnum, field: String)
//final case class GatherInfoRequest(req: SolrQueryRequest, rsp: SolrQueryResponse, qParams: QueryParameters) extends Message
//final case class GatherInfoResult(result: Either[QueryInfo, Throwable]) extends Message
