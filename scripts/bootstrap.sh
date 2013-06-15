#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

[ -d /vagrant ] || die "Missing directory: /vagrant"
cd /vagrant || die "Can't cd to /vagrant"

LIB_DIR="$CATALINA_HOME/lib"
[ -d "$LIB_DIR" ] || die "Missing directory: $LIB_DIR"
[ -d text-analysis ] || die "Missing directory: text-analysis"
cd text-analysis || die "Can't cd to text-analysis"

echo "Compiling custom solr extensions..."
rm -rf target || die "rm failed"
sbt -batch -no-colors package || die "compilation failed"

[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

echo "Installing custom solr extensions..."
[ -f "text-analysis_2.10-1.0.jar" ] || die "Missing file: text-analysis_2.10-1.0.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "text-analysis_2.10-1.0.jar" || die "install failed"
