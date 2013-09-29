package lex.db

import java.io._
import scala.Serializable

case class CSVLine(token: String, partOfSpeech: String, stem: String) extends Serializable

object CSVLine {

  def toByteArray(lines: List[CSVLine]): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bos)
    out.writeObject(lines)
    out.flush()
    bos.flush()
    val bytes = bos.toByteArray
    out.close()
    bytes
  }

  def fromByteArray(bytes: Array[Byte]): List[CSVLine] = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis)
    try {
      val any = ois.readObject()
      if (any == null) {
        throw new IllegalArgumentException("No object or null object found in byte array")
      }

      any match {
        case lines: List[_] =>
          lines match {
            case Nil => Nil
            case head :: _ => head match {
              case _: CSVLine => lines.asInstanceOf[List[CSVLine]]
              case _ => throw new IllegalArgumentException("Byte array does not contain an instance of List[CSVLine]")
            }
          }
        case _ => throw new IllegalArgumentException("Byte array does not contain an instance of List[CSVLine]")
      }
    } finally {
      ois.close()
    }
  }
}
