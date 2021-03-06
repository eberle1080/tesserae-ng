package org.apache.solr.analysis.lexicon

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes._
import org.apache.solr.handler.tesserae.metrics.CommonMetrics
import lex.db.CSVLine
import org.slf4j.LoggerFactory

class LatinLexiconStemFilter(input: TokenStream, multiStem: Boolean, db: LatinLexiconDatabase) extends TokenFilter(input) {

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val offsetAtt: OffsetAttribute = addAttribute(classOf[OffsetAttribute])
  private val posIncAtt: PositionIncrementAttribute = addAttribute(classOf[PositionIncrementAttribute])
  private val typeAtt: TypeAttribute = addAttribute(classOf[TypeAttribute])
  private val keywordAttr: KeywordAttribute = addAttribute(classOf[KeywordAttribute])

  private var currentTokenBuffer: Array[Char] = null
  private var currentTokenLength: Int = 0
  private var currentTokenStart: Int = 0
  private var currentTokenEnd: Int = 0
  private var currentTokenPosition: Int = 0
  private var activeTokenList: List[CSVLine] = Nil

  def incrementToken(): Boolean = {
    CommonMetrics.latinStemOps.mark()

    if (activeTokenList.isEmpty) {
      if (!input.incrementToken()) {
        return false
      } else {
        if (keywordAttr.isKeyword) {
          return true
        }

        currentTokenBuffer = termAtt.buffer().clone()
        currentTokenLength = termAtt.length()
        currentTokenStart = offsetAtt.startOffset()
        currentTokenEnd = offsetAtt.endOffset()
        currentTokenPosition = posIncAtt.getPositionIncrement
      }
    }

    clearAttributes()

    if (activeTokenList.isEmpty) {
      val token = String.valueOf(currentTokenBuffer, 0,  currentTokenLength)
      val tok_list = db.lookup(token).getOrElse {
        List(CSVLine(token, "", token))
      }

      // Special indicator
      val formToken = "_" + token

      activeTokenList = CSVLine(formToken, "", formToken) :: tok_list
      posIncAtt.setPositionIncrement(currentTokenPosition)
    } else {
      posIncAtt.setPositionIncrement(0)
    }

    activeTokenList match {
      case Nil =>
        throw new IllegalStateException("activeTokenList is empty, even after being refreshed")
      case head :: tail => {
        val stem = if (multiStem) {
          head.stem
        } else {
          activeTokenList(0).stem
        }

        termAtt.setEmpty().append(stem)
        termAtt.setLength(stem.length())
        offsetAtt.setOffset(currentTokenStart, currentTokenEnd)
        typeAtt.setType("LATIN")

        if (multiStem) {
          activeTokenList = tail
        } else {
          activeTokenList = Nil
        }
      }
    }

    true
  }
}
