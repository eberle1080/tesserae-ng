package org.tesserae

import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.iq80.leveldb.{CompressionType, Options, DB}
import java.io.File
import org.fusesource.leveldbjni.JniDBFactory._
import scala.Some

object LevelDBManager {
  private lazy val logger = LoggerFactory.getLogger(getClass)
  private val lock = new ReentrantReadWriteLock(true)
  private var databases: Map[String, (DB, ReentrantReadWriteLock)] = Map.empty

  private def loadDB(dbLocation: File, createIfMissing: Boolean) = {
    val opts = new Options
    opts.createIfMissing(createIfMissing)
    opts.compressionType(CompressionType.NONE)
    val _db = factory.open(dbLocation, opts)
    if (_db == null) {
      throw new IllegalStateException("db is null (couldn't load database?)")
    }
    logger.info("Loaded database: " + dbLocation.getPath)
    _db
  }

  def dbFor(dbLocation: File, createIfMissing: Boolean = false) = {
    val key = dbLocation.getAbsoluteFile.getCanonicalPath
    lock.readLock().lock()
    try {
      databases.get(key) match {
        case Some(tuple) => tuple
        case None => {
          lock.readLock().unlock()
          lock.writeLock().lock()
          try {
            databases.get(key) match {
              case Some(tuple) => tuple
              case None => {
                val db = loadDB(dbLocation, createIfMissing)
                val dbLock = new ReentrantReadWriteLock(true)
                val tuple = (db, dbLock)
                databases += key -> tuple
                tuple
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
