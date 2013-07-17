package org.apache.solr.analysis

import java.util.{Map => JavaMap}

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.la.LatinStemFilter
import org.apache.lucene.analysis.util.TokenFilterFactory

class LatinStemFilterFactory(args: JavaMap[String, String]) extends TokenFilterFactory(args) {
  import scala.collection.JavaConversions._
  def create(input: TokenStream): TokenStream =
    new LatinStemFilter(input, args.toMap)
}
