from django.db import models
from django.contrib import admin
import reversion

class SourceText(models.Model):
    LANGUAGE_CHOICES = (
        ('latin', 'Latin'),
        ('greek', 'Greek'),
        ('english', 'English')
    )

    language = models.CharField(max_length=20, choices=LANGUAGE_CHOICES)
    author = models.CharField(max_length=255)
    title = models.CharField(max_length=255, unique=True)
    online_source_name = models.CharField(max_length=4096, null=True, blank=True)
    online_source_link = models.URLField(max_length=4096, null=True, blank=True)
    print_source_name = models.CharField(max_length=4096, null=True, blank=True)
    print_source_link = models.URLField(max_length=4096, null=True, blank=True)
    text = models.TextField(db_index=False)
    enabled = models.BooleanField(default=True)

class SourceTextAdmin(reversion.VersionAdmin):
    list_display = ('title', 'author', 'language')
    fieldsets = (
        (None, {
            'fields': ('language', 'author', 'title',
                ('online_source_name', 'online_source_link'),
                ('print_source_name', 'print_source_link'))
        }),
        ('Full text', {
            'classes': ('collapse',),
            'fields': ('text',)
        })
    )

admin.site.register(SourceText, SourceTextAdmin)
