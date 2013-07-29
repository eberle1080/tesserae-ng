package org.apache.lucene.analysis.la

import java.io.IOException

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.apache.lucene.analysis.tokenattributes.TypeAttribute

object LatinStemFilter {
  val TYPE_NOUN = "LATIN_NOUN"
  val TYPE_VERB = "LATIN_VERB"
}

final class LatinStemFilter(input: TokenStream) extends TokenFilter(input) {
  private val stemmer = new LatinStemmer
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val offsetAtt: OffsetAttribute = addAttribute(classOf[OffsetAttribute])
  private val posIncAtt: PositionIncrementAttribute = addAttribute(classOf[PositionIncrementAttribute])
  private val typeAtt: TypeAttribute = addAttribute(classOf[TypeAttribute])
  private val keywordAttr: KeywordAttribute = addAttribute(classOf[KeywordAttribute])
  private var stemAsNoun = true

  private var currentTokenBuffer: Array[Char] = null
  private var currentTokenLength: Int = 0
  private var currentTokenStart: Int = 0
  private var currentTokenEnd: Int = 0
  private var currentTokenPosition: Int = 0

  override final def incrementToken(): Boolean = {
    if (currentTokenBuffer == null) {
      if (!input.incrementToken()) {
        return false
      } else {
        if (keywordAttr.isKeyword()) {
          return true;
        }

        currentTokenBuffer = termAtt.buffer().clone();
        currentTokenLength = termAtt.length();
        currentTokenStart = offsetAtt.startOffset();
        currentTokenEnd = offsetAtt.endOffset();
        currentTokenPosition = posIncAtt.getPositionIncrement();
      }
    }

    clearAttributes()

    replaceVJ(currentTokenBuffer, currentTokenLength)

    val termLength = stemmer.stemQUE(currentTokenBuffer, currentTokenLength)
    var stemmedToken: String = null
    if (termLength == -1) {
      stemmedToken = String.valueOf(currentTokenBuffer, 0,  currentTokenLength)
    } else {
      if (stemAsNoun) {
        stemmedToken = stemmer.stemAsNoun(currentTokenBuffer, termLength)
      } else {
        stemmedToken = stemmer.stemAsVerb(currentTokenBuffer, termLength)
      }
    }

    var tokenType: String = null
    if(stemAsNoun) {
      stemAsNoun = false
      tokenType = LatinStemFilter.TYPE_NOUN
      posIncAtt.setPositionIncrement(currentTokenPosition)
    } else {
      stemAsNoun = true
      tokenType = LatinStemFilter.TYPE_VERB
      currentTokenBuffer = null
      currentTokenLength = -1
      posIncAtt.setPositionIncrement(0)
    }

    termAtt.setEmpty().append(stemmedToken)
    termAtt.setLength(stemmedToken.length())
    offsetAtt.setOffset(currentTokenStart, currentTokenEnd)
    typeAtt.setType(tokenType)

    true
  }

  private def replaceVJ(termBuffer: Array[Char], termLength: Int) {
    for (i <- 0 until termLength) {
      val oldVal = termBuffer(i)
      termBuffer(i) = oldVal match {
        case 'V' => 'U'
        case 'v' => 'u'
        case 'J' => 'I'
        case 'j' => 'i'
        case _ => oldVal
      }
    }
  }
}
