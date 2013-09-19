package org.apache.solr.handler.tesserae.pos

case class TaggedPartOfSpeech(pos: String, text: String) {
  override def toString = String.valueOf(pos) + ":" + String.valueOf(text)
}

object PartOfSpeech {
  val VERB = "v"
  val NOUN = "n"
  val KEYWORD = "k"
  val UNKNOWN = "u"

  def apply(pos: String, text: String): String =
    TaggedPartOfSpeech(pos, text).toString
  def apply(tps: TaggedPartOfSpeech): String =
    tps.toString

  def unapply(str: String): TaggedPartOfSpeech = str match {
    case null => TaggedPartOfSpeech(UNKNOWN, null)
    case s if s.length > 1 && s.charAt(1) == ':' =>
      TaggedPartOfSpeech(s.substring(0, 1), s.substring(2))
    case default => TaggedPartOfSpeech(UNKNOWN, default)
  }

  def isVerb(str: String): Boolean =
    unapply(str).pos == VERB
  def isVerb(tps: TaggedPartOfSpeech): Boolean =
    tps.pos == VERB
  def isNoun(str: String): Boolean =
    unapply(str).pos == NOUN
  def isNoun(tps: TaggedPartOfSpeech): Boolean =
    tps.pos == NOUN
  def isKeyword(str: String): Boolean =
    unapply(str).pos == KEYWORD
  def isKeyword(tps: TaggedPartOfSpeech): Boolean =
    tps.pos == KEYWORD
  def isUnknown(str: String): Boolean =
    isUnknown(unapply(str))
  def isUnknown(tps: TaggedPartOfSpeech): Boolean =
    tps.pos != VERB && tps.pos != NOUN && tps.pos != KEYWORD
}
