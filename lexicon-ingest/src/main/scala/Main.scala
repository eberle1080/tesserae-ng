package org.tesserae.lexicon.ingest

import au.com.bytecode.opencsv._
import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import java.io._
import org.slf4j.LoggerFactory
import lex.db.CSVLine
import collection.mutable.{Set => MutableSet, HashSet => MutableHashSet}

/**
 * How to normalize the keys
 */
sealed trait NormalizeLevel {
  def name: String
}

/**
 * No normalization should be performed
 */
object NoNormalization extends NormalizeLevel {
  def name: String = "none"
}

/**
 * Do some normalization
 */
object PartialNormalization extends NormalizeLevel {
  def name: String = "partial"
}

/**
 * Do full normalization
 */
object FullNormalization extends NormalizeLevel {
  def name: String = "full"
}

object Main {

  /**
   * A logger
   */
  private lazy val logger = LoggerFactory.getLogger("Main")

  /**
   * Print out the usage and exit
   * @param code The exit code (0 means success)
   */
  private def usage(code: Int) {
    val out = if (code == 0) { System.out } else { System.err }
    out.println("Usage: java -jar lexicon-ingest.jar [OPTIONS]...")
    out.println()
    out.println("Options:")
    out.println("  -i, --input=FILE         An input CSV file (required)")
    out.println("  -o, --output=DIR         An output database directory (required)")
    out.println("  -k, --key-norm=LEVEL     Normalize keys (optional, one of 'none', 'partial',")
    out.println("                           or 'full'). Default is 'full'")
    out.println("  -h, --help               Show this helpful message and exit")
    System.exit(code)
  }

  /**
   * Parse the command line options
   *
   * @param args The command line arguments
   * @return A tuple of (input file, output db directory, normalization level)
   */
  private def parseArgs(args: Array[String]) = {
    import gnu.getopt.{Getopt, LongOpt}

    val longopts = Array(
      new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
      new LongOpt("input", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
      new LongOpt("output", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
      new LongOpt("key-norm", LongOpt.REQUIRED_ARGUMENT, null, 'k')
    )

    var c = -1
    var input: File = null
    var output: File = null
    var normalizeKeys: NormalizeLevel = FullNormalization
    val g = new Getopt("lexicon-ingest", args, "hi:o:k:", longopts)

    c = g.getopt()
    while(c != -1) {
      c match {
        case 'h' => usage(0)
        case 'i' => input = new File(g.getOptarg)
        case 'o' => output = new File(g.getOptarg)
        case 'k' => {
          normalizeKeys = g.getOptarg match {
            case "none" => NoNormalization
            case "partial" => PartialNormalization
            case "full" => FullNormalization
            case other => {
              System.err.println("Error: unknown key-norm level: `" + other + "'")
              System.exit(1); NoNormalization
            }
          }
        }
        case o => {
          System.err.println("Error: unhandled option: `" + o + "'")
          System.exit(1)
        }
      }
      c = g.getopt()
    }

    if (input == null || output == null) {
      usage(1)
    }

    (input, output, normalizeKeys)
  }

  /**
   * Make sure a file is readable
   * @param file A file
   */
  private def sanityCheckPath(file: File) {
    if (!file.exists) {
      logger.error("Path doesn't exist: `" + file.getPath + "'"); System.exit(1)
    }
    if (!file.isFile) {
      logger.error("Path isn't a file: `" + file.getPath + "'"); System.exit(1)
    }
    if (!file.canRead) {
      logger.error("Path isn't readable: `" + file.getPath + "'"); System.exit(1)
    }
  }

  /**
   * Use a FileInputStream and then automatically close it
   *
   * @param file A file to read
   * @param autoClose if true the stream is automatically closed
   * @param body A callback
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingFileInputStream[A](file: File, autoClose: Boolean = true)(body: FileInputStream => A): A = {
    val is = new FileInputStream(file)
    try {
      body(is)
    } finally {
      if (autoClose) {
        is.close()
      }
    }
  }

  /**
   * Use a BufferedInputStream and then automatically close it
   *
   * @param is An input stream to buffer
   * @param autoClose if true the stream is automatically closed
   * @param body A callback
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingBufferedInputStream[A](is: InputStream, autoClose: Boolean = true)(body: BufferedInputStream => A): A = {
    val bis = new BufferedInputStream(is)
    try {
      body(bis)
    } finally {
      if (autoClose) {
        is.close()
      }
    }
  }

  /**
   * Use an InputStreamReader and then automatically close it
   *
   * @param is An input stream to wrap in a reader
   * @param autoClose if true the reader is automatically closed
   * @param body A callback
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingInputStreamReader[A](is: InputStream, autoClose: Boolean = true)(body: InputStreamReader => A): A = {
    val reader = new InputStreamReader(is)
    try {
      body(reader)
    } finally {
      if (autoClose) {
        reader.close()
      }
    }
  }

  /**
   * Use a LineNumberReader and then automatically close it
   *
   * @param reader A reader to wrap in a LineNumberReader
   * @param autoClose if true the reader is automatically closed
   * @param body A callback
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingLineNumberReader[A](reader: Reader, autoClose: Boolean = true)(body: LineNumberReader => A): A = {
    val lnr = new LineNumberReader(reader)
    try {
      body(lnr)
    } finally {
      if (autoClose) {
        lnr.close()
      }
    }
  }

  /**
   * Use a CSVReader and then automatically close it
   *
   * @param reader A reader to wrap in a CSVReader
   * @param autoClose if true the reader is automatically closed
   * @param body A callback
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingCSVReader[A](reader: Reader, autoClose: Boolean = true)(body: CSVReader => A): A = {
    val csv = new CSVReader(reader)
    try {
      body(csv)
    } finally {
      if (autoClose) {
        csv.close()
      }
    }
  }

  /**
   * Open a file as a CSV file and use it
   *
   * @param file A file
   * @param callback Something that uses the CSV-parsed file
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingCSVReader[A](file: File)(callback: CSVReader => A): A = {
    sanityCheckPath(file)
    usingFileInputStream(file) { fis =>
      usingBufferedInputStream(fis, autoClose=false) { bis =>
        usingInputStreamReader(bis, autoClose=false) { isr =>
          usingLineNumberReader(isr, autoClose=false) { lineReader =>
            try {
              usingCSVReader(lineReader, autoClose=false) { csv =>
                callback(csv)
              }
            } catch {
              case e: Exception =>
                val line = lineReader.getLineNumber
                logger.error("Error while reading " + file.getName + ":" + line + " => " + e.getMessage, e)
                System.exit(1)
                throw e // to shut the compiler up
            }
          }
        }
      }
    }
  }

  /**
   * Use a LevelDB database and then close it when finished
   *
   * @param file A LevelDB database directory
   * @param callback Something that uses the LevelDB database
   * @tparam A An arbitrary return type
   * @return Whatever the body returns
   */
  private def usingDatabase[A](file: File)(callback: DB => A): A = {
    val opts = new Options
    opts.createIfMissing(true)
    opts.compressionType(CompressionType.NONE)
    val db = factory.open(file, opts)
    try {
      callback(db)
    } finally {
      db.close()
    }
  }

  /**
   * Get the plural version of a string
   *
   * @param i An integer
   * @param singular The singular version of a word
   * @param plural The plural version of a word
   * @return A plural string for the given count
   */
  private def plural(i: Int, singular: String, plural: String): String = {
    i match {
      case 1 => "1 " + singular
      case n => n + " " + plural
    }
  }

  /**
   * Replace v with u, and j with i
   *
   * @param lowerCase A lower-case string
   * @return A string with v -> u, j -> i
   */
  private def replaceVJ(lowerCase: String) =
    lowerCase.
      replaceAllLiterally("v", "u").
      replaceAllLiterally("j", "i")

  /**
   * A regex that matches non-alphabetic characters
   */
  private val nonCharacters = "[0-9]".r

  /**
   * Normalize a key fully
   *
   * @param str A string
   * @return A normalized string
   */
  private def normalize(str: String) = {
    val replaced = replaceVJ(str.toLowerCase)
    nonCharacters.replaceAllIn(replaced, "")
  }

  /**
   * Determine if a stem is a special protected stem
   *
   * @param stem A stem
   * @return true if the stem is protected
   */
  private def isProtectedStem(stem: String) = stem match {
    case "sum" => true
    case _ => false
  }

  /**
   * Insert a single CSV line into the database
   *
   * @param line A parsed line from the CSV file
   * @param db The LevelDB database
   * @param keys A collection of all seen keys
   * @param normalizeLevel The user-defined normalization level
   * @return true if the line was actually inserted
   */
  private def processLine(line: Array[String], db: DB, keys: MutableSet[String], normalizeLevel: NormalizeLevel): Boolean = {
    if (line.length != 3) {
      throw new RuntimeException("Expected 3 columns")
    }

    val obj = CSVLine(line(0), line(1), normalize(line(2)))

    val key = normalizeLevel match {
      case NoNormalization => obj.token
      case PartialNormalization => replaceVJ(obj.token.toLowerCase)
      case FullNormalization => normalize(obj.token)
    }

    if (!keys.contains(key)) {
      keys += key
    }

    val key_bytes = key.getBytes("UTF-8")
    val old_lst: List[CSVLine] = if (isProtectedStem(obj.stem)) {
      Nil
    } else {
      db.get(key_bytes) match {
        case null => Nil
        case valueBytes => {
          val lst: List[CSVLine] = try {
            CSVLine.fromByteArray(valueBytes)
          } catch {
            case e: Exception => {
              logger.warn("Unable to deserialize byte stream", e)
              null
            }
          }
          Option(lst).getOrElse(Nil)
        }
      }
    }

    old_lst.foreach { other =>
      // Eliminate duplicate stems, and
      if (other.stem == obj.stem) {
        return false
      } else if (isProtectedStem(other.stem)) {
        return false
      }
    }

    val new_lst = obj :: old_lst
    val sorted = new_lst.sortWith { (a, b) => a.stem.compareTo(b.stem) < 0 }
    val value_bytes = CSVLine.toByteArray(sorted)

    db.put(key_bytes, value_bytes)
    true
  }

  /**
   * Entry point. Read a CSV file line by line, and add it to a LevelDB database.
   *
   * @param args The command line arguments
   */
  def main(args: Array[String]) = {
    val (input, output, normalizeKeys) = parseArgs(args)
    usingCSVReader(input) { csv =>
      usingDatabase(output) { db =>
        logger.info("Ingesting lexicon from " + input.getName + "...")
        logger.info("  => Key normalization level: " + normalizeKeys.name)

        var lineCount = 0
        var line: Array[String] = csv.readNext()
        val keys: MutableSet[String] = new MutableHashSet[String]

        while (line != null) {
          processLine(line, db, keys, normalizeKeys)
          lineCount += 1
          if (lineCount % 100000 == 0) {
            logger.info("  => Processed " + plural(lineCount, "line", "lines") + " so far...")
          }
          line = csv.readNext()
        }

        logger.info("  => Processed " + plural(lineCount, "line", "lines") + ".")
        logger.info("  => Found " + plural(keys.size, "unique token", "unique tokens"))
      }
    }
  }
}
