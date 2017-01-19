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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.elasticsearch.ElasticsearchInterpreter;
import org.apache.zeppelin.elasticsearch.action.ActionException;
import org.apache.zeppelin.elasticsearch.action.ActionResponse;
import org.apache.zeppelin.elasticsearch.action.AggWrapper;
import org.apache.zeppelin.elasticsearch.action.AggWrapper.AggregationType;
import org.apache.zeppelin.elasticsearch.action.HitWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

/**
 * Elasticsearch client using the HTTP API.
 */
public class HttpBasedClient implements ElasticsearchClient {

  private static final String QUERY_STRING_TEMPLATE =
      "{ \"query\": { \"query_string\": { \"query\": \"_Q_\", \"analyze_wildcard\": \"true\" } } }";

  private final String host;
  private final int port;
  private final String username;
  private final String password;

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public HttpBasedClient(Properties props) {
    this.host = props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_HOST);
    this.port = Integer.parseInt(props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_PORT));
    this.username = props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_BASIC_AUTH_USERNAME);
    this.password = props.getProperty(ElasticsearchInterpreter.ELASTICSEARCH_BASIC_AUTH_PASSWORD);
  }

  private boolean isSucceeded(HttpResponse response) {
    return response.getStatus() >= 200 && response.getStatus() < 300;
  }

  private JSONObject getParentField(JSONObject parent, String[] fields) {
    JSONObject obj = parent;
    for (int i = 0; i < fields.length - 1; i++) {
      obj = obj.getJSONObject(fields[i]);
    }
    return obj;
  }

  private JSONArray getFieldAsArray(JSONObject obj, String field) {
    final String[] fields = field.split("/");
    final JSONObject parent = getParentField(obj, fields);
    return parent.getJSONArray(fields[fields.length - 1]);
  }

  private String getFieldAsString(HttpResponse<JsonNode> response, String field) {
    return getFieldAsString(response.getBody(), field);
  }

  private String getFieldAsString(JsonNode json, String field) {
    return json.getObject().get(field).toString();
  }

  private long getFieldAsLong(HttpResponse<JsonNode> response, String field) {
    final String[] fields = field.split("/");
    final JSONObject obj = getParentField(response.getBody().getObject(), fields);
    return obj.getLong(fields[fields.length - 1]);
  }

  private String getUrl(String index, String type, String id, boolean useSearch) {
    try {
      final StringBuilder buffer = new StringBuilder();
      buffer.append("http://").append(host).append(":").append(port).append("/");
      if (StringUtils.isNotEmpty(index)) {
        buffer.append(index);

        if (StringUtils.isNotEmpty(type)) {
          buffer.append("/").append(type);

          if (StringUtils.isNotEmpty(id)) {
            if (useSearch) {
              final String encodedId = URLEncoder.encode(id, "UTF-8");
              if (id.equals(encodedId)) {
                // No difference, use directly the id
                buffer.append("/").append(id);
              }
              else {
                // There are differences: to avoid problems with some special characters
                // such as / and # in id, use a "terms" query
                buffer.append("/_search?source=")
                  .append(URLEncoder
                      .encode("{\"query\":{\"terms\":{\"_id\":[\"" + id + "\"]}}}", "UTF-8"));
              }
            }
            else {
              buffer.append("/").append(id);
            }
          }
        }
      }
      return buffer.toString();
    }
    catch (final UnsupportedEncodingException e) {
      throw new ActionException(e);
    }
  }

  private String getUrl(String[] indices, String[] types) {
    final String inds = indices == null ? null : Joiner.on(",").join(indices);
    final String typs = types == null ? null : Joiner.on(",").join(types);
    return getUrl(inds, typs, null, false);
  }

  @Override
  public ActionResponse get(String index, String type, String id) {
    ActionResponse response = null;
    try {
      final HttpRequest request = Unirest.get(getUrl(index, type, id, true));
      if (StringUtils.isNotEmpty(username)) {
        request.basicAuth(username, password);
      }

      final HttpResponse<String> result = request.asString();
      final boolean isSucceeded = isSucceeded(result);

      if (isSucceeded) {
        final JsonNode body = new JsonNode(result.getBody());
        if (body.getObject().has("_index")) {
          response = new ActionResponse()
              .succeeded(true)
              .hit(new HitWrapper(
                  getFieldAsString(body, "_index"),
                  getFieldAsString(body, "_type"),
                  getFieldAsString(body, "_id"),
                  getFieldAsString(body, "_source")));
        }
        else {
          final JSONArray hits = getFieldAsArray(body.getObject(), "hits/hits");
          final JSONObject hit = (JSONObject) hits.iterator().next();
          response = new ActionResponse()
              .succeeded(true)
              .hit(new HitWrapper(
                  hit.getString("_index"),
                  hit.getString("_type"),
                  hit.getString("_id"),
                  hit.opt("_source").toString()));
        }
      }
      else {
        if (result.getStatus() == 404) {
          response = new ActionResponse()
              .succeeded(false);
        }
        else {
          throw new ActionException(result.getBody());
        }
      }
    }
    catch (final UnirestException e) {
      throw new ActionException(e);
    }
    return response;
  }

  @Override
  public ActionResponse delete(String index, String type, String id) {
    ActionResponse response = null;
    try {
      final HttpRequest request = Unirest.delete(getUrl(index, type, id, true));
      if (StringUtils.isNotEmpty(username)) {
        request.basicAuth(username, password);
      }

      final HttpResponse<String> result = request.asString();
      final boolean isSucceeded = isSucceeded(result);

      if (isSucceeded) {
        final JsonNode body = new JsonNode(result.getBody());
        response = new ActionResponse()
            .succeeded(true)
            .hit(new HitWrapper(
                getFieldAsString(body, "_index"),
                getFieldAsString(body, "_type"),
                getFieldAsString(body, "_id"),
                null));
      }
      else {
        throw new ActionException(result.getBody());
      }
    }
    catch (final UnirestException e) {
      throw new ActionException(e);
    }
    return response;
  }

  @Override
  public ActionResponse index(String index, String type, String id, String data) {
    ActionResponse response = null;
    try {
      HttpRequestWithBody request = null;
      if (StringUtils.isEmpty(id)) {
        request = Unirest.post(getUrl(index, type, id, false));
      }
      else {
        request = Unirest.put(getUrl(index, type, id, false));
      }
      request
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .body(data).getHttpRequest();
      if (StringUtils.isNotEmpty(username)) {
        request.basicAuth(username, password);
      }

      final HttpResponse<JsonNode> result = request.asJson();
      final boolean isSucceeded = isSucceeded(result);

      if (isSucceeded) {
        response = new ActionResponse()
            .succeeded(true)
            .hit(new HitWrapper(
                getFieldAsString(result, "_index"),
                getFieldAsString(result, "_type"),
                getFieldAsString(result, "_id"),
                null));
      }
      else {
        throw new ActionException(result.getBody().toString());
      }
    }
    catch (final UnirestException e) {
      throw new ActionException(e);
    }
    return response;
  }

  @Override
  public ActionResponse search(String[] indices, String[] types, String query, int size) {
    ActionResponse response = null;

    if (!StringUtils.isEmpty(query)) {
      // The query can be either JSON-formatted, nor a Lucene query
      // So, try to parse as a JSON => if there is an error, consider the query a Lucene one
      try {
        gson.fromJson(query, Map.class);
      }
      catch (final JsonParseException e) {
        // This is not a JSON (or maybe not well formatted...)
        query = QUERY_STRING_TEMPLATE.replace("_Q_", query);
      }
    }

    try {
      final HttpRequestWithBody request = Unirest
          .post(getUrl(indices, types) + "/_search?size=" + size)
          .header("Content-Type", "application/json");

      if (StringUtils.isNoneEmpty(query)) {
        request.header("Accept", "application/json").body(query);
      }

      if (StringUtils.isNotEmpty(username)) {
        request.basicAuth(username, password);
      }

      final HttpResponse<JsonNode> result = request.asJson();
      final JSONObject body = result.getBody() != null ? result.getBody().getObject() : null;

      if (isSucceeded(result)) {
        final long total = getFieldAsLong(result, "hits/total");

        response = new ActionResponse()
            .succeeded(true)
            .totalHits(total);

        if (containsAggs(result)) {
          JSONObject aggregationsMap = body.getJSONObject("aggregations");
          if (aggregationsMap == null) {
            aggregationsMap = body.getJSONObject("aggs");
          }

          for (final String key: aggregationsMap.keySet()) {
            final JSONObject aggResult = aggregationsMap.getJSONObject(key);
            if (aggResult.has("buckets")) {
              // Multi-bucket aggregations
              final Iterator<Object> buckets = aggResult.getJSONArray("buckets").iterator();
              while (buckets.hasNext()) {
                response.addAggregation(
                    new AggWrapper(AggregationType.MULTI_BUCKETS, buckets.next().toString()));
              }
            }
            else {
              response.addAggregation(
                  new AggWrapper(AggregationType.SIMPLE, aggregationsMap.toString()));
            }
            break; // Keep only one aggregation
          }
        }
        else if (size > 0 && total > 0) {
          final JSONArray hits = getFieldAsArray(body, "hits/hits");
          final Iterator<Object> iter = hits.iterator();

          while (iter.hasNext()) {
            final JSONObject hit = (JSONObject) iter.next();
            final Object data =
                hit.opt("_source") != null ? hit.opt("_source") : hit.opt("fields");
            response.addHit(new HitWrapper(
                hit.getString("_index"),
                hit.getString("_type"),
                hit.getString("_id"),
                data.toString()));
          }
        }
      }
      else {
        throw new ActionException(body.get("error").toString());
      }
    }
    catch (final UnirestException e) {
      throw new ActionException(e);
    }

    return response;
  }

  private boolean containsAggs(HttpResponse<JsonNode> result) {
    return result.getBody() != null &&
        (result.getBody().getObject().has("aggregations") ||
            result.getBody().getObject().has("aggs"));
  }

  @Override
  public void close() {
  }

  @Override
  public String toString() {
    return "HttpBasedClient [host=" + host + ", port=" + port + ", username=" + username + "]";
  }
}
