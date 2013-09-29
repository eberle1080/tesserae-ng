package org.apache.lucene.analysis.la

import org.apache.lucene.analysis.{TokenFilter, TokenStream}
import org.apache.lucene.analysis.tokenattributes.{KeywordAttribute, CharTermAttribute}
import java.util.{Map => JavaMap}
import org.apache.lucene.analysis.util.TokenFilterFactory

class ReplaceRegexFilterFactory(args: JavaMap[String, String]) extends TokenFilterFactory(args) {
  if (!args.containsKey("regex")) {
    throw new IllegalArgumentException("can't instantiate ReplaceCharacterFilterFactory without a 'regex' argument")
  }
  if (!args.containsKey("replacement")) {
    throw new IllegalArgumentException("can't instantiate ReplaceCharacterFilterFactory without a 'replacement' argument")
  }

  private val regex = args.get("regex").r
  private val replacement = args.get("replacement")

  def create(input: TokenStream): TokenStream =
    new ReplaceRegexFilter(input, regex, replacement)
}
