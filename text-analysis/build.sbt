name := "text-analysis"

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies += "org.scala-lang" % "scala-library" % "2.10.2"

libraryDependencies += "org.apache.solr" % "solr-core" % "4.3.1"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.10" % "2.1.4"

libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.5"

resolvers += "restlet" at "http://maven.restlet.org/"

retrieveManaged := true
