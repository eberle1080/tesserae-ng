package org.apache.lucene.analysis.la.legacy

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.apache.lucene.analysis.tokenattributes.TypeAttribute
import org.apache.solr.handler.tesserae.pos.PartOfSpeech
import org.apache.solr.handler.tesserae.metrics.CommonMetrics
import org.apache.lucene.analysis.la.LatinStemmer

class CSVLatinStemFilter(input: TokenStream, csv: Map[String, CSVLine]) extends TokenFilter(input) {
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

  def incrementToken(): Boolean = {
    true
  }
}
