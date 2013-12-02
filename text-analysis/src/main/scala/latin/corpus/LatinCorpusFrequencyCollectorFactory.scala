package org.apache.solr.analysis.corpus

import java.util.{Map => JavaMap}
import org.apache.lucene.analysis.util.TokenFilterFactory
import org.apache.lucene.analysis.TokenStream
import java.io.File

/**
 * A factory that creates LatinCorpusFrequencyCollector instances
 *
 * @param args Arguments passed in through schema.xml
 */
class LatinCorpusFrequencyCollectorFactory(args: JavaMap[String, String]) extends TokenFilterFactory(args) {
  if (!args.containsKey("database")) {
    throw new IllegalArgumentException("can't instantiate LatinCorpusFrequencyCollectorFactory without a 'database' argument")
  }

  private val db = {
    val corpusFreqDBLocation = args.get("database")
    val corpusCacheName = Option(args.get("corpusCacheName"))
    val corpusDir = new File(corpusFreqDBLocation)
    new LatinCorpusDatabase(corpusCacheName, corpusDir)
  }

  def create(input: TokenStream): TokenStream =
    new LatinCorpusFrequencyCollector(input, db)
}
