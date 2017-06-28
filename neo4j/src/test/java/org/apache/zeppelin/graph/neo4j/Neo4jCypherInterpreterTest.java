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
package org.apache.zeppelin.graph.neo4j;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.graph.neo4j.Neo4jConnectionManager.Neo4jAuthType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.graph.GraphResult;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import com.google.gson.Gson;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Neo4jCypherInterpreterTest {

  private Neo4jCypherInterpreter interpreter;

  private InterpreterContext context;

  private static ServerControls server;

  private static final Gson gson = new Gson();

  private static final String LABEL_PERSON = "Person";
  private static final String REL_KNOWS = "KNOWS";

  private static final String CYPHER_FOREACH = "FOREACH (x in range(1,1000) | CREATE (:%s{name: \"name\" + x, age: %s}))";
  private static final String CHPHER_UNWIND = "UNWIND range(1,1000) as x "
        + "MATCH (n), (m) WHERE id(n) = x AND id(m) = toInt(rand() * 1000) "
        + "CREATE (n)-[:%s]->(m)";

  private static final String QUERY_TEMPLATE_SIMPLE_OBJECT = "RETURN %s as object";

  @BeforeClass
  public static void setUpNeo4jServer() throws Exception {
    server = TestServerBuilders.newInProcessBuilder()
                .withConfig("dbms.security.auth_enabled","false")
                .withFixture(String.format(CYPHER_FOREACH, LABEL_PERSON, "x % 10"))
                .withFixture(String.format(CHPHER_UNWIND, REL_KNOWS))
                .newServer();
  }

  @AfterClass
  public static void tearDownNeo4jServer() throws Exception {
    server.close();
  }
  
  @Before
  public void setUpZeppelin() {
    Properties p = new Properties();
    p.setProperty(Neo4jConnectionManager.NEO4J_SERVER_URL, server.boltURI().toString());
    p.setProperty(Neo4jConnectionManager.NEO4J_AUTH_TYPE, Neo4jAuthType.NONE.toString());
    p.setProperty(Neo4jConnectionManager.NEO4J_MAX_CONCURRENCY, "50");
    interpreter = new Neo4jCypherInterpreter(p);
    context = new InterpreterContext("note", "id", null, "title", "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(new InterpreterGroup().getId(), null),
            new LocalResourcePool("id"),
            new LinkedList<InterpreterContextRunner>(),
            new InterpreterOutput(null));
  }

  @After
  public void tearDownZeppelin() throws Exception {
    interpreter.close();
  }

  @Test
  public void testRenderTable() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("MATCH (n:Person) "
    		+ "WHERE n.name IN ['name1', 'name2', 'name3'] "
    		+ "RETURN n.name AS name, n.age AS age", context);
    assertEquals(Code.SUCCESS, result.code());
    final String tableResult = "name\tage\n\"name1\"\t1\n\"name2\"\t2\n\"name3\"\t3\n";
    assertEquals(tableResult, result.toString().replace("%table ", StringUtils.EMPTY));
  }

  @Test
  public void testRenderArray() {
    interpreter.open();
    final String array = "[0, 1, 2]";
    final String tableResult = "object\n0\n1\n2\n";
    InterpreterResult result = interpreter.interpret(String.format(QUERY_TEMPLATE_SIMPLE_OBJECT, array), context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals(tableResult, result.toString().replace("%table ", StringUtils.EMPTY));
  }

  @Test
  public void testRenderMap() {
    interpreter.open();
    final String json = "{key: \"value\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]}";
    final String objectKey = "object.key";
    final String objectListKey0Inner = "object.listKey[0].inner";
    final String objectListKey1Inner = "object.listKey[1].inner";
    InterpreterResult result = interpreter.interpret(String.format(QUERY_TEMPLATE_SIMPLE_OBJECT, json), context);
    assertEquals(Code.SUCCESS, result.code());
    String[] rows = result.toString().replace("%table ", StringUtils.EMPTY).split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 2);
    List<String> header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey0Inner), true);
    assertEquals(header.contains(objectListKey1Inner), true);
    List<String> row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey0Inner)), "Map1");
    assertEquals(row.get(header.indexOf(objectListKey1Inner)), "Map2");

    final String jsonList = "[{key: \"value\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]},"
    		+ "{key: \"value2\", listKey: [{inner: \"Map12\"}, {inner: \"Map22\"}]}]";
    result = interpreter.interpret(String.format(QUERY_TEMPLATE_SIMPLE_OBJECT, jsonList), context);
    assertEquals(Code.SUCCESS, result.code());
    rows = result.toString().replace("%table ", StringUtils.EMPTY).split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 3);
    header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey0Inner), true);
    assertEquals(header.contains(objectListKey1Inner), true);
    row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey0Inner)), "Map1");
    assertEquals(row.get(header.indexOf(objectListKey1Inner)), "Map2");
    row = Arrays.asList(rows[2].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value2");
    assertEquals(row.get(header.indexOf(objectListKey0Inner)), "Map12");
    assertEquals(row.get(header.indexOf(objectListKey1Inner)), "Map22");

    final String jsonListWithNull = "[{key: \"value\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]},"
    		+ "{key: \"value2\", listKey: null}]";
    final String objectListKey = "object.listKey";
    result = interpreter.interpret(String.format(QUERY_TEMPLATE_SIMPLE_OBJECT, jsonListWithNull), context);
    assertEquals(Code.SUCCESS, result.code());
    rows = result.toString().replace("%table ", StringUtils.EMPTY).split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 3);
    header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey0Inner), true);
    assertEquals(header.contains(objectListKey1Inner), true);
    assertEquals(header.contains(objectListKey), true);
    row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey0Inner)), "Map1");
    assertEquals(row.get(header.indexOf(objectListKey1Inner)), "Map2");
    assertEquals(row.get(header.indexOf(objectListKey)), "");
    row = Arrays.asList(rows[2].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value2");
    assertEquals(row.get(header.indexOf(objectListKey0Inner)), "");
    assertEquals(row.get(header.indexOf(objectListKey1Inner)), "");
    assertEquals(row.get(header.indexOf(objectListKey)), "");
  }

  @Test
  public void testRenderNetwork() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("MATCH (n)-[r:KNOWS]-(m) RETURN n, r, m LIMIT 1", context);
    GraphResult.Graph graph = gson.fromJson(result.toString().replace("%network ", StringUtils.EMPTY), GraphResult.Graph.class);
    assertEquals(2, graph.getNodes().size());
    assertEquals(true, graph.getNodes().iterator().next().getLabel().equals(LABEL_PERSON));
    assertEquals(1, graph.getEdges().size());
    assertEquals(true, graph.getEdges().iterator().next().getLabel().equals(REL_KNOWS));
    assertEquals(1, graph.getLabels().size());
    assertEquals(1, graph.getTypes().size());
    assertEquals(true, graph.getLabels().containsKey(LABEL_PERSON));
    assertEquals(REL_KNOWS, graph.getTypes().iterator().next());
    assertEquals(Code.SUCCESS, result.code());
  }

  @Test
  public void testFallingQuery() {
    interpreter.open();
    final String ERROR_MSG_EMPTY = "%text Cypher query is Empty";
    InterpreterResult result = interpreter.interpret(StringUtils.EMPTY, context);
    assertEquals(Code.ERROR, result.code());
    assertEquals(ERROR_MSG_EMPTY, result.toString());

    result = interpreter.interpret(null, context);
    assertEquals(Code.ERROR, result.code());
    assertEquals(ERROR_MSG_EMPTY, result.toString());

    result = interpreter.interpret("MATCH (n:Person{name: }) RETURN n.name AS name, n.age AS age", context);
    assertEquals(Code.ERROR, result.code());
  }
  
}
