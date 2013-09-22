from django.contrib import admin
from website.graphite.events.models import Event

admin.site.register(Event)
