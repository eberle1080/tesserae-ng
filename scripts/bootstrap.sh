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

[ -d $CATALINA_HOME ] || die "Missing directory: $CATALINA_HOME"
sudo chown -R tesserae:tesserae "$CATALINA_HOME"
sudo rm -f "$CATALINA_HOME/logs/*"

BIN_DIR="$CATALINA_HOME/bin"
LIB_DIR="$CATALINA_HOME/webapps/solr/WEB-INF/lib"

[ -d "$LIB_DIR" ] || die "Missing directory: $LIB_DIR"
[ -d "$BIN_DIR" ] || die "Missing directory: $BIN_DIR"

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
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" bundles/com.typesafe.akka/akka-actor_2.10/akka-actor_2.10-2.1.4.jar || die "install failed: akka-actor_2.10-2.1.4.jar"

echo "Installing main Solr extension jar..."
cd ..
[ -d "target/scala-2.10" ] || die "Missing directory: target/scala-2.10"
cd target/scala-2.10 || die "Can't cd to target/scala-2.10"

[ -f "text-analysis_2.10-1.0.jar" ] || die "Missing file: text-analysis_2.10-1.0.jar"
sudo install -o tesserae -g tesserae -m 644 -t "$LIB_DIR" "text-analysis_2.10-1.0.jar" || die "install failed: text-analysis_2.10-1.0.jar"

echo "Setting up Solr home..."
cd /vagrant
[ -f scripts/setenv.sh ] || die "Missing file: scripts/setenv.sh"
sudo install -o tesserae -g tesserae -m 644 -t "$BIN_DIR" scripts/setenv.sh || die "install failed: scripts/setenv.sh"

[ -d solr ] || die "Missing directory: solr"
sudo rm -rf /home/tesserae/solr || die "rm failed"
sudo cp -a solr /home/tesserae/ || die "cp failed"
sudo chown -R tesserae:tesserae /home/tesserae/solr || die "chown failed"
sudo find /home/tesserae/solr -type d -name data -print0 | sudo xargs -0 -n 1 rm -rf || die "rm failed"
sudo find /home/tesserae/solr -type f -name '*~' -print0 | sudo xargs -0 -n 1 rm -f || die "rm failed"

echo "Installing Tomcat startup scripts..."
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

echo "Installing uWSGI startup scripts..."
[ -f supervisor/tesserae-ng.conf ] || die "Missing file: supervisor/tesserae-ng.conf"
[ -f supervisor/tesserae-ng.sh ] || die "Missing file: supervisor/tesserae-ng.sh"
sudo mkdir -p /var/log/supervisor/tesserae-ng
sudo chown tesserae:tesserae /var/log/supervisor/tesserae-ng
sudo cp supervisor/tesserae-ng.conf /etc/supervisor/conf.d/tesserae-ng.conf || die "cp failed"
sudo install -o root -g root -m 755 -t "/usr/local/sbin" supervisor/tesserae-ng.sh

echo "Starting uWSGI web server..."
sudo supervisorctl update

echo "Poking a few holes in the firewall..."
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 8080 -m conntrack --ctstate NEW -j ACCEPT
sudo iptables -A INPUT -i eth0 -p tcp -m tcp --dport 9000 -m conntrack --ctstate NEW -j ACCEPT

echo "Saving firewall state..."
sudo iptables-save > /etc/firewall.conf

touch /home/vagrant/.bootstrapped || die "can't touch this"
echo "All done."

echo '*******************************************************************************'
echo '* End tesserae-ng bootstrap                                                   *'
echo '*******************************************************************************'
