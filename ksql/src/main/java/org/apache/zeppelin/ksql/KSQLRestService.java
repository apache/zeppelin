/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.ksql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KSQLRestService {

  private static final String KSQL_ENDPOINT = "%s/ksql";
  private static final String QUERY_ENDPOINT = "%s/query";

  private static final String KSQL_V1_CONTENT_TYPE = "application/vnd.ksql.v1+json; charset=utf-8";

  private static final List<String> KSQL_COMMON_FIELDS = Arrays
      .asList("statementText", "warnings", "@type");
  private static final String KSQL_URL = "ksql.url";

  private static final ObjectMapper json = new ObjectMapper();

  private final String ksqlUrl;
  private final String queryUrl;
  private final String baseUrl;
  private final Map<String, String> streamsProperties;

  private final Map<String, BasicKSQLHttpClient> clientCache;

  public KSQLRestService(Map<String, String> props) {
    baseUrl = Objects.requireNonNull(props.get(KSQL_URL), KSQL_URL).toString();
    ksqlUrl = String.format(KSQL_ENDPOINT, baseUrl);
    queryUrl = String.format(QUERY_ENDPOINT, baseUrl);
    clientCache = new ConcurrentHashMap<>();
    this.streamsProperties = props.entrySet().stream()
            .filter(e -> e.getKey().startsWith("ksql.") && !e.getKey().equals(KSQL_URL))
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
  }


  public void executeQuery(final String paragraphId, final String query,
               final Consumer<KSQLResponse> callback) throws IOException {
    KSQLRequest request = new KSQLRequest(query, streamsProperties);
    if (isSelect(request)) {
      executeSelect(paragraphId, callback, request);
    } else if (isPrint(request)) {
      executePrint(paragraphId, callback, request);
    } else {
      executeKSQL(paragraphId, callback, request);
    }
  }

  private void executeKSQL(String paragraphId, Consumer<KSQLResponse> callback,
        KSQLRequest request) throws IOException {
    try (BasicKSQLHttpClient client = createNewClient(paragraphId, request, ksqlUrl)) {
      List<Map<String, Object>> queryResponse = json.readValue(client.connect(), List.class);
      queryResponse.stream()
              .map(map -> excludeKSQLCommonFields(map))
              .flatMap(map -> map.entrySet().stream()
                  .filter(e -> e.getValue() instanceof List)
                  .flatMap(e -> ((List<Map<String, Object>>) e.getValue()).stream()))
              .map(KSQLResponse::new)
              .forEach(callback::accept);
      queryResponse.stream()
              .map(map -> excludeKSQLCommonFields(map))
              .flatMap(map -> map.entrySet().stream()
                      .filter(e -> e.getValue() instanceof Map)
                      .map(e -> (Map<String, Object>) e.getValue()))
              .map(KSQLResponse::new)
              .forEach(callback::accept);
    }
  }

  private Map<String, Object> excludeKSQLCommonFields(Map<String, Object> map) {
    return map.entrySet().stream()
        .filter(e -> !KSQL_COMMON_FIELDS.contains(e.getKey()))
        .collect(Collectors
            .toMap(e -> e.getKey(), e -> e.getValue()));
  }

  private BasicKSQLHttpClient createNewClient(String paragraphId, KSQLRequest request,
        String url) throws IOException {
    BasicKSQLHttpClient client = new BasicKSQLHttpClient.Builder()
            .withUrl(url)
            .withJson(json.writeValueAsString(request))
            .withType("POST")
            .withHeader("Content-type", KSQL_V1_CONTENT_TYPE)
            .build();
    BasicKSQLHttpClient oldClient = clientCache.put(paragraphId, client);
    if (oldClient != null) {
      oldClient.close();
    }
    return client;
  }

  private void executeSelect(String paragraphId, Consumer<KSQLResponse> callback,
        KSQLRequest request) throws IOException {
    List<String> fieldNames = getFields(request);
    if (fieldNames.isEmpty()) {
      throw new RuntimeException("Field are empty");
    }
    try (BasicKSQLHttpClient client = createNewClient(paragraphId, request, queryUrl)) {
      client.connectAsync(new BasicKSQLHttpClient.BasicHTTPClientResponse() {
        @Override
        public void onMessage(int status, String message) {
          try {
            Map<String, Object> queryResponse = json.readValue(message, LinkedHashMap.class);
            KSQLResponse resp = new KSQLResponse(fieldNames, queryResponse);
            callback.accept(resp);
            if (resp.isTerminal() || StringUtils.isNotBlank(resp.getErrorMessage())
                    || StringUtils.isNotBlank(resp.getFinalMessage())) {
              client.close();
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public void onError(int status, String message) {
          try {
            KSQLResponse resp = new KSQLResponse(Collections.singletonMap("error", message));
            callback.accept(resp);
            client.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
  }

  private void executePrint(String paragraphId, Consumer<KSQLResponse> callback,
                             KSQLRequest request) throws IOException {
    try (BasicKSQLHttpClient client = createNewClient(paragraphId, request, queryUrl)) {
      client.connectAsync(new BasicKSQLHttpClient.BasicHTTPClientResponse() {
        @Override
        public void onMessage(int status, String message) {
          if (message.toUpperCase().startsWith("FORMAT:")) {
            return;
          }
          List<String> elements = Arrays.asList(message.split(","));
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("timestamp", elements.get(0));
          row.put("offset", elements.get(1));
          row.put("record", String.join("", elements.subList(2, elements.size())));
          KSQLResponse resp = new KSQLResponse(row);
          callback.accept(resp);
        }

        @Override
        public void onError(int status, String message) {
          try {
            KSQLResponse resp = new KSQLResponse(Collections.singletonMap("error", message));
            callback.accept(resp);
            client.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
  }

  private boolean isSelect(KSQLRequest request) {
    return request.getKsql().toUpperCase().startsWith("SELECT");
  }

  private boolean isPrint(KSQLRequest request) {
    return request.getKsql().toUpperCase().startsWith("PRINT");
  }

  public void closeClient(final String paragraphId) throws IOException {
    BasicKSQLHttpClient toClose = clientCache.remove(paragraphId);
    if (toClose != null) {
      toClose.close();
    }
  }

  private List<String> getFields(KSQLRequest request) throws IOException {
    return getFields(request, false);
  }

  private List<String> getFields(KSQLRequest request, boolean tryCoerce) throws IOException {
    if (tryCoerce) {
      /*
       * this because a query like
       * `EXPLAIN SELECT * FROM ORDERS WHERE ADDRESS->STATE = 'New York' LIMIT 10;`
       * fails with the message `Column STATE cannot be resolved`
       * so we try to coerce the field resolution
       */
      String query = request.getKsql()
          .substring(0, request.getKsql().toUpperCase().indexOf("WHERE"));
      request = new KSQLRequest(query, request.getStreamsProperties());
    }
    try (BasicKSQLHttpClient client = new BasicKSQLHttpClient.Builder()
        .withUrl(ksqlUrl)
        .withJson(json.writeValueAsString(request.toExplainRequest()))
        .withType("POST")
        .withHeader("Content-type", KSQL_V1_CONTENT_TYPE)
        .build()) {
      List<Map<String, Object>> explainResponseList = json.readValue(client.connect(), List.class);
      Map<String, Object> explainResponse = explainResponseList.get(0);
      Map<String, Object> queryDescription = (Map<String, Object>) explainResponse
          .getOrDefault("queryDescription", Collections.emptyMap());
      List<Map<String, Object>> fields = (List<Map<String, Object>>) queryDescription
          .getOrDefault("fields", Collections.emptyList());
      return fields.stream()
          .map(elem -> elem.getOrDefault("name", "").toString())
          .filter(s -> !s.isEmpty())
          .collect(Collectors.toList());
    } catch (IOException e) {
      if (!tryCoerce) {
        return getFields(request, true);
      } else {
        throw e;
      }
    }
  }


  public void close() {
    Set<String> keys = clientCache.keySet();
    keys.forEach(key -> {
      try {
        closeClient(key);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }
}
