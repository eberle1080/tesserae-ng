package org.apache.solr.analysis

import java.util.Map

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.la.LatinNumberConvertFilter
import org.apache.lucene.analysis.util.TokenFilterFactory

class LatinNumberConvertFilterFactory(args: Map[String, String]) extends TokenFilterFactory(args) {
  private val strictMode = getBoolean(args, "strictMode", false)
  def create(input: TokenStream): TokenStream =
     new LatinNumberConvertFilter(input, strictMode)
}
