# -*- coding: utf-8 -*-
import datetime
from south.db import db
from south.v2 import SchemaMigration
from django.db import models


class Migration(SchemaMigration):

    def forwards(self, orm):
        # Adding model 'SourceText'
        db.create_table(u'tesserae_ng_sourcetext', (
            (u'id', self.gf('django.db.models.fields.AutoField')(primary_key=True)),
            ('language', self.gf('django.db.models.fields.CharField')(max_length=20)),
            ('author', self.gf('django.db.models.fields.CharField')(max_length=255)),
            ('title', self.gf('django.db.models.fields.CharField')(unique=True, max_length=255)),
            ('online_source_name', self.gf('django.db.models.fields.CharField')(max_length=4096, null=True, blank=True)),
            ('online_source_link', self.gf('django.db.models.fields.URLField')(max_length=4096, null=True, blank=True)),
            ('print_source_name', self.gf('django.db.models.fields.CharField')(max_length=4096, null=True, blank=True)),
            ('print_source_link', self.gf('django.db.models.fields.URLField')(max_length=4096, null=True, blank=True)),
            ('text', self.gf('django.db.models.fields.TextField')()),
            ('enabled', self.gf('django.db.models.fields.BooleanField')(default=True)),
        ))
        db.send_create_signal(u'tesserae_ng', ['SourceText'])


    def backwards(self, orm):
        # Deleting model 'SourceText'
        db.delete_table(u'tesserae_ng_sourcetext')


    models = {
        u'tesserae_ng.sourcetext': {
            'Meta': {'object_name': 'SourceText'},
            'author': ('django.db.models.fields.CharField', [], {'max_length': '255'}),
            'enabled': ('django.db.models.fields.BooleanField', [], {'default': 'True'}),
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'language': ('django.db.models.fields.CharField', [], {'max_length': '20'}),
            'online_source_link': ('django.db.models.fields.URLField', [], {'max_length': '4096', 'null': 'True', 'blank': 'True'}),
            'online_source_name': ('django.db.models.fields.CharField', [], {'max_length': '4096', 'null': 'True', 'blank': 'True'}),
            'print_source_link': ('django.db.models.fields.URLField', [], {'max_length': '4096', 'null': 'True', 'blank': 'True'}),
            'print_source_name': ('django.db.models.fields.CharField', [], {'max_length': '4096', 'null': 'True', 'blank': 'True'}),
            'text': ('django.db.models.fields.TextField', [], {}),
            'title': ('django.db.models.fields.CharField', [], {'unique': 'True', 'max_length': '255'})
        }
    }

    complete_apps = ['tesserae_ng']