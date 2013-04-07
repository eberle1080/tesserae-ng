# -*- coding: utf-8 -*-
import datetime
from south.db import db
from south.v2 import SchemaMigration
from django.db import models


class Migration(SchemaMigration):

    def forwards(self, orm):
        # Deleting model 'SourceTextLine'
        db.delete_table(u'tesserae_ng_sourcetextline')

        # Adding model 'SourceTextSentence'
        db.create_table(u'tesserae_ng_sourcetextsentence', (
            (u'id', self.gf('django.db.models.fields.AutoField')(primary_key=True)),
            ('volume', self.gf('django.db.models.fields.related.ForeignKey')(to=orm['tesserae_ng.SourceTextVolume'], on_delete=models.PROTECT)),
            ('sentence', self.gf('django.db.models.fields.TextField')()),
            ('start_line_num', self.gf('django.db.models.fields.IntegerField')()),
            ('end_line_num', self.gf('django.db.models.fields.IntegerField')()),
        ))
        db.send_create_signal(u'tesserae_ng', ['SourceTextSentence'])


    def backwards(self, orm):
        # Adding model 'SourceTextLine'
        db.create_table(u'tesserae_ng_sourcetextline', (
            ('volume', self.gf('django.db.models.fields.related.ForeignKey')(to=orm['tesserae_ng.SourceTextVolume'], on_delete=models.PROTECT)),
            ('line', self.gf('django.db.models.fields.TextField')()),
            (u'id', self.gf('django.db.models.fields.AutoField')(primary_key=True)),
        ))
        db.send_create_signal(u'tesserae_ng', ['SourceTextLine'])

        # Deleting model 'SourceTextSentence'
        db.delete_table(u'tesserae_ng_sourcetextsentence')


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
            'title': ('django.db.models.fields.CharField', [], {'unique': 'True', 'max_length': '255'})
        },
        u'tesserae_ng.sourcetextsentence': {
            'Meta': {'object_name': 'SourceTextSentence'},
            'end_line_num': ('django.db.models.fields.IntegerField', [], {}),
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'sentence': ('django.db.models.fields.TextField', [], {}),
            'start_line_num': ('django.db.models.fields.IntegerField', [], {}),
            'volume': ('django.db.models.fields.related.ForeignKey', [], {'to': u"orm['tesserae_ng.SourceTextVolume']", 'on_delete': 'models.PROTECT'})
        },
        u'tesserae_ng.sourcetextvolume': {
            'Meta': {'object_name': 'SourceTextVolume'},
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'source': ('django.db.models.fields.related.ForeignKey', [], {'to': u"orm['tesserae_ng.SourceText']", 'on_delete': 'models.PROTECT'}),
            'text': ('django.db.models.fields.TextField', [], {}),
            'volume': ('django.db.models.fields.CharField', [], {'max_length': '255'})
        }
    }

    complete_apps = ['tesserae_ng']