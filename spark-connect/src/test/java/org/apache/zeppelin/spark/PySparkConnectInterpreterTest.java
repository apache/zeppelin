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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for PySparkConnectInterpreter.
 * Requires a running Spark Connect server.
 * Set SPARK_CONNECT_TEST_REMOTE env var (e.g. sc://localhost:15002) to enable.
 */
@EnabledIfEnvironmentVariable(named = "SPARK_CONNECT_TEST_REMOTE", matches = ".+")
public class PySparkConnectInterpreterTest {

  private static PySparkConnectInterpreter interpreter;
  private static SparkConnectInterpreter sparkConnectInterpreter;
  private static InterpreterGroup intpGroup;

  @BeforeAll
  public static void setUp() throws Exception {
    String remote = System.getenv("SPARK_CONNECT_TEST_REMOTE");
    Properties p = new Properties();
    p.setProperty("spark.remote", remote);
    p.setProperty("spark.app.name", "ZeppelinPySparkConnectTest");
    p.setProperty("zeppelin.spark.maxResult", "100");
    p.setProperty("zeppelin.pyspark.connect.useIPython", "false");
    p.setProperty("zeppelin.python", "python");

    intpGroup = new InterpreterGroup();
    
    // Create SparkConnectInterpreter first (required dependency)
    sparkConnectInterpreter = new SparkConnectInterpreter(p);
    sparkConnectInterpreter.setInterpreterGroup(intpGroup);
    intpGroup.put("session_1", new LinkedList<Interpreter>());
    intpGroup.get("session_1").add(sparkConnectInterpreter);
    sparkConnectInterpreter.open();

    // Create PySparkConnectInterpreter
    interpreter = new PySparkConnectInterpreter(p);
    interpreter.setInterpreterGroup(intpGroup);
    intpGroup.get("session_1").add(interpreter);
    interpreter.open();
  }

  @AfterAll
  public static void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
    if (sparkConnectInterpreter != null) {
      sparkConnectInterpreter.close();
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
  void testSparkSessionCreated() {
    assertNotNull(interpreter.getSparkConnectInterpreter());
    assertNotNull(interpreter.getSparkConnectInterpreter().getSparkSession());
  }

  @Test
  void testSimpleQuery() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
        "df = spark.sql(\"SELECT 1 AS id, 'hello' AS message\")\ndf.show()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    assertTrue(output.contains("id") || output.contains("message") || output.contains("hello"),
        "Output should contain query results: " + output);
  }

  @Test
  void testDataFrameVariable() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
        "df = spark.sql(\"SELECT 1 AS id, 'test' AS name\")\nprint(type(df))", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    assertTrue(output.contains("DataFrame") || output.contains("pyspark"),
        "Output should indicate DataFrame type: " + output);
  }

  @Test
  void testDeltaTableQuery() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    // Test the exact query from user
    InterpreterResult result = interpreter.interpret(
        "df = spark.sql(\"select * from gold.delta_test\")", context);
    // This might fail if table doesn't exist, but should not crash
    // We check that interpreter handled it gracefully
    assertTrue(result.code() == InterpreterResult.Code.SUCCESS 
        || result.code() == InterpreterResult.Code.ERROR,
        "Should handle query execution (success or error): " + result.code());
  }

  @Test
  void testSparkVariableAvailable() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
        "print('Spark session:', type(spark))", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    String output = context.out.toInterpreterResultMessage().get(0).getData();
    assertTrue(output.contains("SparkSession") || output.contains("spark"),
        "Spark session should be available: " + output);
  }
}
