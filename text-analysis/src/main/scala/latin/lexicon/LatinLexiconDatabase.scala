package org.apache.solr.analysis.lexicon

import java.io.File

import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import net.sf.ehcache.Element
import org.slf4j.LoggerFactory
import org.tesserae.EhcacheManager
import org.tesserae.lexicon.db.CSVLine
import java.util.concurrent.locks.ReentrantReadWriteLock

object LatinLexiconDatabase {
  private lazy val logger = LoggerFactory.getLogger(getClass)
  private val lock = new ReentrantReadWriteLock(true)
  private var databases: Map[String, DB] = Map.empty

  private def loadDB(dbLocation: File) = {
    val opts = new Options
    opts.createIfMissing(false)
    opts.compressionType(CompressionType.NONE)
    val _db = factory.open(dbLocation, opts)
    if (_db == null) {
      throw new IllegalStateException("db is null (couldn't load database?)")
    }
    logger.info("Loaded lexicon database: " + dbLocation.getPath)
    _db
  }

  def dbFor(dbLocation: File) = {
    val key = dbLocation.getAbsoluteFile.getCanonicalPath
    lock.readLock().lock()
    try {
      databases.get(key) match {
        case Some(db) => db
        case None => {
          lock.readLock().unlock()
          lock.writeLock().lock()
          try {
            databases.get(key) match {
              case Some(db) => db
              case None => {
                val db = loadDB(dbLocation)
                databases += key -> db
                db
              }
            }
          } finally {
            lock.writeLock().unlock()
            lock.readLock().lock()
          }
        }
      }
    } finally {
      lock.readLock().unlock()
    }
  }
}

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

  protected lazy val db =
    LatinLexiconDatabase.dbFor(dbLocation)

  protected lazy val cache =
    EhcacheManager.latinLexiconCache(cacheName)

  def lookup(token: String): Option[CSVLine] = {
    if (token == null) {
      return None
    }

    val cachedLookup = cache.get(token)
    if (cachedLookup != null) {
      val value = cachedLookup.getObjectValue
      value match {
        case line: CSVLine => return Some(line)
        case _ => // ignore it
      }
    }

    val key = token.getBytes("UTF-8")
    try {
      val bytes = db.synchronized {
        db.get(key)
      }

      bytes match {
        case null => None
        case valueBytes => {
          val line: CSVLine = try {
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
  }
}
