/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.pig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;

public class PigInterpreterSparkTest {
  private PigInterpreter pigInterpreter;
  private InterpreterContext context;

  public void setUpSpark(boolean includeJobStats) {
    Properties properties = new Properties();
    properties.put("zeppelin.pig.execType", "spark_local");
    properties.put("zeppelin.pig.includeJobStats", includeJobStats + "");
    pigInterpreter = new PigInterpreter(properties);
    pigInterpreter.open();
    context = InterpreterContext.builder().setParagraphId("paragraphId").build();

  }
  @After
  public void tearDown() {
    pigInterpreter.close();
  }

  @Test
  public void testBasics() throws IOException {
    setUpSpark(false);

    String content = "1\tandy\n"
        + "2\tpeter\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // simple pig script using dump
    String pigscript = "a = load '" + tmpFile.getAbsolutePath() + "';"
        + "dump a;";
    InterpreterResult result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("(1,andy)\n(2,peter)"));

    // describe
    pigscript = "a = load '" + tmpFile.getAbsolutePath() + "' as (id: int, name: bytearray);"
        + "describe a;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("a: {id: int,name: bytearray}"));

    // syntax error (compilation error)
    pigscript = "a = loa '" + tmpFile.getAbsolutePath() + "';"
        + "describe a;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains(
            "Syntax error, unexpected symbol at or near 'a'"));

    // syntax error
    pigscript = "a = load '" + tmpFile.getAbsolutePath() + "';"
        + "foreach a generate $0;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("expecting one of"));
  }

  @Test
  public void testIncludeJobStats() throws IOException {
    setUpSpark(true);

    String content = "1\tandy\n"
        + "2\tpeter\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // simple pig script using dump
    String pigscript = "a = load '" + tmpFile.getAbsolutePath() + "';"
        + "dump a;";
    InterpreterResult result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("Spark Job"));
    assertTrue(result.message().get(0).getData().contains("(1,andy)\n(2,peter)"));

    // describe
    pigscript = "a = load '" + tmpFile.getAbsolutePath() + "' as (id: int, name: bytearray);"
        + "describe a;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    // no job is launched, so no jobStats
    assertTrue(result.message().get(0).getData().contains("a: {id: int,name: bytearray}"));

    // syntax error (compilation error)
    pigscript = "a = loa '" + tmpFile.getAbsolutePath() + "';"
        + "describe a;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    // no job is launched, so no jobStats
    assertTrue(result.message().get(0).getData().contains(
            "Syntax error, unexpected symbol at or near 'a'"));

    // execution error
    pigscript = "a = load 'invalid_path';"
        + "dump a;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("Failed to read data from"));
  }

}


