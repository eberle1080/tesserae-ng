# -*- coding: utf-8 -*-
import datetime
from south.db import db
from south.v2 import SchemaMigration
from django.db import models


class Migration(SchemaMigration):

    def forwards(self, orm):
        # Adding model 'SourceTextVolume'
        db.create_table(u'tesserae_ng_sourcetextvolume', (
            (u'id', self.gf('django.db.models.fields.AutoField')(primary_key=True)),
            ('source', self.gf('django.db.models.fields.related.ForeignKey')(to=orm['tesserae_ng.SourceText'], on_delete=models.PROTECT)),
            ('volume', self.gf('django.db.models.fields.CharField')(max_length=255)),
            ('text', self.gf('django.db.models.fields.TextField')()),
        ))
        db.send_create_signal(u'tesserae_ng', ['SourceTextVolume'])

        # Deleting field 'SourceText.text'
        db.delete_column(u'tesserae_ng_sourcetext', 'text')

        # Deleting field 'SourceTextLine.source'
        db.delete_column(u'tesserae_ng_sourcetextline', 'source_id')

        # Adding field 'SourceTextLine.volume'
        db.add_column(u'tesserae_ng_sourcetextline', 'volume',
                      self.gf('django.db.models.fields.related.ForeignKey')(default=0, to=orm['tesserae_ng.SourceTextVolume'], on_delete=models.PROTECT),
                      keep_default=False)


    def backwards(self, orm):
        # Deleting model 'SourceTextVolume'
        db.delete_table(u'tesserae_ng_sourcetextvolume')


        # User chose to not deal with backwards NULL issues for 'SourceText.text'
        raise RuntimeError("Cannot reverse this migration. 'SourceText.text' and its values cannot be restored.")

        # User chose to not deal with backwards NULL issues for 'SourceTextLine.source'
        raise RuntimeError("Cannot reverse this migration. 'SourceTextLine.source' and its values cannot be restored.")
        # Deleting field 'SourceTextLine.volume'
        db.delete_column(u'tesserae_ng_sourcetextline', 'volume_id')


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
        u'tesserae_ng.sourcetextline': {
            'Meta': {'object_name': 'SourceTextLine'},
            u'id': ('django.db.models.fields.AutoField', [], {'primary_key': 'True'}),
            'line': ('django.db.models.fields.TextField', [], {}),
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