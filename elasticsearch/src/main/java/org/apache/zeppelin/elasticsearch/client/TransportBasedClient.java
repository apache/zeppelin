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

package org.apache.zeppelin.elasticsearch.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.elasticsearch.ElasticsearchInterpreter;
import org.apache.zeppelin.elasticsearch.action.ActionResponse;
import org.apache.zeppelin.elasticsearch.action.AggWrapper;
import org.apache.zeppelin.elasticsearch.action.HitWrapper;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.InternalSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalMetricsAggregation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Elasticsearch client using the transport protocol.
 */
public class TransportBasedClient implements ElasticsearchClient {

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final Client client;

  public TransportBasedClient(Properties props) throws UnknownHostException {
    final String host =
        props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_HOST);
    final int port = Integer.parseInt(
        props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_PORT));
    final String clusterName =
        props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_CLUSTER_NAME);

    final Settings settings = Settings.settingsBuilder()
        .put("cluster.name", clusterName)
        .put(props)
        .build();

    client = TransportClient.builder().settings(settings).build()
        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
  }

  @Override
  public ActionResponse get(String index, String type, String id) {
    final GetResponse getResp = client
        .prepareGet(index, type, id)
        .get();

    return new ActionResponse()
        .succeeded(getResp.isExists())
        .hit(new HitWrapper(
            getResp.getIndex(),
            getResp.getType(),
            getResp.getId(),
            getResp.getSourceAsString()));
  }

  @Override
  public ActionResponse delete(String index, String type, String id) {
    final DeleteResponse delResp = client
        .prepareDelete(index, type, id)
        .get();

    return new ActionResponse()
        .succeeded(delResp.isFound())
        .hit(new HitWrapper(
            delResp.getIndex(),
            delResp.getType(),
            delResp.getId(),
            null));
  }

  @Override
  public ActionResponse index(String index, String type, String id, String data) {
    final IndexResponse idxResp = client
        .prepareIndex(index, type, id)
        .setSource(data)
        .get();

    return new ActionResponse()
        .succeeded(idxResp.isCreated())
        .hit(new HitWrapper(
            idxResp.getIndex(),
            idxResp.getType(),
            idxResp.getId(),
            null));
  }

  @Override
  public ActionResponse search(String[] indices, String[] types, String query, int size) {
    final SearchRequestBuilder reqBuilder = new SearchRequestBuilder(
        client, SearchAction.INSTANCE);
    reqBuilder.setIndices();

    if (indices != null) {
      reqBuilder.setIndices(indices);
    }
    if (types != null) {
      reqBuilder.setTypes(types);
    }

    if (!StringUtils.isEmpty(query)) {
      // The query can be either JSON-formatted, nor a Lucene query
      // So, try to parse as a JSON => if there is an error, consider the query a Lucene one
      try {
        @SuppressWarnings("rawtypes")
        final Map source = gson.fromJson(query, Map.class);
        reqBuilder.setExtraSource(source);
      }
      catch (final JsonSyntaxException e) {
        // This is not a JSON (or maybe not well formatted...)
        reqBuilder.setQuery(QueryBuilders.queryStringQuery(query).analyzeWildcard(true));
      }
    }

    reqBuilder.setSize(size);

    final SearchResponse searchResp = reqBuilder.get();

    final ActionResponse actionResp = new ActionResponse()
        .succeeded(true)
        .totalHits(searchResp.getHits().getTotalHits());

    if (searchResp.getAggregations() != null) {
      setAggregations(searchResp.getAggregations(), actionResp);
    }
    else {
      for (final SearchHit hit: searchResp.getHits()) {
        // Fields can be found either in _source, or in fields (it depends on the query)
        // => specific for elasticsearch's version < 5
        //
        String src = hit.getSourceAsString();
        if (src == null) {
          final Map<String, Object> hitFields = new HashMap<>();
          for (final SearchHitField hitField : hit.getFields().values()) {
            hitFields.put(hitField.getName(), hitField.getValues());
          }
          src = gson.toJson(hitFields);
        }
        actionResp.addHit(new HitWrapper(hit.getIndex(), hit.getType(), hit.getId(), src));
      }
    }

    return actionResp;
  }

  private void setAggregations(Aggregations aggregations, ActionResponse actionResp) {
    // Only the result of the first aggregation is returned
    //
    final Aggregation agg = aggregations.asList().get(0);

    if (agg instanceof InternalMetricsAggregation) {
      actionResp.addAggregation(new AggWrapper(AggWrapper.AggregationType.SIMPLE,
          XContentHelper.toString((InternalMetricsAggregation) agg).toString()));
    }
    else if (agg instanceof InternalSingleBucketAggregation) {
      actionResp.addAggregation(new AggWrapper(AggWrapper.AggregationType.SIMPLE,
          XContentHelper.toString((InternalSingleBucketAggregation) agg).toString()));
    }
    else if (agg instanceof InternalMultiBucketAggregation) {
      final Set<String> headerKeys = new HashSet<>();
      final List<Map<String, Object>> buckets = new LinkedList<>();
      final InternalMultiBucketAggregation multiBucketAgg = (InternalMultiBucketAggregation) agg;

      for (final MultiBucketsAggregation.Bucket bucket : multiBucketAgg.getBuckets()) {
        try {
          final XContentBuilder builder = XContentFactory.jsonBuilder();
          bucket.toXContent(builder, null);
          actionResp.addAggregation(
              new AggWrapper(AggWrapper.AggregationType.MULTI_BUCKETS, builder.string()));
        }
        catch (final IOException e) {
          // Ignored
        }
      }
    }
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
  }

  @Override
  public String toString() {
    return "TransportBasedClient []";
  }
}
