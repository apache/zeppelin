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

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Properties;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.ui.TextBox;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.resource.LocalResourcePool;

public class KotlinSparkInterpreterTest {

  @ClassRule
  public static TemporaryFolder tmpDir = new TemporaryFolder();

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  private static SparkInterpreter repl;
  private static InterpreterGroup intpGroup;
  private static InterpreterContext context;
  private static KotlinSparkInterpreter interpreter;
  private static String output;
  private static boolean sparkSupported;

  public static Properties getSparkTestProperties(TemporaryFolder tmpDir) throws IOException {
    Properties p = new Properties();
    p.setProperty(SparkStringConstants.MASTER_PROP_NAME, "local[*]");
    p.setProperty(SparkStringConstants.APP_NAME_PROP_NAME, "Zeppelin Test");
    p.setProperty("zeppelin.spark.useHiveContext", "true");
    p.setProperty("zeppelin.spark.maxResult", "1000");
    p.setProperty("zeppelin.spark.importImplicit", "true");
    p.setProperty("zeppelin.dep.localrepo", tmpDir.newFolder().getAbsolutePath());
    p.setProperty("zeppelin.spark.property_1", "value_1");
    return p;
  }

  private static void testCodeForResult(String code, String expected) throws Exception {
    InterpreterResult result = interpreter.interpret(code, context);

    String value;
    if (result.message().isEmpty()) {
      value = "";
    } else {
      String message = result.message().get(0).getData().trim();
      // "res0 : kotlin.Int = 1" -> "kotlin.Int = 1"
      value = message.substring(message.indexOf(':') + 2);
    }

    assertEquals(SUCCESS, result.code());
    assertEquals(expected, value);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    context = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .setParagraphTitle("title")
        .setAngularObjectRegistry(new AngularObjectRegistry(intpGroup.getId(), null))
        .setResourcePool(new LocalResourcePool("id"))
        .setInterpreterOut(new InterpreterOutput(null))
        .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
        .build();
    context.out = new InterpreterOutput(
        new InterpreterOutputListener() {
          @Override
          public void onUpdateAll(InterpreterOutput out) {

          }

          @Override
          public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
            try {
              output = out.toInterpreterResultMessage().getData();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

          @Override
          public void onUpdate(int index, InterpreterResultMessageOutput out) {

          }
        });

    InterpreterContext.set(context);

    intpGroup.put("note", new LinkedList<Interpreter>());

    Properties properties = getSparkTestProperties(tmpDir);
    repl = new SparkInterpreter(properties);
    repl.setInterpreterGroup(intpGroup);
    intpGroup.get("note").add(repl);
    repl.open();
    repl.interpret("sc", context);

    interpreter = new KotlinSparkInterpreter(properties);
    interpreter.setInterpreterGroup(intpGroup);
    intpGroup.get("note").add(interpreter);
    try {
      interpreter.open();
      sparkSupported = true;
    } catch (UnsupportedClassVersionError e) {
      sparkSupported = false;
    }
  }

  @AfterClass
  public static void tearDown() throws InterpreterException {
    repl.close();
  }

  @Before
  public void expectUnsupportedError() {
    if (!sparkSupported) {
      exceptionRule.expect(UnsupportedClassVersionError.class);
    }
    Assume.assumeFalse("Spark version should be >= 2.4.", interpreter.isSparkVersionUnsupported());
  }

  @Test
  public void simpleKotlinTest() throws Exception {
    testCodeForResult("1 + 1", "Int = 2");
  }

  @Test
  public void dataFrameTest() throws Exception {
    interpreter.interpret("spark.range(100, 0, -1).sort(\"id\").show(2)", context);
    assertTrue(output.contains(
        "+---+\n" +
        "| id|\n" +
        "+---+\n" +
        "|  1|\n" +
        "|  2|\n" +
        "+---+"));
  }

  @Test
  public void testCancel() throws Exception {
    Thread t = new Thread(() -> {
      try {
        InterpreterResult result = interpreter.interpret(
            "spark.range(10).foreach { Thread.sleep(1000) }", context);
        assertEquals(ERROR, result.code());
        assertTrue(result.message().get(0).getData().trim().contains("cancelled"));
      } catch (UnsupportedClassVersionError e) {
        if (sparkSupported) {
          Assert.fail(e.getMessage());
        }
      } catch (InterpreterException e) {
        Assert.fail(e.getMessage());
      }
    });
    t.start();
    Thread.sleep(1000);
    interpreter.cancel(context);
  }

  @Test
  public void sparkPropertiesTest() throws Exception {
    InterpreterResult result = interpreter.interpret(
        "sc.conf.all.map{ it.toString() }", context);
    String message = result.message().get(0).getData().trim();
    System.out.println("PROPS_1 = " + message);
    assertTrue(message.contains("(zeppelin.spark.property_1,value_1)"));
  }

  @Test
  public void classWriteTest() throws Exception {
    InterpreterResult r0 = interpreter.interpret("val f = { x: Any -> println(x) }", context);
    output = "";
    InterpreterResult result = interpreter.interpret("spark.range(5).foreach(f)", context);
    assertEquals(SUCCESS, result.code());
    assertTrue(output.contains("0"));
    assertTrue(output.contains("1"));
    assertTrue(output.contains("2"));
    assertTrue(output.contains("3"));
    assertTrue(output.contains("4"));

    String classOutputDir = repl.getSparkContext().getConf().get("spark.repl.class.outputDir");
    System.out.println(classOutputDir);

    Path outPath = Paths.get(classOutputDir);
    Files.walk(outPath).forEach(System.out::println);
    assertTrue(Files.walk(outPath).anyMatch(path -> path.toString().matches(
        ".*Line_\\d+_zeppelin\\$f\\$1\\.class")));
    assertTrue(Files.walk(outPath).anyMatch(path -> path.toString().matches(
        ".*Line_\\d+_zeppelin\\$sam\\$org_apache_spark_api_java_function_ForeachFunction\\$0\\.class")));
  }

  @Test
  public void zeppelinContextTest() throws Exception {
    InterpreterResult result = interpreter.interpret("z.input(\"name\", \"default_name\")", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, context.getGui().getForms().size());
    assertTrue(context.getGui().getForms().get("name") instanceof TextBox);
    TextBox textBox = (TextBox) context.getGui().getForms().get("name");
    assertEquals("name", textBox.getName());
    assertEquals("default_name", textBox.getDefaultValue());
  }
}
