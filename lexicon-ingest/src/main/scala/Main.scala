package org.tesserae.lexicon.ingest

import au.com.bytecode.opencsv._
import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import java.io._
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import org.tesserae.lexicon.db.CSVLine

object Main {

  private lazy val logger = LoggerFactory.getLogger("Main")

  private def usage(code: Int) {
    val out = if (code == 0) { System.out } else { System.err }
    out.println("Usage: java -jar lexicon-ingest.jar [OPTIONS]...")
    out.println()
    out.println("Options:")
    out.println("  -i, --input=FILE         An input CSV file (required)")
    out.println("  -o, --output=DIR         An output database directory (required)")
    out.println("  -h, --help               Show this helpful message and exit")
    System.exit(code)
  }

  private def parseArgs(args: Array[String]) = {
    import gnu.getopt.{Getopt, LongOpt}

    val longopts = Array(
      new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
      new LongOpt("input", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
      new LongOpt("output", LongOpt.REQUIRED_ARGUMENT, null, 'o')
    )

    var c = -1
    var input: File = null
    var output: File = null
    val g = new Getopt("lexicon-ingest", args, "hi:o:", longopts)

    c = g.getopt()
    while(c != -1) {
      c match {
        case 'h' => usage(0)
        case 'i' => input = new File(g.getOptarg)
        case 'o' => output = new File(g.getOptarg)
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

    (input, output)
  }

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

  private def plural(i: Int, singular: String, plural: String): String = {
    i match {
      case 1 => "1 " + singular
      case n => n + " " + plural
    }
  }

  def processLine(line: Array[String], writeBatch: WriteBatch, entryCount: AtomicInteger): Boolean = {
    if (line.length != 3) {
      throw new RuntimeException("Expected 3 columns")
    }

    val obj = CSVLine(line(0).toLowerCase, line(1).toLowerCase, line(2).toLowerCase)
    val value = CSVLine.toByteArray(obj)
    val key = obj.token.getBytes("UTF-8")

    writeBatch.put(key, value)
    entryCount.getAndAdd(1)

    true
  }

  def main(args: Array[String]) = {
    val (input, output) = parseArgs(args)
    usingCSVReader(input) { csv =>
      logger.info("Ingesting lexicon from " + input.getName + "...")
      usingDatabase(output) { db =>
        val writeCount = new AtomicInteger(0)
        val writeBatch = db.createWriteBatch()
        try {
          var lineCount = 0
          var line: Array[String] = csv.readNext()
          while (line != null) {
            if (processLine(line, writeBatch, writeCount)) {
              lineCount += 1
            }
            line = csv.readNext()
          }

          logger.info("  => Read " + plural(lineCount, "line", "lines"))
          db.write(writeBatch)

          logger.info("  => Wrote " + plural(writeCount.get(), "entry", "entries"))
        } finally {
          writeBatch.close()
        }
      }
    }
  }
}
