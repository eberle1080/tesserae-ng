package org.apache.lucene.analysis.la

object LatinNumberConverter {
  lazy val latinArabicMap = Map(
    'm' -> 1000,
    'd' -> 500,
    'c' -> 100,
    'l' -> 50,
    'x' -> 10,
    'v' -> 5,
    'i' -> 1
  )

  val HELP_CHAR = 0
  val MAIN_CHAR = 1
  val SUBTRACTION = 2
}

class LatinNumberConverter(private var strict: Boolean) {

  def format(termBuffer: Array[Char], termLength: Int) =
    latinToArabic(termBuffer, termLength)

  private def latinToArabic(termBuffer: Array[Char], termLength: Int): String = {
    if (validate(termBuffer, termLength)) {
      val latin = String.valueOf(termBuffer, 0, termLength)
      val arabic = convertLatinToArabic(latin, strict)
       if(latin.equals(arabic)) {
         null
       } else {
         arabic
       }
    } else {
      null
    }
  }

  private def convertLatinToArabic(latin: Char): Int =
    LatinNumberConverter.latinArabicMap.getOrElse(Character.toLowerCase(latin), 0)

  private def isMainChar(latin: Char): Boolean = {
    val l = Character.toLowerCase(latin)
    l == 'm' || l == 'c' || l == 'x' || l == 'i'
  }

  private def convertLatinToArabic(latin: String, strict: Boolean): String = {
    var maxValue = convertLatinToArabic('M') + 1
    var oldValue = convertLatinToArabic('M')
    var currentArabicValue = 0
    var charCounter = 0
    var charType = LatinNumberConverter.HELP_CHAR
    var arabicValue = 0

    val len = latin.length
    var i = 0

    while (i < len) {
      try {
        if ((i + 1) < len && convertLatinToArabic(latin.charAt(i)) < convertLatinToArabic(latin.charAt(i + 1))) {
          if (isMainChar(latin.charAt(i)) == false) {
            return latin
          }
          currentArabicValue = convertLatinToArabic(latin.charAt(i + 1)) - convertLatinToArabic(latin.charAt(i))
          charType = LatinNumberConverter.SUBTRACTION
        } else {
          currentArabicValue = convertLatinToArabic(latin.charAt(i))
          charType = if (isMainChar(latin.charAt(i)) == true) {
            LatinNumberConverter.MAIN_CHAR
          } else {
            LatinNumberConverter.HELP_CHAR
          }
        }

        if (oldValue < currentArabicValue) {
          return latin;
        }

        if (strict) {
          if (charType != LatinNumberConverter.SUBTRACTION && maxValue < convertLatinToArabic(latin.charAt(i))) {
            return latin
          }
          if (charType == LatinNumberConverter.SUBTRACTION && maxValue < convertLatinToArabic(latin.charAt(i + 1))) {
            return latin
          }
          if (charType == LatinNumberConverter.SUBTRACTION && (convertLatinToArabic(latin.charAt(i + 1)) / convertLatinToArabic(latin.charAt(i))) > 10) {
            return latin
          }
        }

        if (i > 0 && charType != LatinNumberConverter.SUBTRACTION && oldValue == currentArabicValue) {
          charCounter += 1
          if (charType == LatinNumberConverter.MAIN_CHAR && charCounter == 3) {
            return latin
          }
          if (charType == LatinNumberConverter.HELP_CHAR && charCounter == 1) {
            return latin
          }
        } else {
          charCounter = 0
        }

        arabicValue += currentArabicValue
        oldValue = currentArabicValue
        if (charType == LatinNumberConverter.SUBTRACTION) {
          maxValue = convertLatinToArabic(latin.charAt(i))
        }
        if (charType == LatinNumberConverter.SUBTRACTION) {
          i += 1
        }
      } finally {
        i += 1
      }
    }

    String.valueOf(arabicValue)
  }

  private def validate(termBuffer: Array[Char], termLength: Int): Boolean = {
    for (i <- 0 until termLength) {
      val toValidate = Character.toLowerCase(termBuffer(i))
      if (toValidate != 'i' && toValidate != 'v' && toValidate != 'x' && toValidate != 'l'
          && toValidate != 'c' && toValidate != 'd' && toValidate != 'm')
        return false
    }

    true
  }
}
