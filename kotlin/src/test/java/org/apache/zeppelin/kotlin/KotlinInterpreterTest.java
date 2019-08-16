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

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.SUCCESS;
import static org.junit.Assert.assertEquals;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.kotlin.receiver.ZeppelinKotlinReceiver;


public class KotlinInterpreterTest {

  private static KotlinInterpreter interpreter;
  private static InterpreterContext context;

  private static volatile String output = "";

  @BeforeClass
  public static void setUp() throws InterpreterException {
    context = getInterpreterContext();
    interpreter = new KotlinInterpreter(new Properties());

    String cp = System.getProperty("java.class.path") + File.pathSeparator +
        ZeppelinKotlinReceiver.class.getProtectionDomain().getCodeSource().getLocation().getPath();

    interpreter.getBuilder().compilerOptions(Arrays.asList("-classpath", cp));
    interpreter.open();
  }

  @AfterClass
  public static void tearDown() {
    interpreter.close();
  }

  private static void testCodeForResult(String code, String expected) throws Exception {
    InterpreterResult result = interpreter.interpret(code, context);

    String value;
    if (result.message().isEmpty()) {
      value = "";
    } else {
      String message = result.message().get(0).getData().trim();
      System.err.println(message);
      // "res0 : kotlin.Int = 1" -> "kotlin.Int = 1"
      value = message.substring(message.indexOf(':') + 2);
    }

    assertEquals(SUCCESS, result.code());
    assertEquals(expected, value);
  }

  @Test
  public void testLiteral() throws Exception {
    testCodeForResult("1", "kotlin.Int = 1");
  }

  @Test
  public void testOperation() throws Exception {
    testCodeForResult("\"foo\" + \"bar\"", "kotlin.String = foobar");
  }

  @Test
  public void testFunction() throws Exception {
    testCodeForResult(
        "fun square(x: Int): Int = x * x\nsquare(10)",
        "kotlin.Int = 100");
  }

  // TODO(dkaznacheev): work out why it's not incomplete
  public void testIncomplete() throws Exception {
    InterpreterResult result = interpreter.interpret("if (10 > 2) {\n", context);
    assertEquals(ERROR, result.code());
    assertEquals("incomplete code", result.message().get(0).getData().trim());
  }

  @Test
  public void testCompileError() throws Exception {
    InterpreterResult result = interpreter.interpret("prinln(1)", context);
    assertEquals(ERROR, result.code());
    assertEquals("Unresolved reference: prinln", result.message().get(0).getData().trim());
  }

  @Test
  public void testOutput() throws Exception {
    testCodeForResult("println(\"Hello Kotlin\")", "");
    assertEquals("Hello Kotlin\n", output);
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
