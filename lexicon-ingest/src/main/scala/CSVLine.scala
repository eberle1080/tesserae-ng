package org.tesserae.lexicon.db

import java.io._
import scala.Serializable

case class CSVLine(token: String, partOfSpeech: String, stem: String) extends Serializable

object CSVLine {

  def toByteArray(line: CSVLine): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bos)
    out.writeObject(line)
    out.flush()
    bos.flush()
    val bytes = bos.toByteArray
    out.close()
    bytes
  }

  def fromByteArray(bytes: Array[Byte]): CSVLine = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis)
    try {
      val any = ois.readObject()
      if (any == null) {
        throw new IllegalArgumentException("No object or null object found in byte array")
      }

      any match {
        case line: CSVLine => line
        case _ => throw new IllegalArgumentException("Byte array does not contain an instance of CSVLine")
      }
    } finally {
      ois.close()
    }
  }
}
