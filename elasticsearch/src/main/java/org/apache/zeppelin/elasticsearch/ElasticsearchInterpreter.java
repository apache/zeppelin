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
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
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
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.InternalSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalMetricsAggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;


/**
 * Elasticsearch Interpreter for Zeppelin.
 */
public class ElasticsearchInterpreter extends Interpreter {

  private static Logger logger = LoggerFactory.getLogger(ElasticsearchInterpreter.class);

  private static final String HELP = "Elasticsearch interpreter:\n"
    + "General format: <command> /<indices>/<types>/<id> <option> <JSON>\n"
    + "  - indices: list of indices separated by commas (depends on the command)\n"
    + "  - types: list of document types separated by commas (depends on the command)\n"
    + "Commands:\n"
    + "  - search /indices/types <query>\n"
    + "    . indices and types can be omitted (at least, you have to provide '/')\n"
    + "    . a query is either a JSON-formatted query, nor a lucene query\n"
    + "  - size <value>\n"
    + "    . defines the size of the result set (default value is in the config)\n"
    + "    . if used, this command must be declared before a search command\n"
    + "  - count /indices/types <query>\n"
    + "    . same comments as for the search\n"
    + "  - get /index/type/id\n"
    + "  - delete /index/type/id\n"
    + "  - index /ndex/type/id <json-formatted document>\n"
    + "    . the id can be omitted, elasticsearch will generate one";

  private static final List<String> COMMANDS = Arrays.asList(
    "count", "delete", "get", "help", "index", "search");
    

  public static final String ELASTICSEARCH_HOST = "elasticsearch.host";
  public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
  public static final String ELASTICSEARCH_CLUSTER_NAME = "elasticsearch.cluster.name";
  public static final String ELASTICSEARCH_RESULT_SIZE = "elasticsearch.result.size";

  static {
    Interpreter.register(
      "elasticsearch",
      "elasticsearch",
      ElasticsearchInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
          .add(ELASTICSEARCH_HOST, "localhost", "The host for Elasticsearch")
          .add(ELASTICSEARCH_PORT, "9300", "The port for Elasticsearch")
          .add(ELASTICSEARCH_CLUSTER_NAME, "elasticsearch", "The cluster name for Elasticsearch")
          .add(ELASTICSEARCH_RESULT_SIZE, "10", "The size of the result set of a search query")
          .build());
  }

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private Client client;
  private String host = "localhost";
  private int port = 9300;
  private String clusterName = "elasticsearch";
  private int resultSize = 10;

  public ElasticsearchInterpreter(Properties property) {
    super(property);
    this.host = getProperty(ELASTICSEARCH_HOST);
    this.port = Integer.parseInt(getProperty(ELASTICSEARCH_PORT));
    this.clusterName = getProperty(ELASTICSEARCH_CLUSTER_NAME);
    this.resultSize = Integer.parseInt(getProperty(ELASTICSEARCH_RESULT_SIZE));
  }

  @Override
  public void open() {
    try {
      logger.info("prop={}", getProperty());
      final Settings settings = Settings.settingsBuilder()
        .put("cluster.name", clusterName)
        .put(getProperty())
        .build();
      client = TransportClient.builder().settings(settings).build()
        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
    }
    catch (IOException e) {
      logger.error("Open connection with Elasticsearch", e);
    }
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext interpreterContext) {
    logger.info("Run Elasticsearch command '" + cmd + "'");

    int currentResultSize = resultSize;

    if (client == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Problem with the Elasticsearch client, please check your configuration (host, port,...)");
    }

    String[] items = StringUtils.split(cmd.trim(), " ", 3);

    // Process some specific commands (help, size, ...)
    if ("help".equalsIgnoreCase(items[0])) {
      return processHelp(InterpreterResult.Code.SUCCESS, null);
    }

    if ("size".equalsIgnoreCase(items[0])) {
      // In this case, the line with size must be followed by a search,
      // so we will continue with the next lines
      final String[] lines = StringUtils.split(cmd.trim(), "\n", 2);

      if (lines.length < 2) {
        return processHelp(InterpreterResult.Code.ERROR,
                           "Size cmd must be followed by a search");
      }

      final String[] sizeLine = StringUtils.split(lines[0], " ", 2);
      if (sizeLine.length != 2) {
        return processHelp(InterpreterResult.Code.ERROR, "Right format is : size <value>");
      }
      currentResultSize = Integer.parseInt(sizeLine[1]);

      items = StringUtils.split(lines[1].trim(), " ", 3);
    }

    if (items.length < 2) {
      return processHelp(InterpreterResult.Code.ERROR, "Arguments missing");
    }

    final String method = items[0];
    final String url = items[1];
    final String data = items.length > 2 ? items[2].trim() : null;

    final String[] urlItems = StringUtils.split(url.trim(), "/");

    try {
      if ("get".equalsIgnoreCase(method)) {
        return processGet(urlItems);
      }
      else if ("count".equalsIgnoreCase(method)) {
        return processCount(urlItems, data);
      }
      else if ("search".equalsIgnoreCase(method)) {
        return processSearch(urlItems, data, currentResultSize);
      }
      else if ("index".equalsIgnoreCase(method)) {
        return processIndex(urlItems, data);
      }
      else if ("delete".equalsIgnoreCase(method)) {
        return processDelete(urlItems);
      }

      return processHelp(InterpreterResult.Code.ERROR, "Unknown command");
    }
    catch (Exception e) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Error : " + e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext interpreterContext) {
    // Nothing to do
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext interpreterContext) {
    return 0;
  }

  @Override
  public List<String> completion(String s, int i) {
    final List<String> suggestions = new ArrayList<>();

    if (StringUtils.isEmpty(s)) {
      suggestions.addAll(COMMANDS);
    }
    else {
      for (String cmd : COMMANDS) {
        if (cmd.toLowerCase().contains(s)) {
          suggestions.add(cmd);
        }
      }
    }

    return suggestions;
  }

  private InterpreterResult processHelp(InterpreterResult.Code code, String additionalMessage) {
    final StringBuffer buffer = new StringBuffer();

    if (additionalMessage != null) {
      buffer.append(additionalMessage).append("\n");
    }

    buffer.append(HELP).append("\n");

    return new InterpreterResult(code, InterpreterResult.Type.TEXT, buffer.toString());
  }

  /**
   * Processes a "get" request.
   * 
   * @param urlItems Items of the URL
   * @return Result of the get request, it contains a JSON-formatted string
   */
  private InterpreterResult processGet(String[] urlItems) {

    if (urlItems.length != 3 
        || StringUtils.isEmpty(urlItems[0]) 
        || StringUtils.isEmpty(urlItems[1]) 
        || StringUtils.isEmpty(urlItems[2])) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
                                   "Bad URL (it should be /index/type/id)");
    }

    final GetResponse response = client
      .prepareGet(urlItems[0], urlItems[1], urlItems[2])
      .get();
    if (response.isExists()) {
      final String json = gson.toJson(response.getSource());

      return new InterpreterResult(
                    InterpreterResult.Code.SUCCESS,
                    InterpreterResult.Type.TEXT,
                    json);
    }
        
    return new InterpreterResult(InterpreterResult.Code.ERROR, "Document not found");
  }

  /**
   * Processes a "count" request.
   * 
   * @param urlItems Items of the URL
   * @param data May contains the JSON of the request
   * @return Result of the count request, it contains the total hits
   */
  private InterpreterResult processCount(String[] urlItems, String data) {

    if (urlItems.length > 2) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
                                   "Bad URL (it should be /index1,index2,.../type1,type2,...)");
    }

    final SearchResponse response = searchData(urlItems, data, 0);

    return new InterpreterResult(
      InterpreterResult.Code.SUCCESS,
      InterpreterResult.Type.TEXT,
      "" + response.getHits().getTotalHits());
  }

  /**
   * Processes a "search" request.
   * 
   * @param urlItems Items of the URL
   * @param data May contains the JSON of the request
   * @param size Limit of result set
   * @return Result of the search request, it contains a tab-formatted string of the matching hits
   */
  private InterpreterResult processSearch(String[] urlItems, String data, int size) {

    if (urlItems.length > 2) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
                                   "Bad URL (it should be /index1,index2,.../type1,type2,...)");
    }
        
    final SearchResponse response = searchData(urlItems, data, size);

    return buildResponseMessage(response);
  }

  /**
   * Processes a "index" request.
   * 
   * @param urlItems Items of the URL
   * @param data JSON to be indexed
   * @return Result of the index request, it contains the id of the document
   */
  private InterpreterResult processIndex(String[] urlItems, String data) {
        
    if (urlItems.length < 2 || urlItems.length > 3) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
                                   "Bad URL (it should be /index/type or /index/type/id)");
    }
        
    final IndexResponse response = client
      .prepareIndex(urlItems[0], urlItems[1], urlItems.length == 2 ? null : urlItems[2])
      .setSource(data)
      .get();

    return new InterpreterResult(
      InterpreterResult.Code.SUCCESS,
      InterpreterResult.Type.TEXT,
      response.getId());
  }

  /**
   * Processes a "delete" request.
   * 
   * @param urlItems Items of the URL
   * @return Result of the delete request, it contains the id of the deleted document
   */
  private InterpreterResult processDelete(String[] urlItems) {

    if (urlItems.length != 3 
        || StringUtils.isEmpty(urlItems[0]) 
        || StringUtils.isEmpty(urlItems[1]) 
        || StringUtils.isEmpty(urlItems[2])) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
                                   "Bad URL (it should be /index/type/id)");
    }

    final DeleteResponse response = client
      .prepareDelete(urlItems[0], urlItems[1], urlItems[2])
      .get();
        
    if (response.isFound()) {
      return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        response.getId());
    }
        
    return new InterpreterResult(InterpreterResult.Code.ERROR, "Document not found");
  }
    
  private SearchResponse searchData(String[] urlItems, String query, int size) {

    final SearchRequestBuilder reqBuilder = new SearchRequestBuilder(
      client, SearchAction.INSTANCE);
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
      }
      catch (JsonParseException e) {
        // This is not a JSON (or maybe not well formatted...)
        reqBuilder.setQuery(QueryBuilders.queryStringQuery(query).analyzeWildcard(true));
      }
    }

    reqBuilder.setSize(size);

    final SearchResponse response = reqBuilder.get();

    return response;
  }

  private InterpreterResult buildAggResponseMessage(Aggregations aggregations) {

    // Only the result of the first aggregation is returned
    //
    final Aggregation agg = aggregations.asList().get(0);
    InterpreterResult.Type resType = InterpreterResult.Type.TEXT;
    String resMsg = "";

    if (agg instanceof InternalMetricsAggregation) {
      resMsg = XContentHelper.toString((InternalMetricsAggregation) agg).toString();
    }
    else if (agg instanceof InternalSingleBucketAggregation) {
      resMsg = XContentHelper.toString((InternalSingleBucketAggregation) agg).toString();
    }
    else if (agg instanceof InternalMultiBucketAggregation) {
      final StringBuffer buffer = new StringBuffer("key\tdoc_count");

      final InternalMultiBucketAggregation multiBucketAgg = (InternalMultiBucketAggregation) agg;
      for (MultiBucketsAggregation.Bucket bucket : multiBucketAgg.getBuckets()) {
        buffer.append("\n")
          .append(bucket.getKeyAsString())
          .append("\t")
          .append(bucket.getDocCount());
      }

      resType = InterpreterResult.Type.TABLE;
      resMsg = buffer.toString();
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, resType, resMsg);
  }

  private String buildSearchHitsResponseMessage(SearchHit[] hits) {
        
    if (hits == null || hits.length == 0) {
      return "";
    }

    //First : get all the keys in order to build an ordered list of the values for each hit
    //
    final List<Map<String, Object>> flattenHits = new LinkedList<>();
    final Set<String> keys = new TreeSet<>();
    for (SearchHit hit : hits) {
      final String json = hit.getSourceAsString();
      final Map<String, Object> flattenMap = JsonFlattener.flattenAsMap(json);
      flattenHits.add(flattenMap);

      for (String key : flattenMap.keySet()) {
        keys.add(key);
      }
    }

    // Next : build the header of the table
    //
    final StringBuffer buffer = new StringBuffer();
    for (String key : keys) {
      buffer.append(key).append('\t');
    }
    buffer.replace(buffer.lastIndexOf("\t"), buffer.lastIndexOf("\t") + 1, "\n");

    // Finally : build the result by using the key set
    //
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

  private InterpreterResult buildResponseMessage(SearchResponse response) {

    final Aggregations aggregations = response.getAggregations();

    if (aggregations != null && aggregations.asList().size() > 0) {
      return buildAggResponseMessage(aggregations);
    }

    return new InterpreterResult(
      InterpreterResult.Code.SUCCESS,
      InterpreterResult.Type.TABLE,
      buildSearchHitsResponseMessage(response.getHits().getHits()));
  }
}
