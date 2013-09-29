package org.apache.lucene.analysis.la

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.{KeywordAttribute, CharTermAttribute}
import scala.util.matching.Regex

class ReplaceRegexFilter(input: TokenStream, regex: Regex, replacement: String) extends TokenFilter(input) {
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val keywordAttr: KeywordAttribute = addAttribute(classOf[KeywordAttribute])

  override final def incrementToken(): Boolean = {
    if (input.incrementToken()) {
      if (!keywordAttr.isKeyword) {
        val term = String.valueOf(termAtt.buffer(), 0, termAtt.length())
        val replaced = regex.replaceAllIn(term, replacement)

        termAtt.setEmpty().append(replaced)
        termAtt.setLength(replaced.length())
      }
      true
    } else {
      false
    }
  }
}
