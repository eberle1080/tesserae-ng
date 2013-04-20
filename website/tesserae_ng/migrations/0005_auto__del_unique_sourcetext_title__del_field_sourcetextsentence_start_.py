# -*- coding: utf-8 -*-
import datetime
from south.db import db
from south.v2 import SchemaMigration
from django.db import models


class Migration(SchemaMigration):

    def forwards(self, orm):
        # Removing unique constraint on 'SourceText', fields ['title']
        db.delete_unique(u'tesserae_ng_sourcetext', ['title'])

        # Deleting field 'SourceTextSentence.start_line_num'
        db.delete_column(u'tesserae_ng_sourcetextsentence', 'start_line_num')

        # Deleting field 'SourceTextSentence.end_line_num'
        db.delete_column(u'tesserae_ng_sourcetextsentence', 'end_line_num')

        # Adding field 'SourceTextSentence.start_line'
        db.add_column(u'tesserae_ng_sourcetextsentence', 'start_line',
                      self.gf('django.db.models.fields.CharField')(default=0, max_length=255),
                      keep_default=False)

        # Adding field 'SourceTextSentence.end_line'
        db.add_column(u'tesserae_ng_sourcetextsentence', 'end_line',
                      self.gf('django.db.models.fields.CharField')(default=0, max_length=255),
                      keep_default=False)


    def backwards(self, orm):
        # Adding unique constraint on 'SourceText', fields ['title']
        db.create_unique(u'tesserae_ng_sourcetext', ['title'])

        # Adding field 'SourceTextSentence.start_line_num'
        db.add_column(u'tesserae_ng_sourcetextsentence', 'start_line_num',
                      self.gf('django.db.models.fields.IntegerField')(default=0),
                      keep_default=False)

        # Adding field 'SourceTextSentence.end_line_num'
        db.add_column(u'tesserae_ng_sourcetextsentence', 'end_line_num',
                      self.gf('django.db.models.fields.IntegerField')(default=0),
                      keep_default=False)

        # Deleting field 'SourceTextSentence.start_line'
        db.delete_column(u'tesserae_ng_sourcetextsentence', 'start_line')

        # Deleting field 'SourceTextSentence.end_line'
        db.delete_column(u'tesserae_ng_sourcetextsentence', 'end_line')


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
            'title': ('django.db.models.fields.CharField', [], {'max_length': '255'})
        },
        u'tesserae_ng.sourcetextsentence': {
            'Meta': {'object_name': 'SourceTextSentence'},
            'end_line': ('django.db.models.fields.CharField', [], {'max_length': '255'}),
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'sentence': ('django.db.models.fields.TextField', [], {}),
            'start_line': ('django.db.models.fields.CharField', [], {'max_length': '255'}),
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