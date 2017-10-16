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

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class PigQueryInterpreterTest {

  private PigInterpreter pigInterpreter;
  private PigQueryInterpreter pigQueryInterpreter;
  private InterpreterContext context;

  @Before
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.put("zeppelin.pig.execType", "local");
    properties.put("zeppelin.pig.maxResult", "20");

    pigInterpreter = new PigInterpreter(properties);
    pigQueryInterpreter = new PigQueryInterpreter(properties);
    List<Interpreter> interpreters = new ArrayList();
    interpreters.add(pigInterpreter);
    interpreters.add(pigQueryInterpreter);
    InterpreterGroup group = new InterpreterGroup();
    group.put("note_id", interpreters);
    pigInterpreter.setInterpreterGroup(group);
    pigQueryInterpreter.setInterpreterGroup(group);
    pigInterpreter.open();
    pigQueryInterpreter.open();

    context = new InterpreterContext(null, "paragraph_id", null, null, null, null, null, null, null, null,
            null, null);
  }

  @After
  public void tearDown() {
    pigInterpreter.close();
    pigQueryInterpreter.close();
  }

  @Test
  public void testBasics() throws IOException {
    String content = "andy\tmale\t10\n"
            + "peter\tmale\t20\n"
            + "amy\tfemale\t14\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // run script in PigInterpreter
    String pigscript = "a = load '" + tmpFile.getAbsolutePath() + "' as (name, gender, age);\n"
            + "a2 = load 'invalid_path' as (name, gender, age);\n"
            + "dump a;";
    InterpreterResult result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("(andy,male,10)\n(peter,male,20)\n(amy,female,14)"));

    // run single line query in PigQueryInterpreter
    String query = "foreach a generate name, age;";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("name\tage\nandy\t10\npeter\t20\namy\t14\n", result.message().get(0).getData());

    // run multiple line query in PigQueryInterpreter
    query = "b = group a by gender;\nforeach b generate group as gender, COUNT($1) as count;";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("gender\tcount\nmale\t2\nfemale\t1\n", result.message().get(0).getData());

    // generate alias with unknown schema
    query = "b = group a by gender;\nforeach b generate group, COUNT($1);";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals("group\tcol_1\nmale\t2\nfemale\t1\n", result.message().get(0).getData());

    // syntax error in PigQueryInterpereter
    query = "b = group a by invalid_column;\nforeach b generate group as gender, COUNT($1) as count;";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("Projected field [invalid_column] does not exist in schema"));

    // execution error in PigQueryInterpreter
    query = "foreach a2 generate name, age;";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("Input path does not exist"));
  }

  @Test
  public void testMaxResult() throws IOException {
    StringBuilder content = new StringBuilder();
    for (int i=0;i<30;++i) {
      content.append(i + "\tname_" + i + "\n");
    }
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // run script in PigInterpreter
    String pigscript = "a = load '" + tmpFile.getAbsolutePath() + "' as (id, name);";
    InterpreterResult result = pigInterpreter.interpret(pigscript, context);
    assertEquals(0, result.message().size());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    // run single line query in PigQueryInterpreter
    String query = "foreach a generate id;";
    result = pigQueryInterpreter.interpret(query, context);
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("id\n0\n1\n2"));
    assertTrue(result.message().get(1).getData().contains("alert-warning"));
  }
}
