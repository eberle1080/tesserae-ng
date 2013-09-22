#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

ME=`id -u`
if [ $ME -ne 0 ]; then
    sudo "$0"
    exit $?
fi

[ -f /home/tesserae/.bash_local ] || die "Missing file: /home/tesserae/.bash_local"
source /home/tesserae/.bash_local

if [ ! -f /home/vagrant/.bootstrapped ]; then
    die "Bootstrap hasn't been run!"
fi

echo '*******************************************************************************'
echo '* Begin tesserae-ng refresh                                                   *'
echo '*******************************************************************************'
echo ''

[ -d /vagrant ] || die "Missing directory: /vagrant"
cd /vagrant || die "Can't cd to /vagrant"

[ -d $CATALINA_HOME ] || die "Missing directory: $CATALINA_HOME"

BIN_DIR="$CATALINA_HOME/bin"
MAIN_LIB_DIR="$CATALINA_HOME/lib"
LIB_DIR="$CATALINA_HOME/webapps/solr/WEB-INF/lib"

[ -d "$LIB_DIR" ] || die "Missing directory: $LIB_DIR"
[ -d "$MAIN_LIB_DIR" ] || die "Missing directory: $MAIN_LIB_DIR"
[ -d "$BIN_DIR" ] || die "Missing directory: $BIN_DIR"

echo "Updating nginx configuration..."
[ -f conf/nginx-default ] || die "Missing file: conf/nginx-default"
cp -f conf/nginx-default /etc/nginx/sites-available/default || die "cp failed: conf/nginx-default"
chmod 644 /etc/nginx/sites-available/default
chown root:root /etc/nginx/sites-available/default
/etc/init.d/nginx reload

echo "Stopping uWSGI web server..."
supervisorctl stop tesserae-ng || die "Unable to stop uWSGI server"

echo "Stopping Celery worker..."
supervisorctl stop celery-worker || die "Unable to stop Celery worker"

echo "Stopping Tomcat..."
supervisorctl stop tomcat || die "Unable to stop Tomcat"

[ -d patches ] || die "Missing directory: patches"
cd patches || die "Can't cd to patches"

echo "Patching a few files..."

PYSOLR_DEST="/usr/local/lib/python2.7/dist-packages"
rm -f "$PYSOLR_DEST/pysolr.py" || die "Unable to remove buggy pysolr.py"
install -o root -g staff -m 644 -t "$PYSOLR_DEST" pysolr.py || die "install failed: pysolr.py"

cd /vagrant
[ -d text-analysis ] || die "Missing directory: text-analysis"
cd text-analysis || die "Can't cd to text-analysis"

echo "Compiling custom Solr extensions..."
rm -rf target || die "rm failed"
rm -rf lib_managed || die "rm failed"
sbt -batch -no-colors package || die "compilation failed"

[ -d lib_managed ] || die "Missing directory: lib_managed"
cd lib_managed

echo "Refreshing main Solr extension jar..."
cd ..
[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

[ -f "text-analysis_2.10-1.0.jar" ] || die "Missing file: text-analysis_2.10-1.0.jar"
install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "text-analysis_2.10-1.0.jar" || die "install failed: text-analysis_2.10-1.0.jar"

echo "Refreshing Solr configuration (index data will be preserved)..."
cd /vagrant
[ -f conf/setenv.sh ] || die "Missing file: conf/setenv.sh"
install -o tesserae -g tesserae -m 644 -t "$BIN_DIR" conf/setenv.sh || die "install failed: conf/setenv.sh"

[ -f conf/ehcache.xml ] || die "Missing file: conf/ehcache.xml"
install -o tesserae -g tesserae -m 644 -t "$MAIN_LIB_DIR" conf/ehcache.xml || die "install failed: conf/ehcache.xml"

[ -f conf/log4j.properties ] || die "Missing file: conf/log4j.properties"
install -o tesserae -g tesserae -m 644 -t "$MAIN_LIB_DIR" conf/log4j.properties || die "install failed: conf/log4j.properties"

[ -d solr ] || die "Missing directory: solr"
cd solr || die "can't cd to solr"
install -o tesserae -g tesserae -m 644 -t "/home/tesserae/solr" solr.xml || die "install failed: solr.xml"
cd latin/conf || die "can't cd to latin/conf"
find . -maxdepth 1 -type f -exec install -o tesserae -g tesserae -m 644 -t "/home/tesserae/solr/latin/conf" {} \; || die "Install of solr conf files failed"

echo "Cleaning Tomcat logs..."
find "${CATALINA_HOME}/logs" -type f -exec rm -f {} \;
find "/var/log/supervisor/tomcat" -type f -exec rm -f {} \;

echo "Starting Tomcat in the background..."
supervisorctl start tomcat || die "start failed"

echo "Refreshing Django web root..."

if [ ! -d /home/tesserae/website ]; then
    mkdir /home/tesserae/website || die "mkdir failed: /home/tesserae/website"
    chown tesserae:tesserae /home/tesserae/website || die "chown failed"
    chmod 755 /home/tesserae/website || die "chmod failed"
fi

cd /vagrant
[ -d website ] || die "Missing directory: website"
cd website || die "Failed to cd to website"

while read filename; do
    filename=`echo "$filename" | sed 's/^\.\///'`
    basedir=`dirname $filename`

    if [ "x$basedir" = "x." ]; then
        destdir="/home/tesserae/website"
    else
        destdir="/home/tesserae/website/$basedir"
    fi

    [ -d "$destdir" ] || install -o tesserae -g tesserae -d -m 755 "$destdir"
    install -o tesserae -g tesserae -m 644 -t "$destdir" "$filename"
done < <(find . -type f)

cd /vagrant
[ -f manage.py ] || die "Missing file: manage.py"
rm -f /home/tesserae/manage.py || die "rm failed"
install -o tesserae -g tesserae -m 755 -t "/home/tesserae" manage.py || die "install failed: manage.py"

find /home/tesserae/website -type f -name '*.pyc' -print0 | xargs -0 -n 1 rm -rf || die "rm failed"
find /home/tesserae/website -type f -name '*~' -print0 | xargs -0 -n 1 rm -rf || die "rm failed"

echo "Cleaning Django logs..."
find "/var/log/django" -type f -exec rm -f {} \;
find "/var/log/supervisor/tesserae-ng" -type f -exec rm -f {} \;

echo "Cleaning Celery logs..."
find "/var/log/supervisor/celeryd" -type f -exec rm -f {} \;

echo "Starting Celery worker..."
supervisorctl start celery-worker || die "start failed"

echo "Waiting for Tomcat to start..."
netstat -lant | grep -q :8005
RET=$?

until [ $RET -eq 0 ]; do
  sleep 1
  netstat -lant | grep -q :8005
  RET=$?
done

echo "Refreshing Django database"
[ -f scripts/refresh_db.sh ] || die "script missing: scripts/refresh_db.sh"
scripts/refresh_db.sh

echo "Starting uWSGI web server..."
supervisorctl start tesserae-ng

echo "All done."

echo
echo ">>> NOTICE <<<"
echo "The Solr index was NOT rebuilt. This is intentional. If you want to rebuild the"
echo "Solr index, please run './manage.py rebuild_index'"
echo ">>> NOTICE <<<"
echo

echo '*******************************************************************************'
echo '* End tesserae-ng refresh                                                     *'
echo '*******************************************************************************'
