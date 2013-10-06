package org.apache.solr.analysis.corpus

import java.io._
import org.slf4j.LoggerFactory
import org.tesserae.{EhcacheManager, LevelDBManager}
import net.sf.ehcache.Element
import org.iq80.leveldb.{ReadOptions, DBException}
import scala.Some
import org.apache.solr.handler.tesserae.metrics.CommonMetrics

import collection.mutable.{Set => MutableSet, HashSet => MutableHashSet}

import scala.util.control.Breaks._

class LatinCorpusDatabase(cacheName: Option[String], dbLocation: File) {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  if (dbLocation.exists) {
    if (!dbLocation.isDirectory) {
      throw new IllegalArgumentException("location isn't a directory: " + dbLocation.getPath)
    }
    if (!dbLocation.canRead) {
      throw new IllegalArgumentException("location isn't readable: " + dbLocation.getPath)
    }
  }

  protected lazy val (db, lock) =
    LevelDBManager.dbFor(dbLocation, createIfMissing=true)

  protected lazy val cache =
    EhcacheManager.latinCorpusCache()

  protected def fromByteArray(bytes: Array[Byte]): Int = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis)
    try {
      val any = ois.readObject()
      if (any == null) {
        throw new IllegalArgumentException("No object or null object found in byte array")
      }

      any match {
        case i: java.lang.Integer => i
        case _ => throw new IllegalArgumentException("Byte array does not contain an instance of Int")
      }
    } finally {
      ois.close()
    }
  }

  protected def toByteArray(count: Int): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bos)
    out.writeObject(new java.lang.Integer(count))
    out.flush()
    bos.flush()
    val bytes = bos.toByteArray
    out.close()
    bytes
  }

  protected def makeFreqCacheKey(token: String) = "freq_" + token

  protected def makeTopNCacheKey(n: Int, form: Boolean) = "top_" + n + "_frequencies_" + form.toString

  def getTopN(n: Int, form: Boolean = false): MutableSet[String] = {
    val elem = cache.get(makeTopNCacheKey(n, form))
    if (elem == null) {
      val set = internalGetTopN(n, form)
      val elem = new Element(makeTopNCacheKey(n, form), set)
      cache.put(elem)
      set
    } else {
      elem.getObjectValue match {
        case null => {
          val set = internalGetTopN(n, form)
          val elem = new Element(makeTopNCacheKey(n, form), set)
          cache.put(elem)
          set
        }
        case set: MutableSet[_] => { // close enough
          set.asInstanceOf[MutableSet[String]]
        }
        case _ => {
          val set = internalGetTopN(n, form)
          val elem = new Element(makeTopNCacheKey(n, form), set)
          cache.put(elem)
          set
        }
      }
    }
  }

  protected def internalGetTopN(n: Int, wantForm: Boolean): MutableSet[String] = {
    val timer = CommonMetrics.readTopN.time()
    try {
      if (n <= 0) {
        return new MutableHashSet[String]
      }

      lock.readLock().lock()
      try {
        val snapshot = db.getSnapshot
        try {
          lock.readLock().unlock()
          val opts = new ReadOptions
          opts.fillCache(false)
          opts.verifyChecksums(false)
          opts.snapshot(snapshot)

          val iterator = db.iterator(opts)
          try {
            iterator.seekToFirst()
            var unsortedList: List[(String, Int)] = Nil
            while (iterator.hasNext) {
              val entry = iterator.next()
              val key = new String(entry.getKey, "UTF-8")
              val value = fromByteArray(entry.getValue)
              unsortedList = (key, value) :: unsortedList
            }

            val sorted = unsortedList.sortWith { case ((_: String, a: Int), (_: String, b: Int)) => a > b }
            val set = new MutableHashSet[String]

            breakable {
              sorted.foreach { case (term, count) =>
                val isForm = term.startsWith("_")
                if (isForm == wantForm) {
                  if (isForm) {
                    set += term.substring(1)
                  } else {
                    set += term
                  }
                }

                if (set.size >= n) {
                  break()
                }
              }
            }

            set
          } finally {
            iterator.close()
          }
        } finally {
          snapshot.close()
          lock.readLock().lock()
        }
      } finally {
        lock.readLock().unlock()
      }
    } finally {
      timer.stop()
    }
  }

  def getFrequency(token: String): Option[Int] = {
    lock.readLock().lock()
    try {
      if (token == null) {
        return None
      }

      val cacheKey = makeFreqCacheKey(token)
      val cachedLookup = cache.get(cacheKey)
      if (cachedLookup != null) {
        cachedLookup.getObjectValue match {
          case null => // ignore it
          case freq: java.lang.Integer => return Some(freq)
          case _ => // ignore it
        }
      }

      val key = token.getBytes("UTF-8")
      try {
        db.get(key) match {
          case null => None
          case valueBytes => {
            val freq: Option[Int] = try {
              Some(fromByteArray(valueBytes))
            } catch {
              case e: Exception => {
                logger.warn("Unable to deserialize byte stream", e)
                None
              }
            }

            freq.map { i =>
              val elem = new Element(cacheKey, new java.lang.Integer(i))
              cache.put(elem)
            }

            freq
          }
        }
      } catch {
        case dbe: DBException => {
          logger.warn("Database exception: " + dbe.getMessage, dbe)
          None
        }
      }
    } finally {
      lock.readLock().unlock()
    }
  }

  def incrementFrequency(token: String, inc: Int = 1) {
    lock.writeLock().lock()
    try {
      val oldCount = getFrequency(token).getOrElse(0)
      val newCount = oldCount + inc

      cache.remove(makeFreqCacheKey(token))

      val countBytes = toByteArray(newCount)
      val keyBytes = token.getBytes("UTF-8")

      db.put(keyBytes, countBytes)
    } finally {
      lock.writeLock().unlock()
    }
  }
}
