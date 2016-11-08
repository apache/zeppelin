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


import com.github.wnameless.json.flattener.JsonFlattener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface for ElasticsearchConnector implementations.
 */
public abstract class ElasticsearchConnector {

  private static Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);
  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("\\[\\\\\"(.+)\\\\\"\\](.*)");
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public static final String ELASTICSEARCH_HOST = "elasticsearch.host";
  public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
  public static final String ELASTICSEARCH_CLUSTER_NAME = "elasticsearch.cluster.name";
  public static final String ELASTICSEARCH_RESULT_SIZE = "elasticsearch.output.size";
  private static final String HELP = "Elasticsearch interpreter:\n"
      + "General format: <command> /<indices>/<types>/<id> <option> <JSON>\n"
      + "  - indices: list of indices separated by commas (depends on the command)\n"
      + "  - types: list of document types separated by commas (depends on the command)\n"
      + "Commands:\n"
      + "  - search /indices/types <query>\n"
      + "    . indices and types can be omitted (at least, you have to provide '/')\n"
      + "    . a query is either a JSON-formatted query, nor a lucene query\n"
      + "  - size <value>\n"
      + "    . defines the size of the output set (default value is in the config)\n"
      + "    . if used, this command must be declared before a search command\n"
      + "  - count /indices/types <query>\n"
      + "    . same comments as for the search\n"
      + "  - get /index/type/id\n"
      + "  - delete /index/type/id\n"
      + "  - index /ndex/type/id <json-formatted document>\n"
      + "    . the id can be omitted, elasticsearch will generate one";

  public static String getHelpString(String additionalMessage) {
    final StringBuffer buffer = new StringBuffer();

    if (additionalMessage != null) {
      buffer.append(additionalMessage).append("\n");
    }

    buffer.append(HELP).append("\n");

    return buffer.toString();
  }

  protected String host = "localhost";
  protected int port = 9300;
  protected String clusterName = "elasticsearch";
  protected int resultSize = 10;
  protected Client client;

  public int getResultSize() {
    return resultSize;
  }

  protected ElasticsearchConnector(String host, int port, String clusterName, int resultSize) {
    this.host = host;
    this.port = port;
    this.clusterName = clusterName;
    this.resultSize = resultSize;
  }

  abstract void connect(Properties properties);
  abstract void release();
  abstract String executeDeleteQuery(String [] urlItems);

  /**
   * Execute a "get" request.
   *
   * @param urlItems Items of the URL
   * @return Result of the get request, it contains a JSON-formatted string
   */
  public String executeGetQuery(String[] urlItems) {

    if (urlItems.length != 3
        || StringUtils.isEmpty(urlItems[0])
        || StringUtils.isEmpty(urlItems[1])
        || StringUtils.isEmpty(urlItems[2])) {
      throw new RuntimeException("Bad URL (it should be /index/type/id):" + urlItems);
    }

    final GetResponse response = client
        .prepareGet(urlItems[0], urlItems[1], urlItems[2])
        .get();

    if (!response.isExists()) {
      throw new RuntimeException("Document not found");
    }

    return gson.toJson(response.getSource());
  }

  /**
   * Execute a "index" request.
   *
   * @param urlItems Items of the URL
   * @param data     JSON to be indexed
   * @return Result of the index request, it contains the id of the document
   */
  public String executeIndexQuery (String[] urlItems, String data) {
    if (urlItems.length < 2 || urlItems.length > 3) {
      throw new RuntimeException(
          "Bad URL (it should be /index/type or /index/type/id) " + urlItems);
    }

    final IndexResponse response = client
        .prepareIndex(urlItems[0], urlItems[1], urlItems.length == 2 ? null : urlItems[2])
        .setSource(data)
        .get();

    return response.getId();
  }

  /**
   * Execute a "count" request.
   *
   * @param urlItems Items of the URL
   * @param data    May contains the JSON of the request
   * @return Result of the count request, it contains the total hits
   */
  public String executeCountQuery(String[] urlItems, String data) {
    if (urlItems.length > 2) {
      throw new RuntimeException(
          "Bad URL (it should be /index1,index2,.../type1,type2,...) " + urlItems);
    }
    final SearchResponse response = searchData(urlItems, data, 0);

    return "" + response.getHits().getTotalHits();
  }

  /**
   * Execute a "search" request.
   *
   * @param urlItems Items of the URL
   * @param data     May contains the JSON of the request
   * @param size     Limit of output set
   * @return Result of the search request with its type,
   *         it contains a tab-formatted string of the matching hits
   */
  public TypedElasticConnectorResult executeSearchQuery(String[] urlItems, String data, int size) {

    if (urlItems.length > 2) {
      throw new RuntimeException(
          "Bad URL (it should be /index1,index2,.../type1,type2,...) :" + urlItems);
    }

    final SearchResponse response = searchData(urlItems, data, size);
    final Aggregations aggregations = response.getAggregations();

    if (aggregations != null && aggregations.asList().size() > 0) {
      return buildAggResponseMessage(aggregations);
    }

    return new TypedElasticConnectorResult(InterpreterResult.Type.TABLE,
        buildSearchHitsResponseMessage(response.getHits().getHits()));
  }

  private TypedElasticConnectorResult buildAggResponseMessage(Aggregations aggregations) {
    // Only the output of the first aggregation is returned
    final Aggregation agg = aggregations.asList().get(0);
    InterpreterResult.Type resType = InterpreterResult.Type.TEXT;
    String resMsg = "";

    if (agg instanceof InternalMetricsAggregation) {
      resMsg = XContentHelper.toString((InternalMetricsAggregation) agg).toString();
    } else if (agg instanceof InternalSingleBucketAggregation) {
      resMsg = XContentHelper.toString((InternalSingleBucketAggregation) agg).toString();
    } else {
      if (agg instanceof InternalMultiBucketAggregation) {
        final Set<String> headerKeys = new HashSet<>();
        final List<Map<String, Object>> buckets = new LinkedList<>();
        final InternalMultiBucketAggregation multiBucketAgg = (InternalMultiBucketAggregation) agg;

        for (MultiBucketsAggregation.Bucket bucket : multiBucketAgg.getBuckets()) {
          try {
            final XContentBuilder builder = XContentFactory.jsonBuilder();
            bucket.toXContent(builder, null);
            final Map<String, Object> bucketMap = JsonFlattener.flattenAsMap(builder.string());
            headerKeys.addAll(bucketMap.keySet());
            buckets.add(bucketMap);
          } catch (IOException e) {
            logger.error("Processing bucket: " + e.getMessage(), e);
          }
        }

        final StringBuffer buffer = new StringBuffer();
        final String[] keys = headerKeys.toArray(new String[0]);
        for (String key : keys) {
          buffer.append("\t").append(key);
        }
        buffer.deleteCharAt(0);

        for (Map<String, Object> bucket : buckets) {
          buffer.append("\n");

          for (String key : keys) {
            buffer.append(bucket.get(key)).append("\t");
          }
          buffer.deleteCharAt(buffer.length() - 1);
        }

        resType = InterpreterResult.Type.TABLE;
        resMsg = buffer.toString();
      }
    }

    return new TypedElasticConnectorResult(resType, resMsg);
  }

  private String buildSearchHitsResponseMessage(SearchHit[] hits) {
    if (hits == null || hits.length == 0) {
      return "";
    }

    // 1. Get all the keys in order to build an ordered list of the values for each hit
    final Map<String, Object> hitFields = new HashMap<>();
    final List<Map<String, Object>> flattenHits = new LinkedList<>();
    final Set<String> keys = new TreeSet<>();
    for (SearchHit hit : hits) {
      // Fields can be found either in _source, or in fields (it depends on the query)
      String json = hit.getSourceAsString();
      if (json == null) {
        hitFields.clear();
        for (SearchHitField hitField : hit.getFields().values()) {
          hitFields.put(hitField.getName(), hitField.getValues());
        }
        json = gson.toJson(hitFields);
      }

      final Map<String, Object> flattenJsonMap = JsonFlattener.flattenAsMap(json);
      final Map<String, Object> flattenMap = new HashMap<>();
      for (Iterator<String> iter = flattenJsonMap.keySet().iterator(); iter.hasNext(); ) {
        // Replace keys that match a format like that : [\"keyname\"][0]
        final String fieldName = iter.next();
        final Matcher fieldNameMatcher = FIELD_NAME_PATTERN.matcher(fieldName);
        if (fieldNameMatcher.matches()) {
          flattenMap.put(fieldNameMatcher.group(1) + fieldNameMatcher.group(2),
              flattenJsonMap.get(fieldName));
        } else {
          flattenMap.put(fieldName, flattenJsonMap.get(fieldName));
        }
      }
      flattenHits.add(flattenMap);

      for (String key : flattenMap.keySet()) {
        keys.add(key);
      }
    }

    // 2. Build the header of the table
    final StringBuffer buffer = new StringBuffer();
    for (String key : keys) {
      buffer.append(key).append('\t');
    }
    buffer.replace(buffer.lastIndexOf("\t"), buffer.lastIndexOf("\t") + 1, "\n");

    // 3. Build the output by using the key set
    for (Map<String, Object> hit : flattenHits) {
      for (String key : keys) {
        final Object val = hit.get(key);
        if (val != null) {
          buffer.append(val);
        }
        buffer.append('\t');
      }
      buffer.replace(buffer.lastIndexOf("\t"), buffer.lastIndexOf("\t") + 1, "\n");
    }

    return buffer.toString();
  }

  private SearchResponse searchData(String[] urlItems, String query, int size) {

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
