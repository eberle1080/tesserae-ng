package org.apache.solr.analysis.corpus

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

/**
 * A filter that collects all terms and updates a global frequency database
 *
 * @param input A token stream
 * @param db A LevelDB session
 */
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
