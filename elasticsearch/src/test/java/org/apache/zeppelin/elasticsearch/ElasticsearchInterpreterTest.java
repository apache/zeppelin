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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ElasticsearchInterpreterTest {

  @DataPoint public static ElasticsearchInterpreter transportInterpreter;
  @DataPoint public static ElasticsearchInterpreter httpInterpreter;

  private static Client elsClient;
  private static Node elsNode;

  private static final String[] METHODS = { "GET", "PUT", "DELETE", "POST" };
  private static final int[] STATUS = { 200, 404, 500, 403 };

  private static final String ELS_CLUSTER_NAME = "zeppelin-elasticsearch-interpreter-test";
  private static final String ELS_HOST = "localhost";
  private static final String ELS_TRANSPORT_PORT = "10300";
  private static final String ELS_HTTP_PORT = "10200";
  private static final String ELS_PATH = "/tmp/els";

  private static final AtomicInteger deleteId = new AtomicInteger(2);


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

    for (int i = 0; i < 48; i++) {
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

    for (int i = 1; i < 3; i++) {
      elsClient.prepareIndex("logs", "http", "very/strange/id#" + i)
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
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_CLUSTER_NAME, ELS_CLUSTER_NAME);

    props.put(ElasticsearchInterpreter.ELASTICSEARCH_PORT, ELS_TRANSPORT_PORT);
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_CLIENT_TYPE, "transport");
    transportInterpreter = new ElasticsearchInterpreter(props);
    transportInterpreter.open();

    props.put(ElasticsearchInterpreter.ELASTICSEARCH_PORT, ELS_HTTP_PORT);
    props.put(ElasticsearchInterpreter.ELASTICSEARCH_CLIENT_TYPE, "http");
    httpInterpreter = new ElasticsearchInterpreter(props);
    httpInterpreter.open();
  }

  @AfterClass
  public static void clean() {
    if (transportInterpreter != null) {
      transportInterpreter.close();
    }

    if (httpInterpreter != null) {
      httpInterpreter.close();
    }

    if (elsClient != null) {
      elsClient.admin().indices().delete(new DeleteIndexRequest("*")).actionGet();
      elsClient.close();
    }

    if (elsNode != null) {
      elsNode.close();
    }
  }

  private InterpreterContext buildContext(String noteAndParagraphId) {
    final AngularObjectRegistry angularObjReg = new AngularObjectRegistry("elasticsearch", null);
    return new InterpreterContext(noteAndParagraphId, noteAndParagraphId, null, null, null, null, null,
        null, angularObjReg , null, null, null);
  }

  @Theory
  public void testCount(ElasticsearchInterpreter interpreter) {

    final InterpreterContext ctx = buildContext("testCount");

    InterpreterResult res = interpreter.interpret("count /unknown", ctx);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("count /logs", ctx);
    assertEquals(Code.SUCCESS, res.code());
    assertEquals("50", res.message().get(0).getData());
    assertNotNull(ctx.getAngularObjectRegistry().get("count_testCount", null, null));
    assertEquals(50l, ctx.getAngularObjectRegistry().get("count_testCount", null, null).get());

    res = interpreter.interpret("count /logs { \"query\": { \"match\": { \"status\": 500 } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testGet(ElasticsearchInterpreter interpreter) {

    final InterpreterContext ctx = buildContext("get");

    InterpreterResult res = interpreter.interpret("get /logs/http/unknown", ctx);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("get /logs/http/unknown/unknown", ctx);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("get /unknown/unknown/unknown", ctx);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("get /logs/http/very/strange/id#1", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("get /logs/http/4", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("get /logs/_all/4", ctx);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testSearch(ElasticsearchInterpreter interpreter) {

    final InterpreterContext ctx = buildContext("search");

    InterpreterResult res = interpreter.interpret("size 10\nsearch /logs *", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs {{{hello}}}", ctx);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("search /logs { \"query\": { \"match\": { \"status\": 500 } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs status:404", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs { \"fields\": [ \"date\", \"request.headers\" ], \"query\": { \"match\": { \"status\": 500 } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testAgg(ElasticsearchInterpreter interpreter) {

    final InterpreterContext ctx = buildContext("agg");

    // Single-value metric
    InterpreterResult res = interpreter.interpret("search /logs { \"aggs\" : { \"distinct_status_count\" : " +
            " { \"cardinality\" : { \"field\" : \"status\" } } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());

    // Multi-value metric
    res = interpreter.interpret("search /logs { \"aggs\" : { \"content_length_stats\" : " +
            " { \"extended_stats\" : { \"field\" : \"content_length\" } } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());

    // Single bucket
    res = interpreter.interpret("search /logs { \"aggs\" : { " +
            " \"200_OK\" : { \"filter\" : { \"term\": { \"status\": \"200\" } }, " +
            "   \"aggs\" : { \"avg_length\" : { \"avg\" : { \"field\" : \"content_length\" } } } } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());

    // Multi-buckets
    res = interpreter.interpret("search /logs { \"aggs\" : { \"status_count\" : " +
            " { \"terms\" : { \"field\" : \"status\" } } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("search /logs { \"aggs\" : { " +
            " \"length\" : { \"terms\": { \"field\": \"status\" }, " +
            "   \"aggs\" : { \"sum_length\" : { \"sum\" : { \"field\" : \"content_length\" } }, \"sum_status\" : { \"sum\" : { \"field\" : \"status\" } } } } } }", ctx);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testIndex(ElasticsearchInterpreter interpreter) {

    InterpreterResult res = interpreter.interpret("index /logs { \"date\": \"" + new Date() + "\", \"method\": \"PUT\", \"status\": \"500\" }", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("index /logs/http { bad ", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("index /logs/http { \"date\": \"2015-12-06T14:54:23.368Z\", \"method\": \"PUT\", \"status\": \"500\" }", null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("index /logs/http/1000 { \"date\": \"2015-12-06T14:54:23.368Z\", \"method\": \"PUT\", \"status\": \"500\" }", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testDelete(ElasticsearchInterpreter interpreter) {

    InterpreterResult res = interpreter.interpret("delete /logs/http/unknown", null);
    assertEquals(Code.ERROR, res.code());

    res = interpreter.interpret("delete /unknown/unknown/unknown", null);
    assertEquals(Code.ERROR, res.code());

    final int testDeleteId = deleteId.decrementAndGet();
    res = interpreter.interpret("delete /logs/http/" + testDeleteId, null);
    assertEquals(Code.SUCCESS, res.code());
    assertEquals("" + testDeleteId, res.message().get(0).getData());
  }

  @Theory
  public void testMisc(ElasticsearchInterpreter interpreter) {

    InterpreterResult res = interpreter.interpret(null, null);
    assertEquals(Code.SUCCESS, res.code());

    res = interpreter.interpret("   \n \n ", null);
    assertEquals(Code.SUCCESS, res.code());
  }

  @Theory
  public void testCompletion(ElasticsearchInterpreter interpreter) {
    final List<InterpreterCompletion> expectedResultOne = Arrays.asList(new InterpreterCompletion("count", "count", CompletionType.command.name()));
    final List<InterpreterCompletion> expectedResultTwo = Arrays.asList(new InterpreterCompletion("help", "help", CompletionType.command.name()));

    final List<InterpreterCompletion> resultOne = interpreter.completion("co", 0, null);
    final List<InterpreterCompletion> resultTwo = interpreter.completion("he", 0, null);
    final List<InterpreterCompletion> resultAll = interpreter.completion("", 0, null);

    Assert.assertEquals(expectedResultOne, resultOne);
    Assert.assertEquals(expectedResultTwo, resultTwo);

    final List<String> allCompletionList = new ArrayList<>();
    for (final InterpreterCompletion ic : resultAll) {
      allCompletionList.add(ic.getName());
    }
    Assert.assertEquals(ElasticsearchInterpreter.COMMANDS, allCompletionList);
  }

}
