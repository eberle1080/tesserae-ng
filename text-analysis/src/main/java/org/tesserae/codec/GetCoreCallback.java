package org.tesserae.codec;

import org.apache.solr.core.SolrCore;

/**
 * A way to pass in a SolrCore reference that may change
 */
public interface GetCoreCallback {
  SolrCore getCore();
}
