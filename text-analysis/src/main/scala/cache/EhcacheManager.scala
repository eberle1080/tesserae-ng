package org.tesserae

import net.sf.ehcache.CacheManager
import org.slf4j.LoggerFactory

object EhcacheManager {
  private lazy val logger = LoggerFactory.getLogger(getClass)
  private lazy val ehcacheManager = CacheManager.getInstance
  private val DEFAULT_COMPARE_CACHE_NAME = "tesseraeCompare"
  private val DEFAULT_LATIN_LEXICON_CACHE_NAME = "latinLexicon"
  private val DEFAULT_LATIN_CORPUS_CACHE_NAME = "latinCorpus"

  private def getCache(cacheName: String) = {
    val cache = ehcacheManager.getEhcache(cacheName)
    if (cache == null) {
      logger.warn("Unable to find cache `" + cacheName + "', configuring a default one")
      ehcacheManager.addCacheIfAbsent(cacheName)
    } else {
      cache
    }
  }

  def compareCache(overrideName: Option[String] = None) =
    getCache(overrideName.getOrElse(DEFAULT_COMPARE_CACHE_NAME))

  def latinLexiconCache(overrideName: Option[String] = None) =
    getCache(overrideName.getOrElse(DEFAULT_LATIN_LEXICON_CACHE_NAME))

  def latinCorpusCache(overrideName: Option[String] = None) =
    getCache(overrideName.getOrElse(DEFAULT_LATIN_CORPUS_CACHE_NAME))
}
