name := "lexicon-lookup"

version := "1.0"

scalaVersion := "2.10.2"

organization := "org.tesserae"

libraryDependencies += "org.scala-lang" % "scala-library" % "2.10.2"

libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.5"

libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7"

libraryDependencies += "gnu.getopt" % "java-getopt" % "1.0.13"

libraryDependencies += "org.tesserae" %% "lexicon-ingest" % "1.0"

resolvers += "restlet" at "http://maven.restlet.org/"

seq(lsSettings :_*)

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

mainClass in oneJar := Some("org.tesserae.lexicon.lookup.Main")

mainClass in (Compile, run) := Some("org.tesserae.lexicon.lookup.Main")

mainClass in (Compile, packageBin) := Some("org.tesserae.lexicon.lookup.Main")

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

scalacOptions += "-optimise"
