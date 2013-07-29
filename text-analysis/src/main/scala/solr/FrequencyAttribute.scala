package org.apache.solr.handler.tesserae

import org.apache.lucene.util.{AttributeImpl, Attribute}
import scala.collection.mutable.{Map => MutableMap, HashMap => MutableHashMap}

trait FrequencyAttribute extends Attribute {
  def getFrequencies: Map[String, Int]
  def setFrequencies(freq: MutableMap[String, Int])
  def addTerm(term: String)
}

class FrequencyAttributeImpl extends AttributeImpl with FrequencyAttribute {
  private var frequencies: MutableMap[String, Int] = MutableHashMap.empty

  def clear() {
  }

  def copyTo(attr: AttributeImpl) {
    attr match {
      case fa: FrequencyAttribute => fa.setFrequencies(frequencies)
      case _ =>
    }
  }

  def getFrequencies: Map[String, Int] =
    frequencies.toMap

  def setFrequencies(freq: MutableMap[String, Int]) {
    frequencies = freq
  }

  def addTerm(term: String) {
    frequencies.get(term) match {
      case None => frequencies += term -> 1
      case Some(prev) => frequencies += term -> (prev + 1)
    }
  }
}
