#!/usr/bin/env bash

set -e
WSGI=/usr/local/bin/uwsgi
WEBROOT=/opt/graphite/webapp
NUM_WORKERS=4

exec $WSGI \
  --chdir=$WEBROOT \
  --socket=/tmp/graphite.sock \
  --chmod-socket=666 \
  --need-app \
  --env DJANGO_SETTINGS_MODULE=graphite.settings \
  --disable-logging \
  --master \
  --pidfile=$WEBROOT/uwsgi.pid \
  --processes=$NUM_WORKERS \
  --harakiri=300 \
  --max-requests=5000 \
  --module="django.core.handlers.wsgi:WSGIHandler()" \
  --vacuum \
  --listen=128 \
  --need-app
