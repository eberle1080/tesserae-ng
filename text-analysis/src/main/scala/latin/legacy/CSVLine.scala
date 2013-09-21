package org.apache.lucene.analysis.la.legacy

import java.io._
import scala.Serializable
import org.apache.solr.handler.tesserae.metrics.CommonMetrics
import org.slf4j.LoggerFactory

case class CSVLine(token: String, partOfSpeech: String, stem: String) extends Serializable

object CSVLine {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def fromLine(line: String): CSVLine = {
    if (line == null) {
      throw new IllegalArgumentException("line is null")
    }

    val normalized = line.trim
    val parts = normalized.split(",", 3)
    if (parts.length != 3) {
      throw new IllegalArgumentException("malformatted line, not enough commas")
    }

    val token = parts(0).trim
    val metadata = parts(1).trim
    val stem = parts(2).trim

    if (token.isEmpty) {
      throw new IllegalArgumentException("token column is empty")
    } else if (metadata.isEmpty) {
      throw new IllegalArgumentException("metadata column is empty")
    } else if (stem.isEmpty) {
      throw new IllegalArgumentException("stem column is empty")
    }

    CSVLine(token, metadata.substring(0, 1), stem)
  }

  def fromFile(path: String, reader: LineNumberReader): Map[String, CSVLine] = {
    val timer = CommonMetrics.csvParseTime.time()
    try {
      var map: Map[String, CSVLine] = Map.empty
      var line = reader.readLine()
      while (line != null) {
        line = line.trim
        if (!line.isEmpty) {
          try {
            val csvLine = fromLine(line)
            map += csvLine.token -> csvLine
          } catch {
            case ex: Exception => {
              throw new RuntimeException("Malformatted CSV file [" + path + "] at line " + reader.getLineNumber, ex)
            }
          }
        }
      }
      map
    } finally {
      timer.stop()
    }
  }

  protected def internalFromFile(file: File): Map[String, CSVLine] = {
    logger.debug("Loading CSV file: " + file.getPath)
    try {
      usingFileInputStream(file) { fis =>
        usingBufferedInputStream(fis, autoClose=false) { bis =>
          usingInputStreamReader(bis, autoClose=false) { isr =>
            usingLineNumberReader(isr, autoClose=false) { reader =>
              fromFile(file.getAbsolutePath, reader)
            }
          }
        }
      }
    } finally {
      logger.debug("Done loading CSV file")
    }
  }

  protected def fromCache(file: File): Map[String, CSVLine] = {
    logger.debug("Loading CSV cache file: " + file.getPath)
    try {
      usingFileInputStream(file) { fis =>
        usingBufferedInputStream(fis, autoClose=false) { bis =>
          val in = new ObjectInputStream(bis)
          in.readObject.asInstanceOf[Map[String, CSVLine]]
        }
      }
    } finally {
      logger.debug("Done loading CSV cache file")
    }
  }

  protected def storeCache(map: Map[String, CSVLine], cacheFile: File) {
    usingFileOutputStream(cacheFile) { fos =>
      usingBufferedOutputStream(fos) { bos =>
        val out = new ObjectOutputStream(bos)
        out.writeObject(map)
        out.flush()
        out.close()
      }
    }
  }

  def fromFile(file: File): Map[String, CSVLine] = {
    val cacheFile = new File(file.getPath + ".obj")
    val (map: Map[String, CSVLine], fc: Boolean) =
      if (cacheFile.exists && cacheFile.isFile && cacheFile.canRead) {
        (fromCache(file), true)
      } else {
        (internalFromFile(file), false)
      }

    if (!fc) {
      storeCache(map, cacheFile)
    }

    map
  }

  protected def usingFileInputStream[A](file: File, autoClose: Boolean = true)(body: FileInputStream => A): A = {
    val is = new FileInputStream(file)
    try {
      body(is)
    } finally {
      if (autoClose) {
        is.close()
      }
    }
  }

  protected def usingFileOutputStream[A](file: File, autoFlush: Boolean = true, autoClose: Boolean = true)(body: FileOutputStream => A): A = {
    val fos = new FileOutputStream(file)
    try {
      body(fos)
    } finally {
      if (autoFlush) {
        fos.flush()
      }
      if (autoClose) {
        fos.close()
      }
    }
  }

  protected def usingBufferedInputStream[A](is: InputStream, autoClose: Boolean = true)(body: BufferedInputStream => A): A = {
    val bis = new BufferedInputStream(is)
    try {
      body(bis)
    } finally {
      if (autoClose) {
        is.close()
      }
    }
  }

  protected def usingBufferedOutputStream[A](os: OutputStream, autoFlush: Boolean = true, autoClose: Boolean = true)(body: BufferedOutputStream => A): A = {
    val bos = new BufferedOutputStream(os)
    try {
      body(bos)
    } finally {
      if (autoFlush) {
        bos.flush()
      }
      if (autoClose) {
        bos.close()
      }
    }
  }

  protected def usingInputStreamReader[A](is: InputStream, autoClose: Boolean = true)(body: InputStreamReader => A): A = {
    val reader = new InputStreamReader(is)
    try {
      body(reader)
    } finally {
      if (autoClose) {
        reader.close()
      }
    }
  }

  protected def usingLineNumberReader[A](reader: Reader, autoClose: Boolean = true)(body: LineNumberReader => A): A = {
    val lnr = new LineNumberReader(reader)
    try {
      body(lnr)
    } finally {
      if (autoClose) {
        lnr.close()
      }
    }
  }
}
