package org.apache.solr.handler.tesserae

import org.apache.lucene.util.{AttributeImpl, Attribute}
import scala.collection.immutable.HashMap

trait NGramSummaryAttribute extends Attribute {
  def addNGrams(ngramAttr: NGramAttribute)
  def getNGramCounts: Map[String, Int]
  def setNGramCounts(ngramCounts: Map[String, Int])
  def setTotalUniqueNGrams(total: Int)
  def setTotalNGrams(total: Int)
  def getTotalUniqueNGrams: Int
  def getTotalNGrams: Int
}

class NGramSummaryAttributeImpl extends AttributeImpl with NGramSummaryAttribute {
  private var ngramCounts: Map[String, Int] = HashMap.empty
  private var totalUniqueNGrams: Int = 0
  private var totalNGrams: Int = 0

  def addNGrams(ngramAttr: NGramAttribute) {
    ngramAttr.getNGrams.foreach { ngr =>
      totalNGrams += 1
      ngramCounts.get(ngr) match {
        case None =>
          ngramCounts += ngr -> 1
          totalUniqueNGrams += 1
        case Some(prev) =>
          ngramCounts += ngr -> (prev + 1)
      }
    }
  }

  def setNGramCounts(counts: Map[String, Int]) {
    ngramCounts = counts
  }

  def getNGramCounts: Map[String, Int] =
    ngramCounts

  def clear() {
    ngramCounts = HashMap.empty
    totalNGrams = 0
    totalUniqueNGrams = 0
  }

  def copyTo(attr: AttributeImpl) {
    attr match {
      case ng: NGramSummaryAttribute =>
        ng.setNGramCounts(ngramCounts)
        ng.setTotalNGrams(totalNGrams)
        ng.setTotalUniqueNGrams(totalUniqueNGrams)
      case _ =>
    }
  }

  def setTotalUniqueNGrams(total: Int) {
    totalUniqueNGrams = total
  }

  def setTotalNGrams(total: Int) {
    totalNGrams = total
  }

  def getTotalUniqueNGrams: Int =
    totalUniqueNGrams

  def getTotalNGrams: Int =
    totalNGrams
}
