# Configuration options
CATALINA_OPTS="${CATALINA_OPTS} -Duser.language=en"
CATALINA_OPTS="${CATALINA_OPTS} -Duser.country=US"
CATALINA_OPTS="${CATALINA_OPTS} -Dsolr.solr.home=/home/tesserae/solr"

# Enable JMX
CATALINA_OPTS="${CATALINA_OPTS} -Dcom.sun.management.jmxremote"
CATALINA_OPTS="${CATALINA_OPTS} -Dcom.sun.management.jmxremote.ssl=false"
CATALINA_OPTS="${CATALINA_OPTS} -Dcom.sun.management.jmxremote.authenticate=false"
CATALINA_OPTS="${CATALINA_OPTS} -Dcom.sun.management.jmxremote.port=9099"

# Enable remote debugging
#CATALINA_OPTS="${CATALINA_OPTS} -Xdebug"
#CATALINA_OPTS="${CATALINA_OPTS} -Xrunjdwp:transport=dt_socket,address=9000,server=y,suspend=n"

# JVM optimizations
CATALINA_OPTS="${CATALINA_OPTS} -server"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+CMSClassUnloadingEnabled"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+AggressiveOpts"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+UseFastAccessorMethods"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+UseStringCache"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+OptimizeStringConcat"
CATALINA_OPTS="${CATALINA_OPTS} -XX:+UseG1GC"

export CATALINA_OPTS
