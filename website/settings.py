# Django settings for website project.

import sys
import os
import djcelery

from os.path import abspath, dirname, join, exists
djcelery.setup_loader()

DEBUG = False
TEMPLATE_DEBUG = DEBUG

ADMINS = (
    ('Chris Eberle', 'chris.eberle@chriseberle.net'),
)

MANAGERS = ADMINS

DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql_psycopg2',
        'NAME': 'tesserae_ng',
        'USER': 'tesserae_ng',
        'PASSWORD': '7f5Rz3oHSxYOZIA',
        'HOST': 'localhost',
        'PORT': '5432',
    }
}

HAYSTACK_CONNECTIONS = {
    'default': {
        'ENGINE': 'haystack.backends.solr_backend.SolrEngine',
        'URL': 'http://127.0.0.1:8080/solr/latin'
    },
}

# Change to 'sentence' for sentence-based parsing
TESS_PARSE_MODE = 'line'

# For celery
BROKER_URL = 'amqp://tesserae-ng:QAwSSvV8HeNNOXEfokrK@localhost:5672/tesserae-ng'

# Hosts/domain names that are valid for this site; required if DEBUG is False
# See https://docs.djangoproject.com/en/{{ docs_version }}/ref/settings/#allowed-hosts
ALLOWED_HOSTS = '*'

# Local time zone for this installation. Choices can be found here:
# http://en.wikipedia.org/wiki/List_of_tz_zones_by_name
# although not all choices may be available on all operating systems.
# On Unix systems, a value of None will cause Django to use the same
# timezone as the operating system.
# If running in a Windows environment this must be set to the same as your
# system time zone.
TIME_ZONE = 'America/Denver'

# Language code for this installation. All choices can be found here:
# http://www.i18nguy.com/unicode/language-identifiers.html
LANGUAGE_CODE = 'en-us'

SITE_ID = 1

# If you set this to False, Django will make some optimizations so as not
# to load the internationalization machinery.
USE_I18N = True

# If you set this to False, Django will not format dates, numbers and
# calendars according to the current locale
USE_L10N = True

# Absolute path to the directory that holds media.
# Example: "/home/media/media.lawrence.com/"
MEDIA_ROOT = '/home/tesserae/website/media/'

# URL that handles the media served from MEDIA_ROOT. Make sure to use a
# trailing slash if there is a path component (optional in other cases).
# Examples: "http://media.lawrence.com", "http://example.com/media/"
MEDIA_URL = '/media/'

# Absolute path to the directory static files should be collected to.
# Don't put anything in this directory yourself; store your static files
# in apps' "static/" subdirectories and in STATICFILES_DIRS.
# Example: "/var/www/example.com/static/"
STATIC_ROOT = ''

# URL prefix for static files.
# Example: "http://example.com/static/", "http://static.example.com/"
STATIC_URL = '/static/'

# Additional locations of static files
STATICFILES_DIRS = (
    # Put strings here, like "/home/html/static" or "C:/www/django/static".
    # Always use forward slashes, even on Windows.
    # Don't forget to use absolute paths, not relative paths.
)

# List of finder classes that know how to find static files in
# various locations.
STATICFILES_FINDERS = (
    'django.contrib.staticfiles.finders.FileSystemFinder',
    'django.contrib.staticfiles.finders.AppDirectoriesFinder',
)

# Make this unique, and don't share it with anybody.
SECRET_KEY = '_d(ec5q6(5qkqh+)xro*ea$r^u-=h6b^lj!i5pby_r=7r=61#2'

# List of callables that know how to import templates from various sources.
TEMPLATE_LOADERS = (
    'django.template.loaders.filesystem.Loader',
    'django.template.loaders.app_directories.Loader',
)

CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.memcached.MemcachedCache',
        'LOCATION': '127.0.0.1:11211',
    }
}

MIDDLEWARE_CLASSES = (
    'django.middleware.gzip.GZipMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'reversion.middleware.RevisionMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    'debug_toolbar.middleware.DebugToolbarMiddleware',
)

HAYSTACK_SIGNAL_PROCESSOR = 'website.signals.TesseraeNGSignalProcessor'

CELERY_HAYSTACK_DEFAULT_TASK = 'website.tasks.TesseraeNGSignalHandler'

SIGNAL_PROCESSOR_BATCH_SIZE = 100

ROOT_URLCONF = 'website.urls'

# Python dotted path to the WSGI application used by Django's runserver.
WSGI_APPLICATION = 'website.wsgi.application'

# Any requests coming from these IPs will be shown the debug toolbar
INTERNAL_IPS = ('127.0.0.1', '192.168.211.2',)

#def custom_show_toolbar(request):
#    return True # Always show toolbar, for example purposes only.

DEBUG_TOOLBAR_CONFIG = {
    'INTERCEPT_REDIRECTS': False,
    #'SHOW_TOOLBAR_CALLBACK': custom_show_toolbar,
    #'EXTRA_SIGNALS': ['website.signals.TesseraeNGSignalProcessor'],
    'HIDE_DJANGO_SQL': False,
    #'TAG': 'div',
}

DEBUG_TOOLBAR_PANELS = (
    'debug_toolbar.panels.version.VersionDebugPanel',
    'debug_toolbar.panels.timer.TimerDebugPanel',
    'debug_toolbar.panels.settings_vars.SettingsVarsDebugPanel',
    'debug_toolbar.panels.headers.HeaderDebugPanel',
    'debug_toolbar.panels.request_vars.RequestVarsDebugPanel',
    'debug_toolbar.panels.template.TemplateDebugPanel',
    'debug_toolbar.panels.sql.SQLDebugPanel',
    'debug_toolbar.panels.signals.SignalDebugPanel',
    'debug_toolbar.panels.logger.LoggingPanel',
)

TEMPLATE_DIRS = (
    # Put strings here, like "/home/html/django_templates" or "C:/www/django/templates".
    # Always use forward slashes, even on Windows.
    # Don't forget to use absolute paths, not relative paths.
    '/home/tesserae/website/templates/default',
)

INSTALLED_APPS = (
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.sites',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.admin',
    'reversion',
    'haystack',
    'celery_haystack',
    'website.tesserae_ng',
    'website.graphite.metrics',
    'website.graphite.render',
    'website.graphite.cli',
    'website.graphite.browser',
    'website.graphite.composer',
    'website.graphite.account',
    'website.graphite.dashboard',
    'website.graphite.whitelist',
    'website.graphite.events',
    'tagging',
    'djcelery',
    'south',
    'debug_toolbar',
)

# A sample logging configuration. The only tangible logging
# performed by this configuration is to send an email to
# the site admins on every HTTP 500 error when DEBUG=False.
# See http://docs.djangoproject.com/en/dev/topics/logging for
# more details on how to customize your logging configuration.
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'filters': {
        'require_debug_false': {
            '()': 'django.utils.log.RequireDebugFalse'
        }
    },
    'formatters': {
        'standard': {
            'format' : "[%(asctime)s] %(levelname)s [%(name)s:%(lineno)s] %(message)s",
            'datefmt' : "%d/%b/%Y %H:%M:%S"
        },
    },
    'handlers': {
        'null': {
            'level': 'DEBUG',
            'class': 'django.utils.log.NullHandler',
        },
        'mail_admins': {
            'level': 'ERROR',
            'filters': ['require_debug_false'],
            'class': 'django.utils.log.AdminEmailHandler'
        },
        'logfile': {
            'level': 'DEBUG',
            'class': 'logging.handlers.RotatingFileHandler',
            'filename': '/var/log/django/logfile',
            'maxBytes': 50000,
            'backupCount': 2,
            'formatter': 'standard',
        },
        'console': {
            'level': 'INFO',
            'class': 'logging.StreamHandler',
            'formatter': 'standard'
        },
    },
    'loggers': {
        'django': {
            'handlers': ['console', 'logfile'],
            'propagate': True,
            'level': 'INFO',
        },
        'django.request': {
            'handlers': ['mail_admins'],
            'level': 'ERROR',
            'propagate': True,
        },
        'website': {
            'handlers': ['console', 'logfile'],
            'level': 'DEBUG',
        },
    }
}

# Graphite settings

try:
    import rrdtool
except ImportError:
    rrdtool = False

GRAPHITE_ROOT = '/opt/graphite'
CONF_DIR = '/opt/graphite/conf'
STORAGE_DIR = '/opt/graphite/storage'
CONTENT_DIR = '/opt/graphite/webapp/content'
CSS_DIR = join(CONTENT_DIR, 'css')
CONF_DIR = join(GRAPHITE_ROOT, 'conf')
DASHBOARD_CONF = join(CONF_DIR, 'dashboard.conf')
GRAPHTEMPLATES_CONF = join(CONF_DIR, 'graphTemplates.conf')
STORAGE_DIR = join(GRAPHITE_ROOT, 'storage')
WHITELIST_FILE = join(STORAGE_DIR, 'lists', 'whitelist')
INDEX_FILE = join(STORAGE_DIR, 'index')
LOG_DIR = join(STORAGE_DIR, 'log', 'webapp')
WHISPER_DIR = join(STORAGE_DIR, 'whisper/')
RRD_DIR = join(STORAGE_DIR, 'rrd/')

if rrdtool and exists(RRD_DIR):
    DATA_DIRS = [WHISPER_DIR, RRD_DIR]
else:
    DATA_DIRS = [WHISPER_DIR]

CLUSTER_SERVERS = []
WEB_DIR = dirname( abspath(__file__) )
WEBAPP_DIR = dirname(WEB_DIR)
THIRDPARTY_DIR = join(WEB_DIR, 'thirdparty')
sys.path.append(THIRDPARTY_DIR)

LOGIN_URL = '/graphite/account/login'

MEMCACHE_HOSTS = []
DEFAULT_CACHE_DURATION = 60 #metric data and graphs are cached for one minute by default
LOG_CACHE_PERFORMANCE = False

REMOTE_STORE_FETCH_TIMEOUT = 6  
REMOTE_STORE_FIND_TIMEOUT = 2.5 
REMOTE_STORE_RETRY_DELAY = 60   
REMOTE_FIND_CACHE_DURATION = 300

REMOTE_RENDERING = False #if True, rendering is delegated to RENDERING_HOSTS
RENDERING_HOSTS = []
REMOTE_RENDER_CONNECT_TIMEOUT = 1.0
LOG_RENDERING_PERFORMANCE = False

CARBONLINK_HOSTS = ["127.0.0.1:7002"]
CARBONLINK_TIMEOUT = 1.0
SMTP_SERVER = "localhost"
DOCUMENTATION_URL = "http://graphite.readthedocs.org/"
ALLOW_ANONYMOUS_CLI = True
LOG_METRIC_ACCESS = False
LEGEND_MAX_ITEMS = 10
USE_LDAP_AUTH = False
JAVASCRIPT_DEBUG = False

USE_REMOTE_USER_AUTHENTICATION = False
FLUSHRRDCACHED = ''
