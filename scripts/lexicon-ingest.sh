#!/bin/bash
cd `dirname $0`
exec /opt/java/bin/java -client -Dlog4j.configuration=file://`pwd`/log4j.properties -jar lexicon-ingest.jar "$@"
