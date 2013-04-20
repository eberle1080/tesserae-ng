package org.apache.solr.analysis

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.la.LatinStemFilter
import org.apache.lucene.analysis.util.TokenFilterFactory

class LatinStemFilterFactory extends TokenFilterFactory {
  def create(input: TokenStream): TokenStream =
    new LatinStemFilter(input)
}
