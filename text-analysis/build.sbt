name := "text-analysis"

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies += "org.scala-lang" % "scala-library" % "2.10.2"

libraryDependencies += "org.apache.solr" % "solr-core" % "4.4.0"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.10" % "2.2.1"

libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.5"

libraryDependencies += "net.sf.ehcache" % "ehcache-core" % "2.6.6"

libraryDependencies += "javax.transaction" % "jta" % "1.1"

libraryDependencies += "com.codahale.metrics" % "metrics-core" % "3.0.1"

libraryDependencies += "com.codahale.metrics" % "metrics-graphite" % "3.0.1"

libraryDependencies += "com.codahale.metrics" % "metrics-healthchecks" % "3.0.1"

libraryDependencies += "com.codahale.metrics" % "metrics-jvm" % "3.0.1"

libraryDependencies += "com.codahale.metrics" % "metrics-ehcache" % "3.0.1"

libraryDependencies += "com.codahale.metrics" % "metrics-servlets" % "3.0.1"

libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7"

libraryDependencies += "net.sf.opencsv" % "opencsv" % "2.3"

resolvers += "restlet" at "http://maven.restlet.org/"

retrieveManaged := true

seq(lsSettings :_*)
