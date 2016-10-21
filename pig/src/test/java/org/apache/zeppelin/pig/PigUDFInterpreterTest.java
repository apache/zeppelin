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

package org.apache.zeppelin.pig;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;

import org.junit.Assert;
import org.junit.Before;
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
public class PigUDFInterpreterTest {

  private PigInterpreter pigInterpreter;
  private PigUDFInterpreter udfInterpreter;
  private InterpreterContext context;

  @Before
  public void setUp() {
    Properties properties = new Properties();
    properties.put("zeppelin.pig.execType", "local");
    properties.put("zeppelin.pig.maxResult", "20");
    properties.put("zeppelin.interpreter.localRepo", System.getProperty("zeppelin.pig.localRepo"));
    System.out.println("localRepo**********************" + System.getProperty("zeppelin.pig.localRepo"));
    pigInterpreter = new PigInterpreter(properties);
    udfInterpreter = new PigUDFInterpreter(properties);
    context = new InterpreterContext(null, "paragraph_id", null, null, null, null, null, null, null,
            null, null);

    List<Interpreter> interpreters = new ArrayList();
    interpreters.add(pigInterpreter);
    interpreters.add(udfInterpreter);
    InterpreterGroup group = new InterpreterGroup();
    group.put("note_id", interpreters);
    pigInterpreter.setInterpreterGroup(group);
    udfInterpreter.setInterpreterGroup(group);
    pigInterpreter.open();
    udfInterpreter.open();
  }

  @Test
  public void testSimpleUDFWithoutPackage() throws IOException {
    InterpreterResult result = udfInterpreter.interpret(
            "import org.apache.pig.data.Tuple;\n" +
            "import org.apache.pig.EvalFunc;\n" +
            "import java.io.IOException;\n" +
            "public class UDF1 extends EvalFunc<String> {\n" +
                    "public String exec(Tuple input) throws IOException {\n" +
                    "return \"1\";}\n" +
                    "}", context);
    Assert.assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    Assert.assertEquals("Build successfully", result.message());

    String content = "1\tandy\n"
            + "2\tpeter\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // simple pig script using this udf
    String pigscript =
            "DEFINE udf1 UDF1();" +
                    "a = load '" + tmpFile.getAbsolutePath() + "';"
                    + "b = foreach a generate udf1($0), $1;"
                    + "dump b;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.type());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().contains("(1,andy)\n(1,peter)"));
  }

  @Test
  public void testSimpleUDFWithPackage() throws IOException {
    InterpreterResult result = udfInterpreter.interpret(
                    "package org.apache.zeppelin.pig;\n" +
                    "import org.apache.pig.data.Tuple;\n" +
                    "import org.apache.pig.EvalFunc;\n" +
                    "import java.io.IOException;\n" +
                    "public class UDF2 extends EvalFunc<String> {\n" +
                    "public String exec(Tuple input) throws IOException {\n" +
                    "return \"2\";}\n" +
                    "}", context);
    Assert.assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    Assert.assertEquals("Build successfully", result.message());

    String content = "1\tandy\n"
            + "2\tpeter\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // simple pig script using this udf
    String pigscript =
            "DEFINE udf2 org.apache.zeppelin.pig.UDF2();" +
                    "a = load '" + tmpFile.getAbsolutePath() + "';"
                    + "b = foreach a generate udf2($0), $1;"
                    + "dump b;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.type());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().contains("(2,andy)\n(2,peter)"));
  }

  @Test
  public void testUDFWithDependency() throws IOException {
    InterpreterResult result = udfInterpreter.interpret(
            "package org.apache.zeppelin.pig;\n" +
            "import org.apache.pig.data.Tuple;\n" +
            "import org.apache.pig.EvalFunc;\n" +
            "import java.io.IOException;\n" +
            "import org.apache.zeppelin.pig.test.Dummy;\n" +
            "public class UDF3 extends EvalFunc<String> {\n" +
            "public String exec(Tuple input) throws IOException {\n" +
            "return Dummy.VALUE_1;}\n" +
            "}", context);

    Assert.assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    Assert.assertEquals("Build successfully", result.message());

    String content = "1\tandy\n"
        + "2\tpeter\n";
    File tmpFile = File.createTempFile("zeppelin", "test");
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();

    // simple pig script using this udf
    String pigscript =
        "DEFINE udf3 org.apache.zeppelin.pig.UDF3();" +
            "a = load '" + tmpFile.getAbsolutePath() + "';"
            + "b = foreach a generate udf3($0), $1;"
            + "dump b;";
    result = pigInterpreter.interpret(pigscript, context);
    assertEquals(InterpreterResult.Type.TEXT, result.type());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().contains("(1,andy)\n(1,peter)"));
  }

  @Test
  public void testInvalidUDF() {
    InterpreterResult result = udfInterpreter.interpret(
            "import org.apache.pig.data.Tuple;\n" +
            "import org.apache.pig.EvalFunc;\n" +
            "import java.io.IOException;\n" +
            "public class UDF1 extends EvalFunc<String> {" +
                    "public String exe(Tuple input) throws IOException {" +
                    "return \"1\";}" +
                    "}", context);

    Assert.assertEquals(InterpreterResult.Code.ERROR, result.code());
    Assert.assertTrue(result.message()
            .contains("UDF1 is not abstract and does not override abstract method exec"));
  }
}
