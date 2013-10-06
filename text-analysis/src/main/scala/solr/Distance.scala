package org.apache.solr.handler.tesserae

import org.apache.solr.handler.tesserae.DataTypes.SortedFrequencies

object DistanceMetrics extends Enumeration {
  val FREQ, FREQ_TARGET, FREQ_SOURCE, SPAN, SPAN_TARGET, SPAN_SOURCE = Value
  val DEFAULT_METRIC = FREQ

  def apply(str: String): Option[Value] = {
    if (str == null) {
      return None
    }
    str.trim.toLowerCase match {
      case "freq" => Some(FREQ)
      case "freq_target" => Some(FREQ_TARGET)
      case "freq_source" => Some(FREQ_SOURCE)
      case "span" => Some(SPAN)
      case "span_target" => Some(SPAN_TARGET)
      case "span_source" => Some(SPAN_SOURCE)
      case _ => None
    }
  }

  def apply(metric: Value): Option[Distance] = {
    metric match {
      case FREQ => Some(new FreqDistance)
      case FREQ_TARGET => Some(new FreqTargetDistance)
      case FREQ_SOURCE => Some(new FreqSourceDistance)
      case SPAN => Some(new SpanDistance)
      case SPAN_TARGET => Some(new SpanTargetDistance)
      case SPAN_SOURCE => Some(new SpanSourceDistance)
      case _ => None
    }
  }
}

trait Distance {
  def calculateDistance(params: DistanceParameters): Option[Int]
}

// Some re-usable distance functions
trait DistanceMixin {
  import DataTypes._

  protected def sortedFreq(id: Int, info: QueryInfo, freqInfo: SortedFrequencies): List[TermFrequencyEntry] = {
    var frequencies: List[TermFrequencyEntry] = Nil
    val termInfo = info.termInfo(id).termCounts

    freqInfo.foreach { entry =>
      val term = TextTerm(entry.term, form=true, resolved=false)
      termInfo.get(term).map { ct =>
        if (ct > 0) {
          frequencies = entry :: frequencies

          // Since we only use the first 2 anyway...
          if (frequencies.length >= 2) {
            return frequencies
          }
        }
      }
    }

    frequencies
  }

  protected def sortedPositions(id: Int, info: QueryInfo): List[TermPosition] = {
    val posTerms = info.termInfo(id).positionTerms
    val positions: List[(Int, Int)] = posTerms.keySet.toList.sorted

    var list: List[TermPosition] = Nil
    positions.foreach { pos =>
      posTerms(pos).filter { tp => tp.term.form }.toList

      list = list ::: posTerms(pos).toList
    }

    list
  }

  protected def distanceBetween(p0: TermPosition, p1: TermPosition) = {
    import math.abs
    // the perl one worries about words vs. non-words. I don't have this problem
    // because everything in the term vector is guaranteed to be a real word
    abs(p1.position._1 - p0.position._1) + 1
  }
}

class FreqDistance extends Distance with DistanceMixin {

  protected def internalDistance(docID: Int, info: QueryInfo, freqInfo: SortedFrequencies): Option[Int] = {
    val terms = sortedFreq(docID, info, freqInfo)
    if (terms.length < 2) {
      None
    } else {
      val positions = sortedPositions(docID, info)
      val (tt0, tt1) = (terms(0).term, terms(1).term)
      val (t0, t1) = try {
        val _t0 = positions.find(tp => tp.term.text == tt0).get
        val _t1 = positions.find(tp => tp.term.text == tt1).get
        (_t0, _t1)
      } catch {
        case e: NoSuchElementException =>
          // one of the "find" methods failed
          return None
      }

      Some(distanceBetween(t0, t1))
    }
  }

  def calculateDistance(params: DistanceParameters): Option[Int] = {
    internalDistance(params.pair.sourceDoc, params.source, params.frequencies.sourceFrequencies) match {
      case None => None
      case Some(sourceDist) =>
        internalDistance(params.pair.targetDoc, params.target, params.frequencies.targetFrequencies) match {
          case None => None
          case Some(targetDist) => Some(sourceDist + targetDist)
        }
    }
  }
}

class FreqTargetDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.targetDoc, params.target, params.frequencies.targetFrequencies)
}

class FreqSourceDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.sourceDoc, params.source, params.frequencies.sourceFrequencies)
}

class SpanDistance extends Distance with DistanceMixin {
  protected def internalDistance(docID: Int, info: QueryInfo): Option[Int] = {
    val positions = sortedPositions(docID, info)
    if (positions.length < 2) {
      None
    } else {
      val first = positions.head
      val last = positions.takeRight(1).head
      Some(distanceBetween(first, last))
    }
  }

  def calculateDistance(params: DistanceParameters) = {
    internalDistance(params.pair.sourceDoc, params.source) match {
      case None => None
      case Some(sourceDist) =>
        internalDistance(params.pair.targetDoc, params.target) match {
          case None => None
          case Some(targetDist) => Some(sourceDist + targetDist)
        }
    }
  }
}

class SpanTargetDistance extends SpanDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.targetDoc, params.target)
}

class SpanSourceDistance extends SpanDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.sourceDoc, params.source)
}
