package org.apache.solr.analysis

import java.util.Map

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.la.LatinNumberConvertFilter
import org.apache.lucene.analysis.util.TokenFilterFactory

class LatinNumberConvertFilterFactory extends TokenFilterFactory {
  private var strictMode = false

  override def init(args: Map[String, String]) {
    super.init(args)
    strictMode = getBoolean("strictMode", false)
  }

  def create(input: TokenStream): TokenStream =
     new LatinNumberConvertFilter(input, strictMode)
}
