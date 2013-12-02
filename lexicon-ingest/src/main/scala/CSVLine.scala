package lex.db

import java.io._
import scala.Serializable

/**
 * Represents a single line from the CSV file
 *
 * @param token The original token
 * @param partOfSpeech Part of speech (not used)
 * @param stem The stem
 */
case class CSVLine(token: String, partOfSpeech: String, stem: String) extends Serializable

object CSVLine {

  /**
   * Convert multiple CSVLine entries into a byte array
   *
   * @param lines One or more CSVLine entries
   * @return
   */
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

  /**
   * Convert a byte array into a list of CSVLine entries
   *
   * @param bytes A non-null byte array
   * @return A list of CSVLine entires
   */
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
