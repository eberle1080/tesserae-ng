package org.apache.lucene.analysis.la

object LatinStemmer {
  lazy val queSet = Set(
    "atque", "quoque", "neque", "itaque", "absque", "apsque", "abusque", "adaeque", "adusque", "denique",
    "deque", "susque", "oblique", "peraeque", "plenisque", "quandoque", "quisque", "quaeque",
    "cuiusque", "cuique", "quemque", "quamque", "quaque", "quique", "quorumque", "quarumque",
    "quibusque", "quosque", "quasque", "quotusquisque", "quousque", "ubique", "undique", "usque",
    "uterque", "utique", "utroque", "utribique", "torque", "coque", "concoque", "contorque",
    "detorque", "decoque", "excoque", "extorque", "obtorque", "optorque", "retorque", "recoque",
    "attorque", "incoque", "intorque", "praetorque"
  )
}

class LatinStemmer {
  def stemQUE(termBuffer: Array[Char], termLength: Int): Int = {
    val currentToken = String.valueOf(termBuffer, 0, termLength).toLowerCase
    if (LatinStemmer.queSet.contains(currentToken)) {
      return -1
    }

    if (currentToken.endsWith("que")) {
      return termLength - 3
    }

    termLength
  }

  def stemAsNoun(termBuffer: Array[Char], termLength: Int): String = {
    val noun = String.valueOf(termBuffer, 0, termLength).toLowerCase
    if ((noun.endsWith("ibus") || noun.endsWith("arum") || noun.endsWith("erum") || noun.endsWith("orum") || noun.endsWith("ebus")) && noun.length() >= 6) {
      String.valueOf(termBuffer, 0, termLength - 4)
    } else if ((noun.endsWith("ius") || noun.endsWith("uum") || noun.endsWith("ium")) && noun.length() >= 5) {
      String.valueOf(termBuffer, 0, termLength - 3)
    } else if ((noun.endsWith("ae") || noun.endsWith("am") || noun.endsWith("as") || noun.endsWith("em") || noun.endsWith("es")
             || noun.endsWith("ia") || noun.endsWith("is") || noun.endsWith("nt") || noun.endsWith("os") || noun.endsWith("ud")
             || noun.endsWith("um") || noun.endsWith("us") || noun.endsWith("ei") || noun.endsWith("ui") || noun.endsWith("im"))
             && noun.length() >= 4) {
      String.valueOf(termBuffer, 0, termLength - 2)
    } else if ((noun.endsWith("a") || noun.endsWith("e") || noun.endsWith("i") || noun.endsWith("o") || noun.endsWith("u")) && noun.length() >= 3) {
      String.valueOf(termBuffer, 0, termLength - 1)
    } else {
      String.valueOf(termBuffer, 0, termLength)
    }
  }

  def stemAsVerb(termBuffer: Array[Char], termLength: Int): String = {
    val verb = String.valueOf(termBuffer, 0, termLength).toLowerCase
    if (verb.endsWith("iuntur") || verb.endsWith("erunt") || verb.endsWith("untur") || verb.endsWith("iunt") || verb.endsWith("unt")) {
      verbSuffixToI(termBuffer, termLength)
    } else if (verb.endsWith("beris") || verb.endsWith("bor") || verb.endsWith("bo")) {
      verbSuffixToBI(termBuffer, termLength)
    } else if (verb.endsWith("ero") && termLength >= 5) {
      termBuffer(termLength - 1) = 'i'
      String.valueOf(termBuffer, 0, termLength)
    } else if ((verb.endsWith("mini") || verb.endsWith("ntur") || verb.endsWith("stis")) && termLength >= 6) {
      String.valueOf(termBuffer, 0, termLength  - 4)
    } else if ((verb.endsWith("mus") || verb.endsWith("mur") || verb.endsWith("ris") || verb.endsWith("sti") || verb.endsWith("tis") || verb.endsWith("tur")) && termLength >= 5) {
      String.valueOf(termBuffer, 0, termLength  - 3)
    } else if ((verb.endsWith("ns") || verb.endsWith("nt") || verb.endsWith("ri")) && termLength >= 4) {
      String.valueOf(termBuffer, 0, termLength  - 2)
    } else if ((verb.endsWith("m") || verb.endsWith("r") || verb.endsWith("s") || verb.endsWith("t")) && termLength >= 3) {
      String.valueOf(termBuffer, 0, termLength  - 1)
    } else {
      String.valueOf(termBuffer, 0, termLength)
    }
  }

  def verbSuffixToI(termBuffer: Array[Char], termLength: Int): String = {
    val verb = String.valueOf(termBuffer, 0, termLength).toLowerCase
    if (verb.endsWith("iuntur") && termLength >= 8) {
      String.valueOf(termBuffer, 0, termLength - 5)
    } else if ((verb.endsWith("erunt") || verb.endsWith("untur")) && termLength >= 7) {
      termBuffer(termLength - 5) = 'i'
      String.valueOf(termBuffer, 0, termLength - 4)
    } else if (verb.endsWith("iunt") && termLength >= 6) {
      String.valueOf(termBuffer, 0, termLength - 3)
    } else if (verb.endsWith("unt") && termLength >= 5) {
      termBuffer(termLength - 3) = 'i'
      String.valueOf(termBuffer, 0, termLength - 2)
    } else {
      String.valueOf(termBuffer, 0, termLength)
    }
  }

  def verbSuffixToBI(termBuffer: Array[Char], termLength: Int): String = {
    val verb = String.valueOf(termBuffer, 0, termLength).toLowerCase
    if (verb.endsWith("beris") && termLength >= 7) {
      termBuffer(termLength - 4) = 'i'
      String.valueOf(termBuffer, 0, termLength - 3)
    } else if (verb.endsWith("bor") && termLength >= 5) {
      termBuffer(termLength - 2) = 'i'
      String.valueOf(termBuffer, 0, termLength - 1)
    } else if (verb.endsWith("bo") && termLength >= 4) {
      termBuffer(termLength - 1) = 'i'
      String.valueOf(termBuffer, 0, termLength)
    } else {
      String.valueOf(termBuffer, 0, termLength)
    }
  }
}
