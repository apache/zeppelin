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

package org.apache.zeppelin.kotlin;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.kotlin.script.KotlinFunctionInfo;
import org.apache.zeppelin.kotlin.script.KotlinVariableInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.INCOMPLETE;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.SUCCESS;
import static org.apache.zeppelin.kotlin.script.KotlinReflectUtil.shorten;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class KotlinInterpreterTest {

  private static KotlinInterpreter interpreter;
  private static InterpreterContext context;

  private static volatile String output = "";

  public void prepareInterpreter() {
    prepareInterpreter(new Properties());
  }

  public void prepareInterpreter(Properties properties) {
    context = getInterpreterContext();
    interpreter = new KotlinInterpreter(properties);
    output = "";
  }

  @Before
  public void setUp() throws InterpreterException {
    prepareInterpreter();
    interpreter.open();
  }

  @After
  public void tearDown() {
    interpreter.close();
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

  @Test
  public void testLiteral() throws Exception {
    testCodeForResult("1", "Int = 1");
  }

  @Test
  public void testOperation() throws Exception {
    testCodeForResult("\"foo\" + \"bar\"", "String = foobar");
  }

  @Test
  public void testFunction() throws Exception {
    testCodeForResult(
        "fun square(x: Int): Int = x * x\nsquare(10)",
        "Int = 100");
  }

  @Test
  public void testIncomplete() throws Exception {
    InterpreterResult result = interpreter.interpret("val x =", context);
    assertEquals(INCOMPLETE, result.code());
  }

  @Test
  public void testCompileError() throws Exception {
    InterpreterResult result = interpreter.interpret("prinln(1)", context);
    assertEquals(ERROR, result.code());
    String actualMessage = result.message().get(0).getData().trim();
    assertEquals("(1:1 - 7) Unresolved reference: prinln", actualMessage);
  }

  @Test
  public void testOutput() throws Exception {
    testCodeForResult("println(\"Hello Kotlin\")", "");
    assertEquals("Hello Kotlin" + System.lineSeparator(), output);
  }

  @Test
  public void testRuntimeError() throws Exception {
    InterpreterResult result = interpreter.interpret(
        "throw RuntimeException(\"Error Message\")", context);
    assertEquals(ERROR, result.code());

    String errorWithStack = result.message().get(0).getData().trim();
    int firstLineEnd = errorWithStack.indexOf(System.lineSeparator());
    String error = errorWithStack.substring(0, firstLineEnd);
    assertEquals("java.lang.RuntimeException: Error Message", error);
  }

  @Test
  public void testCancel() throws Exception {
    Thread t = new Thread(() -> {
      try {
        InterpreterResult result = interpreter.interpret(
            "repeat(10000000) { Thread.sleep(100) }", context);
        assertEquals(ERROR, result.code());
        assertEquals("sleep interrupted", result.message().get(0).getData().trim());
      } catch (InterpreterException e) {
        Assert.fail(e.getMessage());
      }
    });
    t.start();
    Thread.sleep(200);
    interpreter.cancel(context);
  }

  @Test
  public void testVariables() throws Exception {
    interpreter.interpret("val x = 1", context);
    interpreter.interpret("val x = 2", context);
    List<KotlinVariableInfo> vars = interpreter.getVariables();
    assertEquals(1, vars.size());

    KotlinVariableInfo varX = vars.stream()
        .filter(info -> info.getName().equals("x"))
        .findFirst()
        .orElseGet( () -> {
          Assert.fail();
          return null;
        });

    assertEquals(2, varX.getValue());
    assertEquals("kotlin.Int", varX.getType());
  }

  @Test
  public void testGetVariablesFromCode() throws Exception {
    interpreter.interpret("val x = 1", context);
    interpreter.interpret("val y = 2", context);
    interpreter.interpret("val x = 3", context);
    interpreter.interpret("val l = listOf(1,2,3)", context);
    interpreter.interpret("kc.showVars()", context);
    System.out.println(output);
    assertTrue(output.contains("x: Int = 3"));
    assertTrue(output.contains("y: Int = 2"));
    assertTrue(output.contains("l: List<Int> = [1, 2, 3]"));
    InterpreterResult res = interpreter.interpret("kc.vars = null", context);
    assertTrue(res.message().get(0).getData().contains("Val cannot be reassigned"));
  }

  @Test
  public void testFunctionsAsValues() throws Exception {
    System.out.println(interpreter.interpret("val f = { x: Int -> x + 1 }", context));
    System.out.println(interpreter.getVariables());
  }

  @Test
  public void testMethods() throws Exception {
    interpreter.interpret("fun sq(x: Int): Int = x * x", context);
    interpreter.interpret("fun sq(x: Int): Int = x * x", context);
    assertEquals(1, interpreter.getFunctions().size());

    interpreter.interpret("fun <T> singletonListOf(elem: T): List<T> = listOf(elem)", context);
    List<String> signatures = interpreter.getFunctions().stream()
        .map(KotlinFunctionInfo::toString).collect(Collectors.toList());
    System.out.println(signatures);
    assertTrue(signatures.stream().anyMatch(signature ->
        signature.equals("fun sq(kotlin.Int): kotlin.Int")));
    assertTrue(signatures.stream().anyMatch(signature ->
        signature.equals("fun singletonListOf(T): kotlin.collections.List<T>")));
  }

  @Test
  public void testCompletion() throws Exception {
    interpreter.interpret("val x = 1", context);
    interpreter.interpret("fun inc(n: Int): Int = n + 1", context);
    List<InterpreterCompletion> completions = interpreter.completion("", 0, context);
    assertTrue(completions.stream().anyMatch(c -> c.name.equals("x")));
    assertTrue(completions.stream().anyMatch(c -> c.value.equals("inc(Int)")));
  }

  @Test
  public void testOutputClasses() throws Exception {
    prepareInterpreter();
    Path tempPath = Files.createTempDirectory("tempKotlinClasses");
    interpreter.getKotlinReplProperties().outputDir(tempPath.toAbsolutePath().toString());
    interpreter.open();
    interpreter.interpret("val x = 1\nx", context);
    File[] dir = tempPath.toFile().listFiles();
    assertNotNull(dir);
    assertTrue(dir.length > 0);
    System.out.println(tempPath);
    assertTrue(Arrays.stream(dir)
        .anyMatch(file -> file.getName().matches("Line_\\d+_zeppelin\\.class")));
    int oldLength = dir.length;
    interpreter.interpret("x + 1", context);
    dir = tempPath.toFile().listFiles();
    assertNotNull(dir);
    assertTrue(dir.length > oldLength);
  }

  @Test
  public void testWrapper() throws Exception {
    String code = "import org.apache.zeppelin.kotlin.script.InvokeWrapper\n" +
            "var k = 0\n" +
            "val wrapper = object : InvokeWrapper {\n" +
            "    override operator fun <T> invoke(body: () -> T): T {\n" +
            "        println(\"START\")\n" +
            "        val result = body()\n" +
            "        println(\"END\")\n" +
            "        k = k + 1\n" +
            "        return result\n" +
            "    }\n" +
            "}\n" +
            "kc.wrapper = wrapper\n";
    interpreter.interpret(code, context);
    interpreter.interpret("println(\"hello!\")", context);
    List<KotlinVariableInfo> vars = interpreter.getVariables();
    for (KotlinVariableInfo v: vars) {
      if (v.getName().equals("k")) {
        assertEquals(1, v.getValue());
      }
    }

    InterpreterResult result = interpreter.interpret("kc.vars", context);
    String resultAsString = result.message().get(0).getData();
    assertTrue(resultAsString.contains("k: kotlin.Int = 1"));
  }

  @Test
  public void testReflectUtil() throws Exception {
    String message = interpreter.interpret("1", context)
        .message().get(0).getData();
    assertTrue(shorten(message).contains("Int = 1"));

    interpreter.interpret("val f = { l: List<Int> -> l[0] }", context);
    message = interpreter.interpret("f", context)
        .message().get(0).getData();
    assertTrue(shorten(message).contains("(List<Int>) -> Int"));

    interpreter.interpret("fun first(s: String): Char = s[0]", context);
    KotlinFunctionInfo first = interpreter.getFunctions().get(0);
    assertEquals("fun first(String): Char", first.toString(true));
  }

  @Test
  public void fullTypeNamesTest() throws Exception {
    prepareInterpreter();
    interpreter.getKotlinReplProperties().shortenTypes(false);
    interpreter.open();

    interpreter.interpret("val s = \"abc\"", context);
    interpreter.interpret("fun f(l: List<String>) { }", context);
    interpreter.interpret("kc.showFunctions()", context);
    String newLine = System.lineSeparator();
    assertEquals(
            "fun f(kotlin.collections.List<kotlin.String>): kotlin.Unit" + newLine,
            output);
    output = "";
    interpreter.interpret("kc.showVars()", context);
    System.out.println(output);
    assertTrue(output.contains("s: kotlin.String = abc"));
  }

  private static InterpreterContext getInterpreterContext() {
    output = "";
    InterpreterContext context = InterpreterContext.builder()
        .setInterpreterOut(new InterpreterOutput(null))
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
    return context;
  }
}
