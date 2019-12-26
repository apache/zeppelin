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

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.graph.neo4j.Neo4jConnectionManager.Neo4jAuthType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.graph.GraphResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Neo4jCypherInterpreterTest {

  private Neo4jCypherInterpreter interpreter;

  private InterpreterContext context;

  private static ServerControls server;

  private static final Gson gson = new Gson();

  private static final String LABEL_PERSON = "Person";
  private static final String REL_KNOWS = "KNOWS";

  private static final String CYPHER_FOREACH =
          "FOREACH (x in range(1,100) | CREATE (:%s{name: \"name\" + x, age: %s, " +
                  "address: point({ longitude: 56.7, latitude: 12.78, height: 8 }), " +
                  "birth: date('1984-04-04')}))";
  private static final String CHPHER_UNWIND = "UNWIND range(1,100) as x "
        + "MATCH (n), (m) WHERE id(n) = x AND id(m) = toInt(rand() * 100) "
        + "CREATE (n)-[:%s]->(m)";
  
  private static final String TABLE_RESULT_PREFIX = "%table ";
  private static final String NETWORK_RESULT_PREFIX = "%network ";

  @BeforeClass
  public static void setUpNeo4jServer() throws Exception {
    server = TestServerBuilders.newInProcessBuilder()
                .withConfig("dbms.security.auth_enabled", "false")
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
    context = InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput(null))
        .build();
  }

  @After
  public void tearDownZeppelin() throws Exception {
    interpreter.close();
  }

  @Test
  public void testTableWithArray() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret(
            "return 'a' as colA, 'b' as colB, [1, 2, 3] as colC", context);
    assertEquals(Code.SUCCESS, result.code());
    final String tableResult = "colA\tcolB\tcolC\n\"a\"\t\"b\"\t[1,2,3]\n";
    assertEquals(tableResult, result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));

    result = interpreter.interpret(
            "return 'a' as colA, 'b' as colB, [{key: \"value\"}, {key: 1}] as colC", context);
    assertEquals(Code.SUCCESS, result.code());
    final String tableResultWithMap =
            "colA\tcolB\tcolC\n\"a\"\t\"b\"\t[{\"key\":\"value\"},{\"key\":1}]\n";
    assertEquals(tableResultWithMap, result.toString().replace(TABLE_RESULT_PREFIX,
            StringUtils.EMPTY));
  }

  @Test
  public void testCreateIndex() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("CREATE INDEX ON :Person(name)", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals(StringUtils.EMPTY, result.toString());
  }

  @Test
  public void testRenderTable() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("MATCH (n:Person) "
            + "WHERE n.name IN ['name1', 'name2', 'name3'] "
            + "RETURN n.name AS name, n.age AS age, "
            + "n.address AS address, n.birth AS birth", context);
    assertEquals(Code.SUCCESS, result.code());
    final String tableResult = "name\tage\taddress\tbirth\n" +
            "\"name1\"\t1\tPoint{srid=4979, x=56.7, y=12.78, z=8.0}\t1984-04-04\n" +
            "\"name2\"\t2\tPoint{srid=4979, x=56.7, y=12.78, z=8.0}\t1984-04-04\n" +
            "\"name3\"\t3\tPoint{srid=4979, x=56.7, y=12.78, z=8.0}\t1984-04-04\n";
    assertEquals(tableResult, result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));
  }

  @Test
  public void testRenderMap() {
    interpreter.open();
    final String jsonQuery =
            "RETURN {key: \"value\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]} as object";
    final String objectKey = "object.key";
    final String objectListKey = "object.listKey";
    InterpreterResult result = interpreter.interpret(jsonQuery, context);
    assertEquals(Code.SUCCESS, result.code());
    String[] rows = result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY)
            .split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 2);
    List<String> header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey), true);
    List<String> row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey)),
            "[{\"inner\":\"Map1\"},{\"inner\":\"Map2\"}]");

    final String query = "WITH [{key: \"value\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]},"
            + "{key: \"value2\", listKey: [{inner: \"Map12\"}, {inner: \"Map22\"}]}] "
            + "AS array UNWIND array AS object RETURN object";
    result = interpreter.interpret(query, context);
    assertEquals(Code.SUCCESS, result.code());
    rows = result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY)
            .split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 3);
    header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey), true);
    row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey)),
            "[{\"inner\":\"Map1\"},{\"inner\":\"Map2\"}]");
    row = Arrays.asList(rows[2].split(Neo4jCypherInterpreter.TAB));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value2");
    assertEquals(row.get(header.indexOf(objectListKey)),
            "[{\"inner\":\"Map12\"},{\"inner\":\"Map22\"}]");

    final String jsonListWithNullQuery = "WITH [{key: \"value\", listKey: null},"
            + "{key: \"value2\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]}] "
            + "AS array UNWIND array AS object RETURN object";
    result = interpreter.interpret(jsonListWithNullQuery, context);
    assertEquals(Code.SUCCESS, result.code());
    rows = result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY)
            .split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 3);
    header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey), true);
    row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey)), StringUtils.EMPTY);
    assertEquals(row.get(header.indexOf(objectListKey)), "");
    row = Arrays.asList(rows[2].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value2");
    assertEquals(row.get(header.indexOf(objectListKey)),
            "[{\"inner\":\"Map1\"},{\"inner\":\"Map2\"}]");

    final String jsonListWithoutListKeyQuery = "WITH [{key: \"value\"},"
            + "{key: \"value2\", listKey: [{inner: \"Map1\"}, {inner: \"Map2\"}]}] "
            + "AS array UNWIND array AS object RETURN object";
    result = interpreter.interpret(jsonListWithoutListKeyQuery, context);
    assertEquals(Code.SUCCESS, result.code());
    rows = result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY)
            .split(Neo4jCypherInterpreter.NEW_LINE);
    assertEquals(rows.length, 3);
    header = Arrays.asList(rows[0].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(header.contains(objectKey), true);
    assertEquals(header.contains(objectListKey), true);
    row = Arrays.asList(rows[1].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value");
    assertEquals(row.get(header.indexOf(objectListKey)), StringUtils.EMPTY);
    row = Arrays.asList(rows[2].split(Neo4jCypherInterpreter.TAB, -1));
    assertEquals(row.size(), header.size());
    assertEquals(row.get(header.indexOf(objectKey)), "value2");
    assertEquals(row.get(header.indexOf(objectListKey)),
            "[{\"inner\":\"Map1\"},{\"inner\":\"Map2\"}]");
  }

  @Test
  public void testRenderNetwork() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret(
            "MATCH (n)-[r:KNOWS]-(m) RETURN n, r, m LIMIT 1", context);
    GraphResult.Graph graph = gson.fromJson(result.toString().replace(NETWORK_RESULT_PREFIX,
            StringUtils.EMPTY), GraphResult.Graph.class);
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
    final String errorMsgEmpty = "";
    InterpreterResult result = interpreter.interpret(StringUtils.EMPTY, context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals(errorMsgEmpty, result.toString());

    result = interpreter.interpret(null, context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals(errorMsgEmpty, result.toString());

    result = interpreter.interpret("MATCH (n:Person{name: }) RETURN n.name AS name, n.age AS age",
            context);
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  public void testDates() {
    InterpreterResult result = interpreter.interpret(
            "RETURN datetime('2015-06-24T12:50:35.556+0100') AS theDateTime", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theDateTime\n2015-06-24T12:50:35.556+01:00\n", result.toString()
            .replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));

    result = interpreter.interpret("RETURN localdatetime('2015185T19:32:24') AS theLocalDateTime",
            context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theLocalDateTime\n2015-07-04T19:32:24\n", result.toString()
            .replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));

    result = interpreter.interpret("RETURN date('+2015-W13-4') AS theDate", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theDate\n2015-03-26\n", result.toString()
            .replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));

    result = interpreter.interpret("RETURN time('125035.556+0100') AS theTime", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theTime\n12:50:35.556+01:00\n", result.toString().replace(TABLE_RESULT_PREFIX,
            StringUtils.EMPTY));

    result = interpreter.interpret("RETURN localtime('12:50:35.556') AS theLocalTime", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theLocalTime\n12:50:35.556\n", result.toString()
            .replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));
  }

  @Test
  public void testDuration() {
    InterpreterResult result = interpreter.interpret(
            "RETURN duration('P14DT16H12M') AS theDuration", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("theDuration\nP0M14DT58320S\n", result.toString()
            .replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));
  }

  @Test
  public void testPoint() {
    InterpreterResult result = interpreter.interpret("RETURN point({ x:3, y:0 }) AS cartesian_2d,"
            + "point({ x:0, y:4, z:1 }) AS cartesian_3d,"
            + "point({ latitude: 12, longitude: 56 }) AS geo_2d,"
            + "point({ latitude: 12, longitude: 56, height: 1000 }) AS geo_3d", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("cartesian_2d\tcartesian_3d\tgeo_2d\tgeo_3d\n" +
            "Point{srid=7203, x=3.0, y=0.0}\tPoint{srid=9157, x=0.0, y=4.0, z=1.0}\t" +
            "Point{srid=4326, x=56.0, y=12.0}\tPoint{srid=4979, x=56.0, y=12.0, z=1000.0}\n",
            result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));

    result = interpreter.interpret(
            "WITH point({ latitude: 12, longitude: 56, height: 1000 }) AS geo_3d " +
            "RETURN geo_3d.latitude AS latitude, geo_3d.longitude AS longitude, " +
                    "geo_3d.height AS height", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("latitude\tlongitude\theight\n" +
                    "12.0\t56.0\t1000.0\n",
            result.toString().replace(TABLE_RESULT_PREFIX, StringUtils.EMPTY));
  }
}
