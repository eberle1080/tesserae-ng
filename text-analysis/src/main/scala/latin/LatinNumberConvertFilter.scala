package org.apache.lucene.analysis.la

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.{CharTermAttribute, KeywordAttribute}

class LatinNumberConvertFilter(input: TokenStream, strictMode: Boolean) extends TokenFilter(input) {
  private val numberFormatter = new LatinNumberConverter(strictMode)
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val keywordAttr: KeywordAttribute = addAttribute(classOf[KeywordAttribute])

  override final def incrementToken(): Boolean = {
    if (input.incrementToken()) {
      if (keywordAttr.isKeyword) {
        true
      } else {
        val arabicNumber = numberFormatter.format(termAtt.buffer(), termAtt.length())
        if (arabicNumber != null) {
          termAtt.setEmpty().append(arabicNumber)
          termAtt.setLength(arabicNumber.length())
        }
        true
      }
    } else {
      false
    }
  }
}
