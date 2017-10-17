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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.elasticsearch.action.ActionResponse;
import org.apache.zeppelin.elasticsearch.action.AggWrapper;
import org.apache.zeppelin.elasticsearch.action.HitWrapper;
import org.apache.zeppelin.elasticsearch.client.ElasticsearchClient;
import org.apache.zeppelin.elasticsearch.client.HttpBasedClient;
import org.apache.zeppelin.elasticsearch.client.TransportBasedClient;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.InternalSingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalMetricsAggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.wnameless.json.flattener.JsonFlattener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;


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
      + "  - index /index/type/id <json-formatted document>\n"
      + "    . the id can be omitted, elasticsearch will generate one";

  protected static final List<String> COMMANDS = Arrays.asList(
      "count", "delete", "get", "help", "index", "search");

  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("\\[\\\\\"(.+)\\\\\"\\](.*)");


  public static final String ELASTICSEARCH_HOST = "elasticsearch.host";
  public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
  public static final String ELASTICSEARCH_CLIENT_TYPE = "elasticsearch.client.type";
  public static final String ELASTICSEARCH_CLUSTER_NAME = "elasticsearch.cluster.name";
  public static final String ELASTICSEARCH_RESULT_SIZE = "elasticsearch.result.size";
  public static final String ELASTICSEARCH_BASIC_AUTH_USERNAME = "elasticsearch.basicauth.username";
  public static final String ELASTICSEARCH_BASIC_AUTH_PASSWORD = "elasticsearch.basicauth.password";

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private ElasticsearchClient elsClient;
  private int resultSize = 10;

  public ElasticsearchInterpreter(Properties property) {
    super(property);

  }

  @Override
  public void open() {
    logger.info("Properties: {}", getProperties());

    String clientType = getProperty(ELASTICSEARCH_CLIENT_TYPE);
    clientType = clientType == null ? null : clientType.toLowerCase();

    try {
      this.resultSize = Integer.parseInt(getProperty(ELASTICSEARCH_RESULT_SIZE));
    }
    catch (final NumberFormatException e) {
      this.resultSize = 10;
      logger.error("Unable to parse " + ELASTICSEARCH_RESULT_SIZE + " : " +
          getProperty(ELASTICSEARCH_RESULT_SIZE), e);
    }

    try {
      if (StringUtils.isEmpty(clientType) || "transport".equals(clientType)) {
        elsClient = new TransportBasedClient(getProperties());
      }
      else if ("http".equals(clientType)) {
        elsClient = new HttpBasedClient(getProperties());
      }
      else {
        logger.error("Unknown type of Elasticsearch client: " + clientType);
      }
    }
    catch (final IOException e) {
      logger.error("Open connection with Elasticsearch", e);
    }
  }

  @Override
  public void close() {
    if (elsClient != null) {
      elsClient.close();
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext interpreterContext) {
    logger.info("Run Elasticsearch command '" + cmd + "'");

    if (StringUtils.isEmpty(cmd) || StringUtils.isEmpty(cmd.trim())) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    int currentResultSize = resultSize;

    if (elsClient == null) {
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
        return processGet(urlItems, interpreterContext);
      }
      else if ("count".equalsIgnoreCase(method)) {
        return processCount(urlItems, data, interpreterContext);
      }
      else if ("search".equalsIgnoreCase(method)) {
        return processSearch(urlItems, data, currentResultSize, interpreterContext);
      }
      else if ("index".equalsIgnoreCase(method)) {
        return processIndex(urlItems, data);
      }
      else if ("delete".equalsIgnoreCase(method)) {
        return processDelete(urlItems);
      }

      return processHelp(InterpreterResult.Code.ERROR, "Unknown command");
    }
    catch (final Exception e) {
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
  public List<InterpreterCompletion> completion(String s, int i,
      InterpreterContext interpreterContext) {
    final List suggestions = new ArrayList<>();

    for (final String cmd : COMMANDS) {
      if (cmd.toLowerCase().contains(s)) {
        suggestions.add(new InterpreterCompletion(cmd, cmd, CompletionType.command.name()));
      }
    }
    return suggestions;
  }

  private void addAngularObject(InterpreterContext interpreterContext, String prefix, Object obj) {
    interpreterContext.getAngularObjectRegistry().add(
        prefix + "_" + interpreterContext.getParagraphId().replace("-", "_"),
        obj, null, null);
  }

  private String[] getIndexTypeId(String[] urlItems) {

    if (urlItems.length < 3) {
      return null;
    }

    final String index = urlItems[0];
    final String type = urlItems[1];
    final String id = StringUtils.join(Arrays.copyOfRange(urlItems, 2, urlItems.length), '/');

    if (StringUtils.isEmpty(index)
        || StringUtils.isEmpty(type)
        || StringUtils.isEmpty(id)) {
      return null;
    }

    return new String[] { index, type, id };
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
   * @param interpreterContext Instance of the context
   * @return Result of the get request, it contains a JSON-formatted string
   */
  private InterpreterResult processGet(String[] urlItems, InterpreterContext interpreterContext) {

    final String[] indexTypeId = getIndexTypeId(urlItems);

    if (indexTypeId == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Bad URL (it should be /index/type/id)");
    }

    final ActionResponse response = elsClient.get(indexTypeId[0], indexTypeId[1], indexTypeId[2]);

    if (response.isSucceeded()) {
      final JsonObject json = response.getHit().getSourceAsJsonObject();
      final String jsonStr = gson.toJson(json);

      addAngularObject(interpreterContext, "get", json);

      return new InterpreterResult(
          InterpreterResult.Code.SUCCESS,
          InterpreterResult.Type.TEXT,
          jsonStr);
    }

    return new InterpreterResult(InterpreterResult.Code.ERROR, "Document not found");
  }

  /**
   * Processes a "count" request.
   *
   * @param urlItems Items of the URL
   * @param data May contains the JSON of the request
   * @param interpreterContext Instance of the context
   * @return Result of the count request, it contains the total hits
   */
  private InterpreterResult processCount(String[] urlItems, String data,
      InterpreterContext interpreterContext) {

    if (urlItems.length > 2) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Bad URL (it should be /index1,index2,.../type1,type2,...)");
    }

    final ActionResponse response = searchData(urlItems, data, 0);

    addAngularObject(interpreterContext, "count", response.getTotalHits());

    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        "" + response.getTotalHits());
  }

  /**
   * Processes a "search" request.
   *
   * @param urlItems Items of the URL
   * @param data May contains the JSON of the request
   * @param size Limit of result set
   * @param interpreterContext Instance of the context
   * @return Result of the search request, it contains a tab-formatted string of the matching hits
   */
  private InterpreterResult processSearch(String[] urlItems, String data, int size,
      InterpreterContext interpreterContext) {

    if (urlItems.length > 2) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Bad URL (it should be /index1,index2,.../type1,type2,...)");
    }

    final ActionResponse response = searchData(urlItems, data, size);

    addAngularObject(interpreterContext, "search",
        (response.getAggregations() != null && response.getAggregations().size() > 0) ?
            response.getAggregations() : response.getHits());

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

    final ActionResponse response = elsClient.index(
        urlItems[0], urlItems[1], urlItems.length == 2 ? null : urlItems[2], data);

    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TEXT,
        response.getHit().getId());
  }

  /**
   * Processes a "delete" request.
   *
   * @param urlItems Items of the URL
   * @return Result of the delete request, it contains the id of the deleted document
   */
  private InterpreterResult processDelete(String[] urlItems) {

    final String[] indexTypeId = getIndexTypeId(urlItems);

    if (indexTypeId == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "Bad URL (it should be /index/type/id)");
    }

    final ActionResponse response =
        elsClient.delete(indexTypeId[0], indexTypeId[1], indexTypeId[2]);

    if (response.isSucceeded()) {
      return new InterpreterResult(
          InterpreterResult.Code.SUCCESS,
          InterpreterResult.Type.TEXT,
          response.getHit().getId());
    }

    return new InterpreterResult(InterpreterResult.Code.ERROR, "Document not found");
  }

  private ActionResponse searchData(String[] urlItems, String query, int size) {

    String[] indices = null;
    String[] types = null;

    if (urlItems.length >= 1) {
      indices = StringUtils.split(urlItems[0], ",");
    }
    if (urlItems.length > 1) {
      types = StringUtils.split(urlItems[1], ",");
    }

    return elsClient.search(indices, types, query, size);
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
      final Set<String> headerKeys = new HashSet<>();
      final List<Map<String, Object>> buckets = new LinkedList<>();
      final InternalMultiBucketAggregation multiBucketAgg = (InternalMultiBucketAggregation) agg;

      for (final MultiBucketsAggregation.Bucket bucket : multiBucketAgg.getBuckets()) {
        try {
          final XContentBuilder builder = XContentFactory.jsonBuilder();
          bucket.toXContent(builder, null);
          final Map<String, Object> bucketMap = JsonFlattener.flattenAsMap(builder.string());
          headerKeys.addAll(bucketMap.keySet());
          buckets.add(bucketMap);
        }
        catch (final IOException e) {
          logger.error("Processing bucket: " + e.getMessage(), e);
        }
      }

      final StringBuffer buffer = new StringBuffer();
      final String[] keys = headerKeys.toArray(new String[0]);
      for (final String key: keys) {
        buffer.append("\t" + key);
      }
      buffer.deleteCharAt(0);

      for (final Map<String, Object> bucket : buckets) {
        buffer.append("\n");

        for (final String key: keys) {
          buffer.append(bucket.get(key)).append("\t");
        }
        buffer.deleteCharAt(buffer.length() - 1);
      }

      resType = InterpreterResult.Type.TABLE;
      resMsg = buffer.toString();
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, resType, resMsg);
  }

  private InterpreterResult buildAggResponseMessage(List<AggWrapper> aggregations) {

    final InterpreterResult.Type resType = InterpreterResult.Type.TABLE;
    String resMsg = "";

    final Set<String> headerKeys = new HashSet<>();
    final List<Map<String, Object>> buckets = new LinkedList<>();

    for (final AggWrapper aggregation: aggregations) {
      final Map<String, Object> bucketMap = JsonFlattener.flattenAsMap(aggregation.getResult());
      headerKeys.addAll(bucketMap.keySet());
      buckets.add(bucketMap);
    }

    final StringBuffer buffer = new StringBuffer();
    final String[] keys = headerKeys.toArray(new String[0]);
    for (final String key: keys) {
      buffer.append("\t" + key);
    }
    buffer.deleteCharAt(0);

    for (final Map<String, Object> bucket : buckets) {
      buffer.append("\n");

      for (final String key: keys) {
        buffer.append(bucket.get(key)).append("\t");
      }
      buffer.deleteCharAt(buffer.length() - 1);
    }

    resMsg = buffer.toString();

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, resType, resMsg);
  }

  private String buildSearchHitsResponseMessage(ActionResponse response) {

    if (response.getHits() == null || response.getHits().size() == 0) {
      return "";
    }

    //First : get all the keys in order to build an ordered list of the values for each hit
    //
    final List<Map<String, Object>> flattenHits = new LinkedList<>();
    final Set<String> keys = new TreeSet<>();
    for (final HitWrapper hit : response.getHits()) {

      final String json = hit.getSourceAsString();

      final Map<String, Object> flattenJsonMap = JsonFlattener.flattenAsMap(json);
      final Map<String, Object> flattenMap = new HashMap<>();
      for (final Iterator<String> iter = flattenJsonMap.keySet().iterator(); iter.hasNext(); ) {
        // Replace keys that match a format like that : [\"keyname\"][0]
        final String fieldName = iter.next();
        final Matcher fieldNameMatcher = FIELD_NAME_PATTERN.matcher(fieldName);
        if (fieldNameMatcher.matches()) {
          flattenMap.put(fieldNameMatcher.group(1) + fieldNameMatcher.group(2),
              flattenJsonMap.get(fieldName));
        }
        else {
          flattenMap.put(fieldName, flattenJsonMap.get(fieldName));
        }
      }
      flattenHits.add(flattenMap);

      for (final String key : flattenMap.keySet()) {
        keys.add(key);
      }
    }

    // Next : build the header of the table
    //
    final StringBuffer buffer = new StringBuffer();
    for (final String key : keys) {
      buffer.append(key).append('\t');
    }
    buffer.replace(buffer.lastIndexOf("\t"), buffer.lastIndexOf("\t") + 1, "\n");

    // Finally : build the result by using the key set
    //
    for (final Map<String, Object> hit : flattenHits) {
      for (final String key : keys) {
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

  private InterpreterResult buildResponseMessage(ActionResponse response) {

    final List<AggWrapper> aggregations = response.getAggregations();

    if (aggregations != null && aggregations.size() > 0) {
      return buildAggResponseMessage(aggregations);
    }

    return new InterpreterResult(
        InterpreterResult.Code.SUCCESS,
        InterpreterResult.Type.TABLE,
        buildSearchHitsResponseMessage(response));
  }
}
