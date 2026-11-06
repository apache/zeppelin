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

package org.apache.zeppelin.spark;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for SparkConnectSqlInterpreter.
 * Requires a running Spark Connect server.
 * Set SPARK_CONNECT_TEST_REMOTE env var (e.g. sc://localhost:15002) to enable.
 */
@EnabledIfEnvironmentVariable(named = "SPARK_CONNECT_TEST_REMOTE", matches = ".+")
public class SparkConnectSqlInterpreterTest {

  private static SparkConnectInterpreter connectInterpreter;
  private static SparkConnectSqlInterpreter sqlInterpreter;
  private static InterpreterGroup intpGroup;

  @BeforeAll
  public static void setUp() throws Exception {
    String remote = System.getenv("SPARK_CONNECT_TEST_REMOTE");
    Properties p = new Properties();
    p.setProperty("spark.remote", remote);
    p.setProperty("spark.app.name", "ZeppelinSparkConnectSqlTest");
    p.setProperty("zeppelin.spark.maxResult", "100");
    p.setProperty("zeppelin.spark.concurrentSQL", "true");
    p.setProperty("zeppelin.spark.concurrentSQL.max", "10");
    p.setProperty("zeppelin.spark.sql.stacktrace", "true");
    p.setProperty("zeppelin.spark.sql.interpolation", "false");

    intpGroup = new InterpreterGroup();
    connectInterpreter = new SparkConnectInterpreter(p);
    connectInterpreter.setInterpreterGroup(intpGroup);

    sqlInterpreter = new SparkConnectSqlInterpreter(p);
    sqlInterpreter.setInterpreterGroup(intpGroup);

    intpGroup.put("session_1", new LinkedList<Interpreter>());
    intpGroup.get("session_1").add(connectInterpreter);
    intpGroup.get("session_1").add(sqlInterpreter);

    connectInterpreter.open();
    sqlInterpreter.open();
  }

  @AfterAll
  public static void tearDown() throws InterpreterException {
    if (sqlInterpreter != null) {
      sqlInterpreter.close();
    }
    if (connectInterpreter != null) {
      connectInterpreter.close();
    }
  }

  private static InterpreterContext getInterpreterContext() {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setParagraphTitle("title")
        .setAngularObjectRegistry(new AngularObjectRegistry(intpGroup.getId(), null))
        .setResourcePool(new LocalResourcePool("id"))
        .setInterpreterOut(new InterpreterOutput())
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .build();
  }

  @Test
  void testSimpleQuery() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
        "SELECT 1 AS id, 'hello' AS message", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    assertTrue(output.contains("id"));
    assertTrue(output.contains("message"));
    assertTrue(output.contains("hello"));
  }

  @Test
  void testMultipleStatements() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
        "SELECT 1 AS a; SELECT 2 AS b", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(2, context.out.toInterpreterResultMessage().size());
  }

  @Test
  void testInvalidSQL() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
        "SELECT FROM WHERE INVALID", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(context.out.toInterpreterResultMessage().get(0).getData().length() > 0);
  }

  @Test
  void testMaxResultLimit() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("limit", "3");
    InterpreterResult result = sqlInterpreter.interpret(
        "SELECT id FROM range(100)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    String[] lines = output.split("\n");
    // header + 3 data rows
    assertTrue(lines.length <= 5);
  }

  @Test
  void testCreateAndQuery() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = sqlInterpreter.interpret(
        "CREATE OR REPLACE TEMP VIEW sql_test AS SELECT 42 AS answer", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = sqlInterpreter.interpret("SELECT * FROM sql_test", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    assertTrue(output.contains("42"));
  }

  @Test
  void testFormType() {
    assertEquals(Interpreter.FormType.SIMPLE, sqlInterpreter.getFormType());
  }
}
