from django.conf.urls import *
from django.views.generic import TemplateView
from django.views.generic.base import RedirectView
from django.conf.urls.static import static
from django.conf import settings

from django.contrib import admin
admin.autodiscover()

urlpatterns = patterns('',
    (r'^admin/', include(admin.site.urls)),
    (r'^robots\.txt$', TemplateView.as_view(template_name='robots.txt', content_type='text/plain')),
    (r'^humans\.txt$', TemplateView.as_view(template_name='humans.txt', content_type='text/plain')),
    (r'^favicon\.ico$', RedirectView.as_view(url='/media/img/favicon.ico')),
    (r'^graphite/render/?', include('website.graphite.render.urls')),
    (r'^graphite/cli/?', include('website.graphite.cli.urls')),
    (r'^graphite/composer/?', include('website.graphite.composer.urls')),
    (r'^graphite/metrics/?', include('website.graphite.metrics.urls')),
    (r'^graphite/browser/?', include('website.graphite.browser.urls')),
    (r'^graphite/account/?', include('website.graphite.account.urls')),
    (r'^graphite/dashboard/?', include('website.graphite.dashboard.urls')),
    (r'^graphite/whitelist/?', include('website.graphite.whitelist.urls')),
    (r'^graphite/content/(?P<path>.*)$', 'django.views.static.serve', {'document_root' : settings.CONTENT_DIR}),
    (r'^graphite/graphlot/', include('website.graphite.graphlot.urls')),
    (r'^graphite/version/', include('website.graphite.version.urls')),
    (r'^graphite/events/', include('website.graphite.events.urls')),
    (r'^graphite/', 'website.graphite.browser.views.browser'),
    (r'^', include('website.tesserae_ng.urls'))
) + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
