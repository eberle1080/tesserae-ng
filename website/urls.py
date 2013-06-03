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
    (r'^', include('website.tesserae_ng.urls'))
) + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
