/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.elasticsearch;

import com.google.gson.JsonParseException;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;


/**
 * Connector for Elasticsearch 2.x used in ElasticsearchInterpreter.
 */
public class Elasticsearch2Connector extends ElasticsearchConnector {

  private static Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  protected Elasticsearch2Connector(String host, int port, String clusterName,
                                 int resultSize) {
    super(host, port, clusterName, resultSize);
  }

  @Override
  public void connect(Properties props) {
    logger.info("prop={}", props);

    try {
      final Settings settings = Settings.builder()
          .put("cluster.name", clusterName)
          .put(props)
          .build();
      client = TransportClient.builder().settings(settings).build()
          .addTransportAddress(
          new InetSocketTransportAddress(InetAddress.getByName(host), port));
    } catch (IOException e) {
      logger.error("Open connection with Elasticsearch", e);
    }
  }

  @Override
  public void release() {
    if (client != null) {
      client.close();
    }
  }

  /**
   * Execute a "delete" request.
   *
   * @param urlItems Items of the URL
   * @return Result of the delete request, it contains the id of the deleted document
   */
  public String executeDeleteQuery(String[] urlItems) {
    if (urlItems.length != 3
        || StringUtils.isEmpty(urlItems[0])
        || StringUtils.isEmpty(urlItems[1])
        || StringUtils.isEmpty(urlItems[2])) {
      throw new RuntimeException("Bad URL (it should be /index/type/id) : " + urlItems);
    }

    final DeleteResponse response = client
        .prepareDelete(urlItems[0], urlItems[1], urlItems[2])
        .get();

    if (!response.isFound()) {
      throw new RuntimeException("Document not found");
    }

    return response.getId();
  }

  @Override
  protected SearchResponse searchData(String[] urlItems, String query, int size) {

    final SearchRequestBuilder reqBuilder =
        new SearchRequestBuilder(client, SearchAction.INSTANCE);
    reqBuilder.setIndices();

    if (urlItems.length >= 1) {
      reqBuilder.setIndices(StringUtils.split(urlItems[0], ","));
    }
    if (urlItems.length > 1) {
      reqBuilder.setTypes(StringUtils.split(urlItems[1], ","));
    }

    if (!StringUtils.isEmpty(query)) {
      // The query can be either JSON-formatted, nor a Lucene query
      // So, try to parse as a JSON => if there is an error, consider the query a Lucene one
      try {
        final Map source = gson.fromJson(query, Map.class);
        reqBuilder.setExtraSource(source);
      } catch (JsonParseException e) {
        // This is not a JSON (or maybe not well formatted...)
        reqBuilder.setQuery(QueryBuilders.queryStringQuery(query).analyzeWildcard(true));
      }
    }

    reqBuilder.setSize(size);
    final SearchResponse response = reqBuilder.get();
    return response;
  }
}
