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
    pushd "$CATALINA_HOME/logs" >/dev/null 2>&1
    rm -f *
    popd >/dev/null 2>&1
    sudo -u tesserae "$0"
else
    [ $ME -eq 1000 ] || die "Only tesserae or root can run this"
    BIN_DIR="$CATALINA_HOME/bin"
    [ -d "$BIN_DIR" ] || die "Missing directory: $BIN_DIR"

    cd $BIN_DIR
    ./startup.sh
fi
