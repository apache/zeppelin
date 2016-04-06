/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.flink;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class FlinkInterpreterTest {

  private static FlinkInterpreter flink;
  private static InterpreterContext context;

  @BeforeClass
  public static void setUp() {
    Properties p = new Properties();
    flink = new FlinkInterpreter(p);
    flink.open();
    context = new InterpreterContext(null, null, null, null, null, null, null, null, null, null, null);
  }

  @AfterClass
  public static void tearDown() {
    flink.close();
    flink.destroy();
  }

  @Test
  public void testNextLineInvocation() {
    assertEquals(InterpreterResult.Code.SUCCESS, flink.interpret("\"123\"\n.toInt", context).code());
  }

  @Test
  public void testNextLineComments() {
    assertEquals(InterpreterResult.Code.SUCCESS, flink.interpret("\"123\"\n/*comment here\n*/.toInt", context).code());
  }

  @Test
  public void testNextLineCompanionObject() {
    String code = "class Counter {\nvar value: Long = 0\n}\n // comment\n\n object Counter {\n def apply(x: Long) = new Counter()\n}";
    assertEquals(InterpreterResult.Code.SUCCESS, flink.interpret(code, context).code());
  }

  @Test
  public void testSimpleStatement() {
    InterpreterResult result = flink.interpret("val a=1", context);
    result = flink.interpret("print(a)", context);
    assertEquals("1", result.message());
  }

  @Test
  public void testSimpleStatementWithSystemOutput() {
    InterpreterResult result = flink.interpret("val a=1", context);
    result = flink.interpret("System.out.print(a)", context);
    assertEquals("1", result.message());
  }

  @Test
  public void testWordCount() {
    flink.interpret("val text = env.fromElements(\"To be or not to be\")", context);
    flink.interpret("val counts = text.flatMap { _.toLowerCase.split(\" \") }.map { (_, 1) }.groupBy(0).sum(1)", context);
    InterpreterResult result = flink.interpret("counts.print()", context);
    assertEquals(Code.SUCCESS, result.code());

    String[] expectedCounts = {"(to,2)", "(be,2)", "(or,1)", "(not,1)"};
    Arrays.sort(expectedCounts);

    String[] counts = result.message().split("\n");
    Arrays.sort(counts);

    assertArrayEquals(expectedCounts, counts);
  }
}
