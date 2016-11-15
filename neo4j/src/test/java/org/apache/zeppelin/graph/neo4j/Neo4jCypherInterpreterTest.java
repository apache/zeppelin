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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.graph.model.GraphResult;
import org.apache.zeppelin.graph.neo4j.Neo4jCypherInterpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Neo4jCypherInterpreterTest {
  
  private Neo4jCypherInterpreter interpreter;
  
  private InterpreterContext context;
  
  private ServerControls server;
  
  private static final ObjectMapper jsonMapper = new ObjectMapper();
  
  private static final String LABEL_PERSON = "Person";
  private static final String REL_KNOWS = "KNOWS";
  
  private static final String CYPHER_FOREACH = "FOREACH (x in range(1,1000) | CREATE (:%s{name: \"name\" + x, age: %s}))";
  private static final String CHPHER_UNWIND = "UNWIND range(1,1000) as x "
        + "MATCH (n), (m) WHERE id(n) = x AND id(m) = toInt(rand() * 1000) "
        + "CREATE (n)-[:%s]->(m)";
  
  
  private static final String EXPECTED_TABLE = "name\tage\n\"name1\"\t1\n";

  @Before
  public void setUp() throws Exception {
    server = TestServerBuilders.newInProcessBuilder()
                .withConfig("dbms.security.auth_enabled","false")
                .withFixture(String.format(CYPHER_FOREACH, LABEL_PERSON, "x % 10"))
                .withFixture(String.format(CHPHER_UNWIND, REL_KNOWS))
                .newServer();
    Properties p = new Properties();
    p.setProperty(Neo4jCypherInterpreter.NEO4J_SERVER_URL, server.boltURI().toString());
    interpreter = new Neo4jCypherInterpreter(p);
    context = new InterpreterContext("note", "id", "title", "text",
            new AuthenticationInfo(),
            new HashMap<String, Object>(),
            new GUI(),
            new AngularObjectRegistry(new InterpreterGroup().getId(), null),
            new LocalResourcePool("id"),
            new LinkedList<InterpreterContextRunner>(),
            new InterpreterOutput(new InterpreterOutputListener() {
              @Override
              public void onAppend(InterpreterOutput out, byte[] line) {

              }

              @Override
              public void onUpdate(InterpreterOutput out, byte[] output) {

              }
            }));
  }

  @After
  public void tearDown() throws Exception {
    server.close();
    interpreter.close();
  }

  @Test
  public void testRenderTable() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("MATCH (n:Person{name: 'name1'}) RETURN n.name AS name, n.age AS age", context);
    assertEquals(EXPECTED_TABLE, result.message());
    assertEquals(Code.SUCCESS, result.code());
  }

  @Test
  public void testRenderNetwork() throws JsonParseException, JsonMappingException, IOException {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("MATCH (n)-[r:KNOWS]-(m) RETURN n, r, m LIMIT 1", context);
    GraphResult.Graph graph = jsonMapper.readValue(result.message(), GraphResult.Graph.class);
    assertEquals(2, graph.getNodes().size());
    assertEquals(1, graph.getEdges().size());
    assertEquals(1, graph.getLabels().size());
    assertEquals(1, graph.getTypes().size());
    assertEquals(true, graph.getLabels().containsKey(LABEL_PERSON));
    assertEquals(REL_KNOWS, graph.getTypes().iterator().next());
    assertEquals(Code.SUCCESS, result.code());
  }

  @Test
  public void testFallingQuery() {
    interpreter.open();
    InterpreterResult result = interpreter.interpret("", context);
    assertEquals(Code.ERROR, result.code());

    result = interpreter.interpret(null, context);
    assertEquals(Code.ERROR, result.code());

    result = interpreter.interpret("MATCH (n:Person{name: }) RETURN n.name AS name, n.age AS age", context);
    assertEquals(Code.ERROR, result.code());
  }
  
}
