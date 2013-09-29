package org.apache.lucene.analysis.la

import java.util.{Map => JavaMap}
import org.apache.lucene.analysis.util.TokenFilterFactory
import org.apache.lucene.analysis.TokenStream

class ReplaceCharacterFilterFactory(args: JavaMap[String, String]) extends TokenFilterFactory(args) {
  if (!args.containsKey("searchCharacters")) {
    throw new IllegalArgumentException("can't instantiate ReplaceCharacterFilterFactory without a 'searchCharacters' argument")
  }
  if (!args.containsKey("replacementCharacters")) {
    throw new IllegalArgumentException("can't instantiate ReplaceCharacterFilterFactory without a 'replacementCharacters' argument")
  }

  private val map = {
    val searchChars = args.get("searchCharacters")
    val replaceChars = args.get("replacementCharacters")
    if (searchChars.length != replaceChars.length) {
      throw new IllegalArgumentException("can't instantiate ReplaceCharacterFilterFactory because the size " +
        "of searchCharacters and replacementCharacters don't match")
    }

    var replacementMap: Map[String, String] = Map.empty

    val len = searchChars.length
    for (i <- 0 until len) {
      val input = String.valueOf(searchChars(i))
      val replacedOrig = String.valueOf(replaceChars(i))

      val replaced = replacedOrig match {
        case " " => ""
        case any => any
      }

      replacementMap += input -> replaced
    }

    replacementMap
  }

  def create(input: TokenStream): TokenStream =
    new ReplaceCharacterFilter(input, map)
}
