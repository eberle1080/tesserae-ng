package org.apache.solr.analysis.lexicon

import java.util.Map

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.util.TokenFilterFactory
import java.io.File

class LatinLexiconStemFilterFactory(args: Map[String, String]) extends TokenFilterFactory(args) {
  if (!args.containsKey("database")) {
    throw new IllegalArgumentException("can't instantiate LatinLexiconStemFilterFactory without a 'database' argument")
  }

  private val multiStem = if (args.containsKey("multiStem")) {
    args.get("multiStem").toBoolean
  } else {
    false
  }

  private val db = new LatinLexiconDatabase(Option(args.get("cacheName")), new File(args.get("database")))

  def create(input: TokenStream): TokenStream =
    new LatinLexiconStemFilter(input, multiStem, db)
}
