#!/usr/bin/env bash

PROGNAME=$(basename $0)
function die {
    echo "${PROGNAME}: ${1:-"Unknown Error"}" 1>&2
    exit 1
}

if [ ! -d /vagrant/texts ]; then
    echo "${PROGNAME}: Skipping auto-ingest because /vagrant/texts is missing"
    exit 0
fi

[ -f /home/tesserae/.bash_local ] || die "Missing file: /home/tesserae/.bash_local"
source /home/tesserae/.bash_local

ME=`id -u`
if [ $ME -eq 0 ]; then
    sudo -u tesserae "$0"
else
    [ $ME -eq 1000 ] || die "Only tesserae or root can run this"
    cd /home/tesserae

    [ -f manage.py ] || die "Missing file: manage.py"
    echo "Running auto-ingest on /vagrant/texts (this may take a while)..."
    counter=0
    while read path; do
        python2.7 ./manage.py ingest --traceback --is-auto=true "$path" || die "auto-ingest failed while processing '$path'"
        let counter=counter+1
    done < <(find /vagrant/texts -mindepth 1 -type f -name '*.yaml' | sort)

    if [ "$counter" -eq 1 ]; then
        echo "Automatically ingested 1 file"
    else
        echo "Automatically ingested $counter files"
    fi
fi
