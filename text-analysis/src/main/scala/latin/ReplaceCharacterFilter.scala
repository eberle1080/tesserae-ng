package org.apache.lucene.analysis.la

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.{KeywordAttribute, CharTermAttribute}

class ReplaceCharacterFilter(input: TokenStream, replacements: Map[String, String]) extends TokenFilter(input) {
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val keywordAttr: KeywordAttribute = addAttribute(classOf[KeywordAttribute])

  override final def incrementToken(): Boolean = {
    if (input.incrementToken()) {
      if (!keywordAttr.isKeyword) {
        var term = String.valueOf(termAtt.buffer(), 0, termAtt.length())
        replacements.foreach { case (search, replacement) =>
          term = term.replaceAllLiterally(search, replacement)
        }

        termAtt.setEmpty().append(term)
        termAtt.setLength(term.length())
      }
      true
    } else {
      false
    }
  }
}
