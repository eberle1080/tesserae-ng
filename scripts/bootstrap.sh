#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

echo '*******************************************************************************'
echo '* Begin tesserae-ng bootstrap                                                 *'
echo '*******************************************************************************'
echo ''

[ -d /vagrant ] || die "Missing directory: /vagrant"
cd /vagrant || die "Can't cd to /vagrant"

[ -d $CATALINA_HOME ] || die "Missing directory: $CATALINA_HOME"
sudo chown -R tesserae:tesserae "$CATALINA_HOME"
sudo rm -f "$CATALINA_HOME/logs/*"

BIN_DIR="$CATALINA_HOME/bin"
LIB_DIR="$CATALINA_HOME/lib"

[ -d "$LIB_DIR" ] || die "Missing directory: $LIB_DIR"
[ -d "$BIN_DIR" ] || die "Missing directory: $BIN_DIR"

[ -d text-analysis ] || die "Missing directory: text-analysis"
cd text-analysis || die "Can't cd to text-analysis"

echo "Compiling custom solr extensions..."
rm -rf target || die "rm failed"
rm -rf lib_managed || die "rm failed"
sbt -batch -no-colors package || die "compilation failed"

[ -d lib_managed ] || die "Missing directory: lib_managed"
cd lib_managed

echo "Installing dependency jars..."
while read path; do
    echo " [INSTALL] `basename $path`"
    sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "$path" || die "install failed: $path"
done < <(find . -type f -name *.jar)

echo "Installing main library jar..."
cd ..
[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

[ -f "text-analysis_2.10-1.0.jar" ] || die "Missing file: text-analysis_2.10-1.0.jar"
echo " [INSTALL] text-analysis_2.10-1.0.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "text-analysis_2.10-1.0.jar" || die "install failed: text-analysis_2.10-1.0.jar"

echo "Setting up solr home..."
cd /vagrant
[ -f setenv.sh ] || die "Missing file: setenv.sh"
sudo install -o tesserae -g tesserae -m 644 -t "$BIN_DIR" setenv.sh || die "install failed: setenv.sh"

[ -d solr ] || die "Missing directory: solr"
sudo rm -rf /home/tesserae/solr || die "rm failed"
sudo cp -a solr /home/tesserae/ || die "cp failed"
sudo chown -R tesserae:tesserae /home/tesserae/solr || die "chown failed"
sudo find /home/tesserae/solr -type d -name data -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"

echo "Starting tomcat in the background..."
[ -f scripts/start_tomcat.sh ] || die "Missing file: scripts/start_tomcat.sh"
sudo scripts/start_tomcat.sh || die "start failed"

echo "Setting up django website..."
[ -d website ] || die "Missing directory: website"
sudo rm -rf /home/tesserae/website || die "rm failed"
sudo cp -a website /home/tesserae/ || die "cp failed"
sudo chown -R tesserae:tesserae /home/tesserae/website || die "chown failed"
sudo find /home/tesserae/website -type f -name '*.pyc' -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"

[ -f manage.py ] || die "Missing file: manage.py"
sudo rm -f /home/tesserae/manage.py || die "rm failed"
sudo install -o tesserae -g tesserae -m 755 -t "/home/tesserae" manage.py || die "install failed: manage.py"

echo "Waiting for tomcat to start..."
netstat -lant | grep -q :8005
RET=$?

until [ $RET -eq 0 ]; do
  sleep 1
  netstat -lant | grep -q :8005
  RET=$?
done

echo "Initializing django database..."
[ -f scripts/init_db.sh ] || die "Missing file: scripts/init_db.sh"
sudo scripts/init_db.sh || die "start failed"



echo '*******************************************************************************'
echo '* End tesserae-ng bootstrap                                                   *'
echo '*******************************************************************************'
