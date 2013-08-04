package org.apache.solr.handler.tesserae

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
  import TesseraeCompareHandler.TermPositionsList

  protected def sortedFreq(id: Int, info: QueryInfo): List[(Int, String)] = {
    val terms = info.termInfo(id).termCounts
    var counts: List[(Int, String)] = Nil
    terms.foreach { case (term, freq) =>
      counts = (freq, term) :: counts
    }

    counts.sortWith { (a, b) => a._1 < b._1 }
  }

  protected def filterPositions(tpl: TermPositionsList): TermPositionsList = {
    var entriesMap: Map[Int, String] = Map.empty
    tpl.foreach { entry =>
      val termLen = entry.term.length
      entriesMap.get(entry.position) match {
        case None =>
          entriesMap += entry.position -> entry.term
        case Some(priorTerm) =>
          val prLen = priorTerm.length
          if (termLen > prLen) {
            entriesMap += entry.position -> entry.term
          } else if (termLen == prLen) {
            // choose alphabetically
            if (entry.term.compareTo(priorTerm) > 0) {
              entriesMap += entry.position -> entry.term
            }
          } else {
            // leave the old one alone
          }
      }
    }

    var list: TermPositionsList = Nil
    entriesMap.foreach { case (pos, term) =>
      list = TermPositionsListEntry(term, pos) :: list
    }

    list
  }

  protected def sortedPositions(id: Int, info: QueryInfo, filter: Boolean = true): TermPositionsList = {
    val terms = info.termInfo(id).termPositions
    val filtered = if (filter) { filterPositions(terms) } else { terms }
    terms.sortWith { (a, b) => a.position < b.position }
  }

  protected def distanceBetween(p0: TermPositionsListEntry, p1: TermPositionsListEntry) = {
    import math.abs
    // the perl one worries about words vs. non-words. I don't have this problem
    // because everything in the term vector is guaranteed to be a real word
    abs(p1.position - p0.position) + 1
  }
}

class FreqDistance extends Distance with DistanceMixin {

  protected def internalDistance(docID: Int, info: QueryInfo): Option[Int] = {
    val terms = sortedFreq(docID, info)
    if (terms.length < 2) {
      None
    } else {
      val positions = sortedPositions(docID, info)
      val (tt0, tt1) = (terms(0)._2, terms(1)._2)
      val (t0, t1) = try {
        val _t0 = positions.find(tp => tp.term == tt0).get
        val _t1 = positions.find(tp => tp.term == tt1).get
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

class FreqTargetDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.targetDoc, params.target)
}

class FreqSourceDistance extends FreqDistance {
  override def calculateDistance(params: DistanceParameters) =
    internalDistance(params.pair.sourceDoc, params.source)
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
