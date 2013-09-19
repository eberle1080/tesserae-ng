package org.apache.solr.handler.tesserae.metrics

import com.codahale.metrics.{JmxReporter, MetricRegistry}
import MetricRegistry.name

object CommonMetrics {
  lazy val metrics: MetricRegistry = {
    val registry = new MetricRegistry
    val reporter = JmxReporter.forRegistry(registry).build()
    reporter.start()
    registry
  }

  lazy val latinStemOps = metrics.meter(name(getClass, "latin-stem-operations"))
  lazy val latinTokens = metrics.counter(name(getClass, "latin-tokens"))
  lazy val latinNouns = metrics.counter(name(getClass, "latin-nouns"))
  lazy val latinVerbs = metrics.counter(name(getClass, "latin-verbs"))
  lazy val latinKeywords = metrics.counter(name(getClass, "latin-keywords"))
  lazy val latinUnknown = metrics.counter(name(getClass, "latin-unknown"))
  lazy val compareOps = metrics.meter(name(getClass, "compares"))
  lazy val compareTime = metrics.timer(name(getClass, "compare-time"))
  lazy val uncachedCompareTime = metrics.timer(name(getClass, "uncached-compare-time"))
  lazy val resultsFormattingTime = metrics.timer(name(getClass, "result-formatting-time"))
  lazy val compareExceptions = metrics.meter(name(getClass, "compare-exceptions"))
}
