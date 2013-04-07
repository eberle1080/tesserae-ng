from django.conf.urls import *

urlpatterns = patterns('website.tesserae_ng.views',
    url(r'^$', 'index', name='index'),
    url(r'^index$', 'index', name='index'),
    url(r'^index\.htm$', 'index', name='index'),
    url(r'^index\.html$', 'index', name='index'),
    url(r'^home$', 'index', name='home'),
    url(r'^search/(?P<language>.+)/(?P<level>.+)$', 'search', name='search'),
    url(r'^logout$', 'logout', name='logout'),
    url(r'^login$', 'login', name='login'),
    url(r'^upload$', 'upload', name='upload'),
    url(r'^ingest$', 'ingest', name='ingest'),
    url(r'^s/', include('haystack.urls'))
)
