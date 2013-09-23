package org.apache.solr.analysis.lexicon

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes._
import org.apache.solr.handler.tesserae.metrics.CommonMetrics
import org.tesserae.lexicon.db.CSVLine
import org.slf4j.LoggerFactory

class LatinLexiconStemFilter(input: TokenStream, db: LatinLexiconDatabase) extends TokenFilter(input) {

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

  def incrementToken(): Boolean = {
    CommonMetrics.latinStemOps.mark()

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

    clearAttributes()

    val normalized = String.valueOf(currentTokenBuffer, 0,  currentTokenLength).toLowerCase
    val tok = db.lookup(normalized).getOrElse(CSVLine(normalized, "", normalized))
    val stemmed = tok.stem

    posIncAtt.setPositionIncrement(currentTokenPosition)
    termAtt.setEmpty().append(stemmed)
    termAtt.setLength(stemmed.length())
    offsetAtt.setOffset(currentTokenStart, currentTokenEnd)
    typeAtt.setType("LATIN")

    true
  }
}
