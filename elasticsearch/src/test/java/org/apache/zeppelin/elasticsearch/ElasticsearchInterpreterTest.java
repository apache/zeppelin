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

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ElasticsearchInterpreterTest {

  private static Client elsClient;
  private static Node elsNode;
  private static ElasticsearchInterpreter interpreter;

  private static final String[] METHODS = { "GET", "PUT", "DELETE", "POST" };
  private static final int[] STATUS = { 200, 404, 500, 403 };

  private static final String ELS_CLUSTER_NAME = "zeppelin-elasticsearch-interpreter-test";
  private static final String ELS_HOST = "localhost";
  private static final String ELS_TRANSPORT_PORT = "10300";
  private static final String ELS_HTTP_PORT = "10200";
  private static final String ELS_PATH = "/tmp/els";


  @BeforeClass
  public static void populate() throws IOException {

    final Settings settings = Settings.settingsBuilder()
      .put("cluster.name", ELS_CLUSTER_NAME)
      .put("network.host", ELS_HOST)
      .put("http.port", ELS_HTTP_PORT)
      .put("transport.tcp.port", ELS_TRANSPORT_PORT)
      .put("path.home", ELS_PATH)
      .build();

    elsNode = NodeBuilder.nodeBuilder().settings(settings).node();
    elsClient = elsNode.client();

    elsClient.admin().indices().prepareCreate("logs")
      .addMapping("http", jsonBuilder()
        .startObject().startObject("http").startObject("properties")
          .startObject("content_length")
            .field("type", "integer")
          .endObject()
        .endObject().endObject().endObject()).get();

    for (int i = 0; i < 50; i++) {
      elsClient.prepareIndex("logs", "http", "" + i)
        .setRefresh(true)
        .setSource(jsonBuilder()
          .startObject()
            .field("date", new Date())
            .startObject("request")
              .field("method", METHODS[RandomUtils.nextInt(METHODS.length)])
              .field("url", "/zeppelin/" + UUID.randomUUID().toString())
              .field("headers", Arrays.asList("Accept: *.*", "Host: apache.org"))
            .endObject()
            .field("status", STATUS[RandomUtils.nextInt(STATUS.length)])
            .field("content_length", RandomUtils.nextInt(2000))
          )
        .get();
    }

    final Properties props = new Properties();
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_HOST, ELS_HOST);
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_PORT, ELS_TRANSPORT_PORT);
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_CLUSTER_NAME, ELS_CLUSTER_NAME);
    interpreter = new ElasticsearchInterpreter(props);
    interpreter.open();
  }

  @AfterClass
  public static void clean() {
    if (interpreter != null) {
      interpreter.close();
    }

    if (elsClient != null) {
      elsClient.admin().indices().delete(new DeleteIndexRequest("logs")).actionGet();
      elsClient.close();
    }

    if (elsNode != null) {
      elsNode.close();
    }
  }

  @Test
  public void testCount() {

    InterpreterResult res = interpreter.interpret("count /unknown", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("count /logs", null);
    assertEquals("50", res.message());
  }

  @Test
  public void testGet() {

    InterpreterResult res = interpreter.interpret("get /logs/http/unknown", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("get /logs/http/10", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Test
  public void testSearch() {

    InterpreterResult res = interpreter.interpret("size 10\nsearch /logs *", null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs {{{hello}}}", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("search /logs { \"query\": { \"match\": { \"status\": 500 } } }", null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs status:404", null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs { \"fields\": [ \"date\", \"request.headers\" ], \"query\": { \"match\": { \"status\": 500 } } }", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Test
  public void testAgg() {

    // Single-value metric
    InterpreterResult res = interpreter.interpret("search /logs { \"aggs\" : { \"distinct_status_count\" : " +
            " { \"cardinality\" : { \"field\" : \"status\" } } } }", null);
    assertEquals(Code.SUCCESS, res.code());

    // Multi-value metric
    res = interpreter.interpret("search /logs { \"aggs\" : { \"content_length_stats\" : " +
            " { \"extended_stats\" : { \"field\" : \"content_length\" } } } }", null);
    assertEquals(Code.SUCCESS, res.code());

    // Single bucket
    res = interpreter.interpret("search /logs { \"aggs\" : { " +
            " \"200_OK\" : { \"filter\" : { \"term\": { \"status\": \"200\" } }, " +
            "   \"aggs\" : { \"avg_length\" : { \"avg\" : { \"field\" : \"content_length\" } } } } } }", null);
    assertEquals(Code.SUCCESS, res.code());

    // Multi-buckets
    res = interpreter.interpret("search /logs { \"aggs\" : { \"status_count\" : " +
            " { \"terms\" : { \"field\" : \"status\" } } } }", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Test
  public void testIndex() {

    InterpreterResult res = interpreter.interpret("index /logs { \"date\": \"" + new Date() + "\", \"method\": \"PUT\", \"status\": \"500\" }", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("index /logs/http { \"date\": \"2015-12-06T14:54:23.368Z\", \"method\": \"PUT\", \"status\": \"500\" }", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Test
  public void testDelete() {

    InterpreterResult res = interpreter.interpret("delete /logs/http/unknown", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("delete /logs/http/11", null);
    assertEquals("11", res.message());
  }

  @Test
  public void testMisc() {

    InterpreterResult res = interpreter.interpret(null, null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("   \n \n ", null);
    assertEquals(Code.SUCCESS, res.code());
  }

}
