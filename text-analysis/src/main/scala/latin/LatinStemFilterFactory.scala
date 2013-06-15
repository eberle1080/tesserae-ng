package org.apache.solr.analysis

import java.util.Map

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.la.LatinStemFilter
import org.apache.lucene.analysis.util.TokenFilterFactory

class LatinStemFilterFactory(args: Map[String, String]) extends TokenFilterFactory(args) {
  def create(input: TokenStream): TokenStream =
    new LatinStemFilter(input)
}
