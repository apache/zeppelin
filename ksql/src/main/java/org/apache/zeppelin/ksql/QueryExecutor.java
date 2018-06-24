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
package org.apache.zeppelin.ksql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class QueryExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryExecutor.class);

  static final String HTML_MAGIC = "%html \n";
  static final String TABLE_MAGIC = "%table ";

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  String queryEndpoint;
  String ksqlEndpoint;
  String statusEndpoint;

  private static final Map<KsqlQuery.QueryType, Function<String, InterpreterResult>> HANDLERS =
      new TreeMap();

  static {
    Function<String, InterpreterResult> func = s -> QueryExecutor.formatTables("tables", s);
    HANDLERS.put(KsqlQuery.QueryType.SHOW_TABLES, func);
    func = s -> QueryExecutor.formatTables("streams", s);
    HANDLERS.put(KsqlQuery.QueryType.SHOW_STREAMS, func);
    func = QueryExecutor::formatProperties;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_PROPS, func);
    func = QueryExecutor::formatTopics;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_TOPICS, func);
  }

  QueryExecutor(final String url) {
    LOGGER.info("Initializing query executor for URL: {}", url);
    // TODO(alex): parse URL, normalize it, and then append endpoints...
    queryEndpoint = url + "/query";
    ksqlEndpoint = url + "/ksql";
    statusEndpoint = url + "/status";
  }

  public InterpreterResult execute(KsqlQuery query) {
    if (query.isUnsupported()) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Query '" + query.getQuery() + "' isn't supported yet...");
    }
    InterpreterResult result = null;
    try {
      KsqlQuery.QueryType queryType = query.getType();
      final String endpoint;
      if (queryType == KsqlQuery.QueryType.SELECT) {
        endpoint = queryEndpoint;
      } else {
        endpoint = ksqlEndpoint;
      }
      // make a call to REST API & get answer...
      CloseableHttpClient httpclient = HttpClients.createDefault();
      HttpPost httpPost = new HttpPost(endpoint);
      // TODO(alex): use correct JSON generation
      StringEntity entity = new StringEntity("{\"ksql\":\"" + query.getQuery() + "\"}");
      httpPost.setEntity(entity);
      httpPost.addHeader("Content-Type", "application/json");

      CloseableHttpResponse response = httpclient.execute(httpPost);
      String body = "{}";
      try {
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != 200) {
          return new InterpreterResult(InterpreterResult.Code.ERROR,
            "Non-200 Answer from KSQL server: " + status.getStatusCode() +
              ". " + status.getReasonPhrase());
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        response.getEntity().writeTo(os);
        body = os.toString("UTF-8");
      } finally {
        response.close();
      }
      LOGGER.debug("Got answer from server: {}", body);

      // handle results
      Function<String, InterpreterResult> handler = HANDLERS.get(queryType);
      if (handler != null) {
        result = handler.apply(body);
      } else {
        result = new InterpreterResult(InterpreterResult.Code.ERROR,
          "No handler for query type " + queryType.name());
      }
    } catch (Exception ex) {
      result = new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
      LOGGER.error("Exception: ", ex);
    }
    return result;
  }

  public static InterpreterResult formatTables(final String type, final String payload) {
    Map<String, Object> m;
    try {
      List<Object> results = OBJECT_MAPPER.readValue(payload, new TypeReference<List<Object>>() {
      });
      if (results.size() < 1) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "No data returned!");
      }
      m = (Map<String, Object>) (results.get(0));
      m = (Map<String, Object>) (m.get(type));
      if (m == null) {
        return new InterpreterResult(InterpreterResult.Code.ERROR,
          "No " + type + " section in result!");
      }
    } catch (IOException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    List<Object> values = (List<Object>) (m.get(type));
    if (values == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "No " + type + " section in result!");
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Name\tStream\tFormat\n");
    for (Object obj : values) {
      Map<String, String> entry = (Map<String, String>) obj;
      sb.append(entry.getOrDefault("name", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("stream", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("format", ""));
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatProperties(final String payload) {
    Map<String, Object> m;
    try {
      List<Object> results = OBJECT_MAPPER.readValue(payload, new TypeReference<List<Object>>() {
      });
      if (results.size() < 1) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "No data returned!");
      }
      m = (Map<String, Object>) (results.get(0));
      m = (Map<String, Object>) (m.get("properties"));
      if (m == null) {
        return new InterpreterResult(InterpreterResult.Code.ERROR,
          "No 'properties' section in result!");
      }
    } catch (IOException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    Map<String, Object> values = (Map<String, Object>) (m.get("properties"));
    if (values == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "No 'properties' section in result!");
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Property\tValue\n");
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      sb.append(entry.getKey());
      sb.append('\t');
      sb.append(entry.getValue());
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatTopics(final String payload) {
    Map<String, Object> m;
    try {
      List<Object> results = OBJECT_MAPPER.readValue(payload, new TypeReference<List<Object>>() {
      });
      if (results.size() < 1) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "No data returned!");
      }
      m = (Map<String, Object>) (results.get(0));
      m = (Map<String, Object>) (m.get("kafka_topics"));
      if (m == null) {
        return new InterpreterResult(InterpreterResult.Code.ERROR,
          "No kafka_topics section in result!");
      }
    } catch (IOException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    List<Object> values = (List<Object>) (m.get("topics"));
    if (values == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "No topics section in result!");
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Name\tRegistered?\tPartition count\tReplica Information");
    sb.append("\tConsumer count\tConsumer group count\n");
    for (Object obj : values) {
      Map<String, Object> entry = (Map<String, Object>) obj;
      sb.append(entry.getOrDefault("name", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("registered", false));
      sb.append('\t');
      sb.append(entry.getOrDefault("partitionCount", 0));
      sb.append('\t');
      sb.append(entry.getOrDefault("replicaInfo", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("consumerCount", 0));
      sb.append('\t');
      sb.append(entry.getOrDefault("consumerGroupCount", ""));
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

}
