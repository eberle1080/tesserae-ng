package org.apache.solr.analysis.lexicon

import java.io.File

import org.iq80.leveldb._
import net.sf.ehcache.Element
import org.slf4j.LoggerFactory
import org.tesserae.{LevelDBManager, EhcacheManager}
import lex.db.CSVLine

class LatinLexiconDatabase(cacheName: Option[String], dbLocation: File) {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  if (!dbLocation.exists) {
    throw new IllegalArgumentException("location doesn't exist: " + dbLocation.getPath)
  }
  if (!dbLocation.isDirectory) {
    throw new IllegalArgumentException("location isn't a directory: " + dbLocation.getPath)
  }
  if (!dbLocation.canRead) {
    throw new IllegalArgumentException("location isn't readable: " + dbLocation.getPath)
  }

  protected lazy val (db, lock) =
    LevelDBManager.dbFor(dbLocation)

  protected lazy val cache =
    EhcacheManager.latinLexiconCache(cacheName)

  def lookup(token: String): Option[List[CSVLine]] = {
    lock.readLock().lock()
    try {
      if (token == null) {
        return None
      }

      val cachedLookup = cache.get(token)
      if (cachedLookup != null) {
        val value = cachedLookup.getObjectValue
        value match {
          case null => // ignore it
          case lines: List[_] =>
            lines match {
              case Nil => return Some(Nil)
              case head :: _ => head match {
                case _: CSVLine => return Some(lines.asInstanceOf[List[CSVLine]])
                case _ => // ignore it
              }
            }
          case _ => // ignore it
        }
      }

      val key = token.getBytes("UTF-8")
      try {
        db.get(key) match {
          case null => None
          case valueBytes => {
            val line: List[CSVLine] = try {
              CSVLine.fromByteArray(valueBytes)
            } catch {
              case e: Exception => {
                logger.warn("Unable to deserialize byte stream", e)
                null
              }
            }

            if (line != null) {
              val elem = new Element(token, line)
              cache.put(elem)
            }

            Option(line)
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
}
