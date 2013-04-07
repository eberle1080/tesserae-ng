# -*- coding: utf-8 -*-
import datetime
from south.db import db
from south.v2 import SchemaMigration
from django.db import models


class Migration(SchemaMigration):

    def forwards(self, orm):
        # Adding model 'SourceTextLine'
        db.create_table(u'tesserae_ng_sourcetextline', (
            (u'id', self.gf('django.db.models.fields.AutoField')(primary_key=True)),
            ('source', self.gf('django.db.models.fields.related.ForeignKey')(to=orm['tesserae_ng.SourceText'], on_delete=models.PROTECT)),
            ('line', self.gf('django.db.models.fields.TextField')()),
        ))
        db.send_create_signal(u'tesserae_ng', ['SourceTextLine'])


    def backwards(self, orm):
        # Deleting model 'SourceTextLine'
        db.delete_table(u'tesserae_ng_sourcetextline')


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
        },
        u'tesserae_ng.sourcetextline': {
            'Meta': {'object_name': 'SourceTextLine'},
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'line': ('django.db.models.fields.TextField', [], {}),
            'source': ('django.db.models.fields.related.ForeignKey', [], {'to': u"orm['tesserae_ng.SourceText']", 'on_delete': 'models.PROTECT'})
        }
    }

    complete_apps = ['tesserae_ng']