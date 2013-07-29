package org.apache.solr.handler.tesserae

import org.apache.lucene.util.Attribute
import org.apache.lucene.util.AttributeImpl

object NGramsGenerator {
  import scala.collection.JavaConversions._
  import scala.collection.mutable.{Queue => MutableQueue}
  import java.util.StringTokenizer

  // each ngram is represented as a List of String
  def generate(text: String, minSize: Int, maxSize: Int) = {
    if (maxSize < minSize) {
      throw new IllegalArgumentException("maxSize must be >= minSize")
    }
    if (minSize <= 0) {
      throw new IllegalArgumentException("minSize must be > 0")
    }
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize must be > 0")
    }

    // iterate for each token on the available ngrams
    for (ngramList <- this.generate_sub(text, minSize, maxSize); ngram <- ngramList)
      yield ngram.toList
  }

  // generate a list of ngrams
  def generate_sub(text: String, minSize: Int, maxSize: Int) = {
    val nGramsBuffer = new NGramsBuffer(minSize, maxSize)
    for (t <- this.getTokenizerIterator(text))
      yield {
        nGramsBuffer.addToken(t.asInstanceOf[String])
        nGramsBuffer.getNGrams
      }
  }

  // Can be overloaded to use an other tokenizer
  def getTokenizerIterator(text: String) =
    new StringTokenizer(text, "")

  // A utility class that stores a list of fixed size queues, each queue is a current ngram
  class NGramsBuffer(val minSize: Int, val maxSize: Int) {
    val queues = minSize.to(maxSize).foldLeft(List[SlidingFixedSizeQueue[String]]()) { (list, n) =>
      new SlidingFixedSizeQueue[String](n) :: list
    }

    def addToken(token: String) = queues.foreach(q => q.enqueue(token))

    // return only buffers that are full, otherwise not enough tokens have been
    // see yet to fill in the NGram
    def getNGrams = queues.filter(q => q.size == q.maxSize)
  }

  // Utility class to store the last maxSize encountered tokens
  class SlidingFixedSizeQueue[A](val maxSize: Int) extends scala.collection.mutable.Queue[A] {
    override def enqueue(elems: A*) = {
      elems.foreach(super.enqueue(_))
      while (this.size > this.maxSize)
        this.dequeue()
    }
  }
}

trait NGramAttribute extends Attribute {
  def setMinSize(size: Int)
  def setMaxSize(size: Int)
  def prependNGram(ngram: String)
  def appendNGram(ngram: String)
  def setNGrams(ngrams: List[String])
  def getNGrams: List[String]
  def setFromString(str: String)
}

class NGramAttributeImpl extends AttributeImpl with NGramAttribute {
  protected var minSize: Int = 3
  protected var maxSize: Int = 3
  protected var ngramList: List[String] = Nil

  def setMinSize(size: Int) {
    if (size < 1) {
      throw new IllegalArgumentException("size must be > 0")
    }
    minSize = size
  }

  def setMaxSize(size: Int) {
    if (size < 1) {
      throw new IllegalArgumentException("size must be > 0")
    }
    maxSize = size
  }

  def prependNGram(ngram: String) {
    ngramList = ngram :: ngramList
  }

  def appendNGram(ngram: String) {
    ngramList = ngramList ::: List(ngram)
  }

  def setNGrams(ngrams: List[String]) {
    ngramList = ngrams
  }

  def getNGrams: List[String] = ngramList

  def copyTo(attr: AttributeImpl) {
    attr match {
      case ng: NGramAttribute =>
        ng.setMinSize(minSize)
        ng.setMaxSize(maxSize)
      case _ =>
    }
  }

  def clear() {
    ngramList = Nil
  }

  def setFromString(str: String) {
    ngramList = Nil
    NGramsGenerator.generate(str, minSize, maxSize).foreach { ngrams =>
      ngramList = ngramList ::: ngrams
    }
  }
}
