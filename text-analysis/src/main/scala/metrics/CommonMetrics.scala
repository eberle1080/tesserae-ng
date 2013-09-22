package org.apache.solr.handler.tesserae.metrics

import com.codahale.metrics.{MetricFilter, JmxReporter, MetricRegistry}
import com.codahale.metrics.graphite.{GraphiteReporter, Graphite}
import MetricRegistry.name
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

object CommonMetrics {
  lazy val metrics: MetricRegistry = {
    val registry = new MetricRegistry
    val graphite = new Graphite(new InetSocketAddress("localhost", 2003))
    val graphiteReporter = GraphiteReporter.forRegistry(registry).
            prefixedWith("tesserae.solr.compare").
            convertRatesTo(TimeUnit.SECONDS).
            convertDurationsTo(TimeUnit.MILLISECONDS).
            filter(MetricFilter.ALL).
            build(graphite)

    graphiteReporter.start(15, TimeUnit.SECONDS)

    val jmxReporter = JmxReporter.forRegistry(registry).build()
    jmxReporter.start()

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
  lazy val csvParseTime = metrics.timer(name(getClass, "csv-parse-time"))
}
