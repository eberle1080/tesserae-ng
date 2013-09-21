#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

[ -d /opt/graphite/webapp/graphite ] || die "Missing directory: /opt/graphite/webapp/graphite"

[ -f /home/tesserae/.bash_local ] || die "Missing file: /home/tesserae/.bash_local"
source /home/tesserae/.bash_local

ME=`id -u`
if [ $ME -eq 0 ]; then
    sudo -u tesserae "$0"
else
    [ $ME -eq 1000 ] || die "Only tesserae or root can run this"
    cd /opt/graphite/webapp/graphite

    [ -f manage.py ] || die "Missing file: manage.py"

    echo "*** Running syncdb..."
    python2.7 ./manage.py syncdb --noinput

    echo "*** Running migrate..."
    python2.7 ./manage.py migrate --noinput
fi
