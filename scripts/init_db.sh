#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

[ -f /home/tesserae/.bash_local ] || die "Missing file: /home/tesserae/.bash_local"
source /home/tesserae/.bash_local

ME=`id -u`
if [ $ME -eq 0 ]; then
    sudo -u tesserae "$0"
else
    [ $ME -eq 1000 ] || die "Only tesserae or root can run this"
    cd /home/tesserae

    [ -f manage.py ] || die "Missing file: manage.py"
    [ -f /vagrant/scripts/make_superuser.py ] || die "Missing file: make_superuser.py"

    echo "*** Running syncdb..."
    python2.7 ./manage.py syncdb --noinput

    echo "*** Running migrate..."
    python2.7 ./manage.py migrate --noinput

    echo "*** Creating initial superuser..."
    python2.7 /vagrant/scripts/make_superuser.py

    echo "*** Running update_index..."
    python2.7 ./manage.py update_index
fi
