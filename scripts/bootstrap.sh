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

if [ -f /home/vagrant/.bootstrapped ]; then
    echo ' Bootstrap has already been run, will not continue'
    echo ''
    echo '*******************************************************************************'
    echo '* End tesserae-ng bootstrap                                                   *'
    echo '*******************************************************************************'
    exit 0
fi

[ -d /vagrant ] || die "Missing directory: /vagrant"
cd /vagrant || die "Can't cd to /vagrant"

echo "Unpacking archives (sorry, saves space on the image)..."

pushd /usr/share >/dev/null 2>&1
echo " * emacs.tar.xz"
[ -f emacs.tar.xz ] && sudo /bin/bash -c "xzcat emacs.tar.xz | tar -C /usr/share -xpf -" || die "extraction failed: emacs.tar.xz"
[ -f emacs.tar.xz ] && sudo rm -f emacs.tar.xz || die "rm failed: emacs.tar.xz"
echo "     => ok"
echo " * vim.tar.xz"
[ -f vim.tar.xz ] && sudo /bin/bash -c "xzcat vim.tar.xz | tar -C /usr/share -xpf -" || die "extraction failed: vim.tar.xz"
[ -f vim.tar.xz ] && sudo rm -f vim.tar.xz || die "rm failed: vim.tar.xz"
echo "     => ok"
echo " * man.tar.xz"
[ -f man.tar.xz ] && sudo /bin/bash -c "xzcat man.tar.xz | tar -C /usr/share -xpf -" || die "extraction failed: man.tar.xz"
[ -f man.tar.xz ] && sudo rm -f man.tar.xz || die "rm failed: man.tar.xz"
echo "     => ok"
echo " * nltk_data.tar.xz"
[ -f nltk_data.tar.xz ] && sudo /bin/bash -c "xzcat nltk_data.tar.xz | tar -C /usr/share -xpf -" || die "extraction failed: nltk_data.tar.xz"
[ -f nltk_data.tar.xz ] && sudo rm -f nltk_data.tar.xz || die "rm failed: nltk_data.tar.xz"
echo "     => ok"
popd >/dev/null 2>&1
pushd /opt >/dev/null 2>&1
echo " * java.tar.xz"
[ -f java.tar.xz ] && sudo /bin/bash -c "xzcat java.tar.xz | tar -C /opt -xpf -" || die "extraction failed: java.tar.xz"
[ -f java.tar.xz ] && sudo rm -f java.tar.xz || die "rm failed: java.tar.xz"
echo "     => ok"
echo " * data.tar.xz"
[ -f data.tar.xz ] && sudo /bin/bash -c "xzcat data.tar.xz | tar -C /opt -xpf -" || die "extraction failed: data.tar.xz"
[ -f data.tar.xz ] && sudo rm -f data.tar.xz || die "rm failed: data.tar.xz"
echo "     => ok"
popd >/dev/null 2>&1
pushd /home/tesserae >/dev/null 2>&1
echo " * ivy2.tar.xz"
[ -f ivy2.tar.xz ] && sudo /bin/bash -c "xzcat ivy2.tar.xz | tar -C /home/tesserae -xpf -" || die "extraction failed: ivy2.tar.xz"
[ -f ivy2.tar.xz ] && sudo /bin/bash -c "xzcat ivy2.tar.xz | tar -C /home/vagrant -xpf -" || die "extraction failed: ivy2.tar.xz"
[ -f ivy2.tar.xz ] && sudo /bin/bash -c "xzcat ivy2.tar.xz | tar -C /root -xpf -" || die "extraction failed: ivy2.tar.xz"
[ -d .ivy2 ] && sudo chown -R tesserae:tesserae .ivy2 || die "chown failed: .ivy2"
[ -d /home/vagrant/.ivy2 ] && sudo chown -R vagrant:vagrant /home/vagrant/.ivy2
sudo /bin/bash -c "[ -d /root/.ivy2 ] && sudo chown -R root:root /root/.ivy2 || exit 1" || die "chown failed: .sbt"
[ -f ivy2.tar.xz ] && sudo rm -f ivy2.tar.xz || die "rm failed: ivy2.tar.xz"
echo "     => ok"
echo " * sbt.tar.xz"
[ -f sbt.tar.xz ] && sudo /bin/bash -c "xzcat sbt.tar.xz | tar -C /home/tesserae -xpf -" || die "extraction failed: sbt.tar.xz"
[ -f sbt.tar.xz ] && sudo /bin/bash -c "xzcat sbt.tar.xz | tar -C /home/vagrant -xpf -" || die "extraction failed: sbt.tar.xz"
[ -f sbt.tar.xz ] && sudo /bin/bash -c "xzcat sbt.tar.xz | tar -C /root -xpf -" || die "extraction failed: sbt.tar.xz"
[ -d .sbt ] && sudo chown -R tesserae:tesserae .sbt || die "chown failed: .sbt"
[ -d /home/vagrant/.sbt ] && sudo chown -R vagrant:vagrant /home/vagrant/.sbt || die "chown failed: .sbt"
sudo /bin/bash -c "[ -d /root/.sbt ] && sudo chown -R root:root /root/.sbt || exit 1" || die "chown failed: .sbt"
[ -f sbt.tar.xz ] && sudo rm -f sbt.tar.xz || die "rm failed: sbt.tar.xz"
echo "     => ok"
popd >/dev/null 2>&1

sudo sync
[ -f ~/.bash_local ] && . ~/.bash_local

[ -d $CATALINA_HOME ] || die "Missing directory: $CATALINA_HOME"
sudo chown -R tesserae:tesserae "$CATALINA_HOME"
sudo rm -f "$CATALINA_HOME/logs/*"

BIN_DIR="$CATALINA_HOME/bin"
MAIN_LIB_DIR="$CATALINA_HOME/lib"
LIB_DIR="$CATALINA_HOME/webapps/solr/WEB-INF/lib"

[ -d "$LIB_DIR" ] || die "Missing directory: $LIB_DIR"
[ -d "$MAIN_LIB_DIR" ] || die "Missing directory: $MAIN_LIB_DIR"
[ -d "$BIN_DIR" ] || die "Missing directory: $BIN_DIR"

[ -d patches ] || die "Missing directory: patches"
cd patches || die "Can't cd to patches"

echo "Patching a few files..."

PYSOLR_DEST="/usr/local/lib/python2.7/dist-packages"
sudo rm -f "$PYSOLR_DEST/pysolr.py" || die "Unable to remove buggy pysolr.py"
sudo install -o root -g staff -m 644 -t "$PYSOLR_DEST" pysolr.py || die "install failed: pysolr.py"

echo "Configuring nginx..."
cd /vagrant
[ -f conf/nginx-default ] || die "Missing file: conf/nginx-default"
sudo cp -f conf/nginx-default /etc/nginx/sites-available/default || die "cp failed: conf/nginx-default"
sudo chmod 644 /etc/nginx/sites-available/default
sudo chown root:root /etc/nginx/sites-available/default
sudo /etc/init.d/nginx reload

echo "Setting up Graphite..."
[ -f conf/carbon.conf ] || die "Missing file: conf/carbon.conf"
sudo install -o root -g root -m 644 -t "/opt/graphite/conf" conf/carbon.conf || die "install failed: conf/carbon.conf"
[ -f conf/storage-schemas.conf ] || die "Missing file: conf/storage-schemas.conf"
sudo install -o root -g root -m 644 -t "/opt/graphite/conf" conf/storage-schemas.conf || die "install failed: conf/storage-schemas.conf"
sudo chown -R tesserae:tesserae /opt/graphite

echo "Installing Carbon Cache startup scripts..."
sudo mkdir -p /var/log/supervisor/carbon-cache
[ -d supervisor ] || die "Missing directory: supervisor"
[ -f supervisor/carbon-cache.conf ] || die "Missing file: supervisor/carbon-cache.conf"
sudo cp supervisor/carbon-cache.conf /etc/supervisor/conf.d/carbon-cache.conf || die "cp failed"

echo "Starting Carbon Cache in the background..."
sudo supervisorctl update || die "start failed"

cd /vagrant
[ -d lexicon-ingest ] || die "Missing directory: lexicon-ingest"
cd lexicon-ingest || die "Can't cd to lexicon-ingest"

echo "Compiling Lexicon Ingest utility..."
rm -rf target || die "rm failed"
rm -rf lib_managed || die "rm failed"
sbt -batch -no-colors one-jar publishLocal

[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

echo "Installing Lexicon Ingest utility..."
[ -f lexicon-ingest_2.10-1.0-one-jar.jar ] || die "Missing file: lexicon-ingest_2.10-1.0-one-jar.jar"
sudo cp -f lexicon-ingest_2.10-1.0-one-jar.jar /opt/data/lexicon/lexicon-ingest.jar || die "cp failed: lexicon-ingest_2.10-1.0-one-jar.jar"
sudo chown tesserae:tesserae /opt/data/lexicon/lexicon-ingest.jar || die "chown failed: /opt/data/lexicon/lexicon-ingest.jar"
sudo chmod 0644 /opt/data/lexicon/lexicon-ingest.jar || due "chmod failed: /opt/data/lexicon/lexicon-ingest.jar"

cd /vagrant
[ -d text-analysis ] || die "Missing directory: text-analysis"
cd text-analysis || die "Can't cd to text-analysis"

echo "Compiling custom Solr extensions..."
rm -rf target || die "rm failed"
rm -rf lib_managed || die "rm failed"
sbt -batch -no-colors package || die "compilation failed"

[ -d lib_managed ] || die "Missing directory: lib_managed"
cd lib_managed

echo "Installing dependency jars..."
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" /home/tesserae/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.10.2.jar || die "install failed: scala-library-2.10.2.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" jars/com.typesafe.akka/akka-actor_2.10/akka-actor_2.10-2.2.1.jar || die "install failed: akka-actor_2.10-2.2.1.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.typesafe/config/config-1.0.2.jar || die "install failed: config-1.0.0.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" jars/net.sf.ehcache/ehcache-core/ehcache-core-2.6.6.jar || die "install failed: ehcache-core-2.6.6.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.codahale.metrics/metrics-core/metrics-core-3.0.1.jar || die "install failed: metrics-core-3.0.1"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.codahale.metrics/metrics-healthchecks/metrics-healthchecks-3.0.1.jar || die "install failed: metrics-healthchecks-3.0.1.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.codahale.metrics/metrics-ehcache/metrics-ehcache-3.0.1.jar || die "install failed: metrics-ehcache-3.0.1.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.codahale.metrics/metrics-graphite/metrics-graphite-3.0.1.jar || die "install failed: metrics-graphite-3.0.1.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.codahale.metrics/metrics-jvm/metrics-jvm-3.0.1.jar || die "install failed: metrics-jvm-3.0.1.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/org.fusesource.leveldbjni/leveldbjni-all/leveldbjni-all-1.7.jar || die "install failed: leveldbjni-all-1.7.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" jars/net.sf.opencsv/opencsv/opencsv-2.3.jar || die "install failed: opencsv-2.3.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" jars/joda-time/joda-time/joda-time-2.2.jar || die "install failed: joda-time-2.2.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" jars/org.tesserae/lexicon-ingest_2.10/lexicon-ingest_2.10-1.0.jar || die "install failed: lexicon-ingest_2.10-1.0.jar"

echo "Installing main Solr extension jar..."
cd ..
[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

[ -f "text-analysis_2.10-1.0.jar" ] || die "Missing file: text-analysis_2.10-1.0.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "text-analysis_2.10-1.0.jar" || die "install failed: text-analysis_2.10-1.0.jar"

echo "Setting up Solr home..."
cd /vagrant
[ -f conf/setenv.sh ] || die "Missing file: conf/setenv.sh"
sudo install -o tesserae -g tesserae -m 644 -t "$BIN_DIR" conf/setenv.sh || die "install failed: conf/setenv.sh"

[ -f conf/ehcache.xml ] || die "Missing file: conf/ehcache.xml"
sudo install -o tesserae -g tesserae -m 644 -t "$MAIN_LIB_DIR" conf/ehcache.xml || die "install failed: conf/ehcache.xml"

[ -f conf/log4j.properties ] || die "Missing file: conf/log4j.properties"
sudo install -o tesserae -g tesserae -m 644 -t "$MAIN_LIB_DIR" conf/log4j.properties || die "install failed: conf/log4j.properties"

[ -d solr ] || die "Missing directory: solr"
sudo rm -rf /home/tesserae/solr || die "rm failed"
sudo cp -a solr /home/tesserae/ || die "cp failed"
sudo chown -R tesserae:tesserae /home/tesserae/solr || die "chown failed"
sudo find /home/tesserae/solr -type d -name data -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"
sudo find /home/tesserae/solr -type f -name '*~' -print0 | sudo xargs -0 -n 1 rm -f || die "rm failed"

echo "Ingesting Latin lexicon..."
cd /opt/data/lexicon || die "cd failed: /opt/data/lexicon"
[ -f lexicon-ingest.jar ] || die "Missing file: lexicon-ingest.jar"
[ -f la.lexicon.csv ] || die "Missing file: la.lexicon.csv"
sudo /opt/java/bin/java -jar lexicon-ingest.jar --input=la.lexicon.csv --output=la.lexicon.db || die "ingest failed"
sudo chown -R tesserae:tesserae la.lexicon.db

echo "Installing Tomcat startup scripts..."
cd /vagrant
sudo mkdir -p /var/log/supervisor/tomcat
[ -d supervisor ] || die "Missing directory: supervisor"
[ -f supervisor/tomcat.conf ] || die "Missing file: supervisor/tomcat.conf"
sudo cp supervisor/tomcat.conf /etc/supervisor/conf.d/tomcat.conf || die "cp failed"

echo "Starting Tomcat in the background..."
sudo supervisorctl update || die "start failed"

echo "Setting up Django web root..."
[ -d website ] || die "Missing directory: website"
sudo rm -rf /home/tesserae/website || die "rm failed"
sudo cp -a website /home/tesserae/ || die "cp failed"
sudo chown -R tesserae:tesserae /home/tesserae/website || die "chown failed"
sudo find /home/tesserae/website -type f -name '*.pyc' -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"
sudo find /home/tesserae/website -type f -name '*~' -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"

cd /vagrant
sudo mkdir -p /var/log/django
sudo chown tesserae:tesserae /var/log/django

[ -f manage.py ] || die "Missing file: manage.py"
sudo rm -f /home/tesserae/manage.py || die "rm failed"
sudo install -o tesserae -g tesserae -m 755 -t "/home/tesserae" manage.py || die "install failed: manage.py"

echo "Setting up RabbitMQ..."
sudo rabbitmqctl add_user tesserae-ng QAwSSvV8HeNNOXEfokrK
sudo rabbitmqctl add_vhost tesserae-ng
sudo rabbitmqctl set_permissions -p tesserae-ng tesserae-ng ".*" ".*" ".*"

echo "Installing Celery worker startup scripts..."
sudo mkdir -p /var/log/supervisor/celeryd
[ -f supervisor/celery-worker.conf ] || die "Missing file: supervisor/celery-worker.conf"
sudo cp supervisor/celery-worker.conf /etc/supervisor/conf.d/celery-worker.conf

echo "Starting Celery worker..."
sudo supervisorctl update || die "start failed"

echo "Waiting for Tomcat to start..."
netstat -lant | grep -q :8005
RET=$?

until [ $RET -eq 0 ]; do
  sleep 1
  netstat -lant | grep -q :8005
  RET=$?
done

echo "Initializing Django database and index..."
[ -f scripts/init_db.sh ] || die "Missing file: scripts/init_db.sh"
sudo scripts/init_db.sh || die "init_db.sh failed"

echo "Installing Tesserae uWSGI startup scripts..."
[ -f supervisor/tesserae-ng.conf ] || die "Missing file: supervisor/tesserae-ng.conf"
[ -f supervisor/tesserae-ng.sh ] || die "Missing file: supervisor/tesserae-ng.sh"
sudo mkdir -p /var/log/supervisor/tesserae-ng
sudo chown tesserae:tesserae /var/log/supervisor/tesserae-ng
sudo cp supervisor/tesserae-ng.conf /etc/supervisor/conf.d/tesserae-ng.conf || die "cp failed"
sudo install -o root -g root -m 755 -t "/usr/local/sbin" supervisor/tesserae-ng.sh

echo "Starting Tesserae uWSGI web server..."
sudo supervisorctl update

echo "Poking a few holes in the firewall..."
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 8080 -m conntrack --ctstate NEW -j ACCEPT
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 9000 -m conntrack --ctstate NEW -j ACCEPT
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 9099 -m conntrack --ctstate NEW -j ACCEPT
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 15672 -m conntrack --ctstate NEW -j ACCEPT

echo "Saving firewall state..."
sudo iptables-save > /etc/firewall.conf

echo "Installing refresh script links..."
[ -f scripts/refresh.sh ] || die "Missing file: scripts/refresh.sh"
sudo ln -s /vagrant/scripts/refresh.sh /home/tesserae/refresh.sh

if [ -e /vagrant/texts/.skip-auto-ingest ]; then
    echo "Skipping auto-ingest because the file '.skip-auto-ingest' exists"
else
    echo "The system is ready, ingesting any documents marked for auto-ingest..."
    [ -f scripts/auto_ingest.sh ] || die "Missing file: scripts/auto_ingest.sh"
    sudo scripts/auto_ingest.sh || die "auto_ingest.sh failed"

    echo ''
    echo "Any documents that were automatically ingested are now being indexed by Solr."
    echo "The high CPU and I/O load is to be expected until it's finished. If a lot of"
    echo "documents were ingested, this may take a while to complete. If you'd like to"
    echo "skip the auto-ingest feature in the future, create an empty file in the"
    echo "'texts' directory named '.skip-auto-ingest'."
    echo ''
    echo "If you'd like to keep an eye on the index queue, you can log in to the box"
    echo "and run the following command to see the count of the remaining documents:"
    echo ''
    echo "  \$> sudo rabbitmqctl list_queues -p tesserae-ng | grep ^celery | cut -f2"
fi

touch /home/vagrant/.bootstrapped || die "can't touch this"
sudo sync
echo ''
echo "All done."

echo ''
echo '*******************************************************************************'
echo '* End tesserae-ng bootstrap                                                   *'
echo '*******************************************************************************'
