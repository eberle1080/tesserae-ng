package org.tesserae.utils

import org.slf4j.LoggerFactory
import org.apache.solr.handler.tesserae.RequestContext

import collection.mutable.{Map => MutableMap, Set => MutableSet, HashMap => MutableHashMap, HashSet => MutableHashSet}

object TimerUtils {

  private lazy val logger = LoggerFactory.getLogger(getClass)
  private lazy val lock = new AnyRef
  private lazy val requestStats: MutableMap[RequestContext, MutableMap[String, (Long, Int)]] = new MutableHashMap

  def printRequestStats(implicit context: RequestContext) {
    val map = lock.synchronized {
      val tmp = requestStats.toMap
      requestStats -= context
      tmp
    }

    var list: List[(String, Long, Int, Long)] = Nil
    map.get(context).map { dict =>
      dict.foreach { case (name, stats) =>
        val avg = stats._1 / stats._2
        list = (name, stats._1, stats._2, avg) :: list
      }
    }

    if (!list.isEmpty) {
      logger.debug("Request statistics for request " + context.id)
      list.sortWith(_._4 > _._4).foreach { case (str, _, times, avg) =>
        logger.debug("[AVG] " + str + ": " + avg + " ms (" + times + ")")
      }
    }
  }

  def time[A](str: String, enabled: Boolean = true)(body: => A)(implicit context: RequestContext): A = {
    if (enabled) {
      val start = System.currentTimeMillis()
      val ret = body
      val end = System.currentTimeMillis()
      val diff = end - start

      logger.debug(str + ": " + diff + " ms")

      lock.synchronized {
        val innerDict = requestStats.getOrElseUpdate(context, new MutableHashMap)
        val old: (Long, Int) = innerDict.getOrElse(str, (0, 0))
        val theNew = (old._1 + diff, old._2 + 1)
        innerDict += str -> theNew
      }

      ret
    } else {
      body
    }
  }

  def timeQuietly[A](str: String, enabled: Boolean = true)(body: => A)(implicit context: RequestContext): A = {
    if (enabled) {
      val start = System.currentTimeMillis()
      val ret = body
      val end = System.currentTimeMillis()
      val diff = end - start

      lock.synchronized {
        val innerDict = requestStats.getOrElseUpdate(context, new MutableHashMap)
        val old: (Long, Int) = innerDict.getOrElse(str, (0, 0))
        val theNew = (old._1 + diff, old._2 + 1)
        innerDict += str -> theNew
      }

      ret
    } else {
      body
    }
  }
}
