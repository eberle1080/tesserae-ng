package org.tesserae.codec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.LiveDocsFormat;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.lucene40.Lucene40LiveDocsFormat;
import org.apache.lucene.codecs.lucene40.Lucene40SegmentInfoFormat;
import org.apache.lucene.codecs.lucene40.Lucene40TermVectorsFormat;
import org.apache.lucene.codecs.lucene41.Lucene41StoredFieldsFormat;
import org.apache.lucene.codecs.lucene42.Lucene42FieldInfosFormat;
import org.apache.lucene.codecs.lucene42.Lucene42NormsFormat;
import org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.SchemaField;

/**
 * Implements the Lucene 4.2 index format, with configurable per-field postings
 * and docvalues formats.
 * <p>
 * If you want to reuse functionality of this codec in another codec, extend
 * {@link FilterCodec}.
 *
 * @see org.apache.lucene.codecs.lucene42 package documentation for file format details.
 * @lucene.experimental
 */
// NOTE: if we make largish changes in a minor release, easier to just make Lucene43Codec or whatever
// if they are backwards compatible or smallish we can probably do the backwards in the postingsreader
// (it writes a minor version, etc).
public class CustomLucene42Codec extends Codec {
  private final StoredFieldsFormat fieldsFormat = new Lucene41StoredFieldsFormat();
  private final TermVectorsFormat vectorsFormat = new Lucene40TermVectorsFormat(); // <-- See notes below
  private final FieldInfosFormat fieldInfosFormat = new Lucene42FieldInfosFormat();
  private final SegmentInfoFormat infosFormat = new Lucene40SegmentInfoFormat();
  private final LiveDocsFormat liveDocsFormat = new Lucene40LiveDocsFormat();
  private final GetCoreCallback coreCallback;

  // NOTES: Later versions of Solr use a compressing term-vectors format.
  // This compression slows down searches by about 1 full second (every time)
  // By using a deprecated class (prior to the compressed format) I can
  // improve the search speed.

  private final PostingsFormat postingsFormat = new PerFieldPostingsFormat() {
    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
      return CustomLucene42Codec.this.getPostingsFormatForField(field);
    }
  };

  private final DocValuesFormat docValuesFormat = new PerFieldDocValuesFormat() {
    @Override
    public DocValuesFormat getDocValuesFormatForField(String field) {
      return CustomLucene42Codec.this.getDocValuesFormatForField(field);
    }
  };

  public CustomLucene42Codec(final GetCoreCallback coreCallback) {
    super("CustomLucene42Codec");
    this.coreCallback = coreCallback;
  }

  public CustomLucene42Codec() {
    super("CustomLucene42Codec");
    this.coreCallback = null;
  }

  @Override
  public final StoredFieldsFormat storedFieldsFormat() {
    return fieldsFormat;
  }

  @Override
  public final TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }

  @Override
  public final PostingsFormat postingsFormat() {
    return postingsFormat;
  }

  @Override
  public final FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }

  @Override
  public final SegmentInfoFormat segmentInfoFormat() {
    return infosFormat;
  }

  @Override
  public final LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }

  /** Returns the postings format that should be used for writing 
   *  new segments of <code>field</code>.
   *  
   *  The default implementation always returns "Lucene41"
   */
  public PostingsFormat getPostingsFormatForField(String field) {
    if (coreCallback == null) {
      return defaultFormat;
    } else {
      final SolrCore core = coreCallback.getCore();
      if (core == null) {
        return defaultFormat;
      } else {
        final SchemaField fieldOrNull = core.getLatestSchema().getFieldOrNull(field);
        if (fieldOrNull == null) {
          throw new IllegalArgumentException("no such field " + field);
        }

        String postingsFormatName = fieldOrNull.getType().getPostingsFormat();
        if (postingsFormatName != null) {
          return PostingsFormat.forName(postingsFormatName);
        } else {
          return defaultFormat;
        }
      }
    }
  }

  /** Returns the docvalues format that should be used for writing 
   *  new segments of <code>field</code>.
   *  
   *  The default implementation always returns "Lucene42"
   */
  public DocValuesFormat getDocValuesFormatForField(String field) {
    if (coreCallback == null) {
      return defaultDVFormat;
    } else {
      final SolrCore core = coreCallback.getCore();
      if (core == null) {
        return defaultDVFormat;
      } else {
        final SchemaField fieldOrNull = core.getLatestSchema().getFieldOrNull(field);
        if (fieldOrNull == null) {
          throw new IllegalArgumentException("no such field " + field);
        }

        String docValuesFormatName = fieldOrNull.getType().getDocValuesFormat();
        if (docValuesFormatName != null) {
          return DocValuesFormat.forName(docValuesFormatName);
        } else {
          return defaultDVFormat;
        }
      }
    }
  }

  @Override
  public final DocValuesFormat docValuesFormat() {
    return docValuesFormat;
  }

  private final PostingsFormat defaultFormat = PostingsFormat.forName("Lucene41");
  private final DocValuesFormat defaultDVFormat = DocValuesFormat.forName("Lucene42");

  private final NormsFormat normsFormat = new Lucene42NormsFormat();

  @Override
  public final NormsFormat normsFormat() {
    return normsFormat;
  }
}
