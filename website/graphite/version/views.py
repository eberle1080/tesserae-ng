from django.shortcuts import render_to_response
from django.conf import settings


def index(request):
  context = {
    'version' : settings.WEBAPP_VERSION,
  }
  return render_to_response('graphite/version.html', context)
