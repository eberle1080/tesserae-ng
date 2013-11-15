package org.tesserae.codec;

import org.apache.lucene.codecs.Codec;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CodecFactory;
import org.apache.solr.core.SolrCore;
import org.apache.solr.util.plugin.SolrCoreAware;

public class CustomSchemaCodecFactory extends CodecFactory implements SolrCoreAware {
  private Codec codec;
  private volatile SolrCore core;

  @Override
  public void inform(SolrCore core) {
    this.core = core;
  }

  @Override
  public void init(NamedList args) {
    super.init(args);
    codec = new CustomLucene42Codec(new GetCoreCallback() {
      public SolrCore getCore() {
        return core;
      }
    });
  }

  @Override
  public Codec getCodec() {
    assert core != null : "inform must be called first";
    return codec;
  }
}
