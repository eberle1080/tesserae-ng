package org.apache.solr.analysis.corpus

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

class LatinCorpusFrequencyCollector(input: TokenStream, db: LatinCorpusDatabase) extends TokenFilter(input) {
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])

  def incrementToken(): Boolean = {
    if (input.incrementToken()) {
      val token = String.valueOf(termAtt.buffer(), 0, termAtt.length())
      db.incrementFrequency(token)
      true
    } else {
      false
    }
  }
}
