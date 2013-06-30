from django.db import models
from django.contrib import admin
from dirtyfields import DirtyFieldsMixin
import reversion

class SourceText(models.Model, DirtyFieldsMixin):
    LANGUAGE_CHOICES = (
        ('latin', 'Latin'),
        ('greek', 'Greek'),
        ('english', 'English')
    )

    language = models.CharField(max_length=20, choices=LANGUAGE_CHOICES)
    author = models.CharField(max_length=255)
    title = models.CharField(max_length=255)
    online_source_name = models.CharField(max_length=4096, null=True, blank=True)
    online_source_link = models.URLField(max_length=4096, null=True, blank=True)
    print_source_name = models.CharField(max_length=4096, null=True, blank=True)
    print_source_link = models.URLField(max_length=4096, null=True, blank=True)
    enabled = models.BooleanField(default=True)

reversion.register(SourceText)

class SourceTextAdmin(reversion.VersionAdmin):
    list_display = ('title', 'author', 'language')
    fieldsets = (
        (None, {
            'fields': ('language', 'author', 'title',
                ('online_source_name', 'online_source_link'),
                ('print_source_name', 'print_source_link'))
        }),
    )

admin.site.register(SourceText, SourceTextAdmin)

class SourceTextVolume(models.Model, DirtyFieldsMixin):
    source = models.ForeignKey(SourceText, on_delete=models.PROTECT)
    volume = models.CharField(max_length=255)
    text = models.TextField(db_index=False)

reversion.register(SourceTextVolume)

class SourceTextVolumeAdmin(reversion.VersionAdmin):
    list_display = ('source', 'volume')
    fieldsets = (
        (None, {
            'fields': ('source', 'volume')
        }),
        ('Full text', {
            'classes': ('collapse',),
            'fields': ('text',)
        })
    )

admin.site.register(SourceTextVolume, SourceTextVolumeAdmin)

class SourceTextSentence(models.Model, DirtyFieldsMixin):
    volume = models.ForeignKey(SourceTextVolume, on_delete=models.PROTECT)
    sentence = models.TextField(db_index=False)
    start_line = models.CharField(max_length=255)
    end_line = models.CharField(max_length=255)

reversion.register(SourceTextSentence)

class SourceTextSentenceAdmin(reversion.VersionAdmin):
    list_display = ('volume', 'sentence',)
    fieldsets = (
        (None, {
            'fields': ('volume', 'sentence', 'start_line', 'end_line')
        }),
    )

admin.site.register(SourceTextSentence, SourceTextSentenceAdmin)
