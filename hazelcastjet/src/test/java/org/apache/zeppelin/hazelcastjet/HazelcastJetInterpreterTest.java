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

package org.apache.zeppelin.hazelcastjet;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * HazelcastJetInterpreterTest
 */
public class HazelcastJetInterpreterTest {

  private static HazelcastJetInterpreter jet;
  private static InterpreterContext context;

  @BeforeClass
  public static void setUp() {
    Properties p = new Properties();
    jet = new HazelcastJetInterpreter(p);
    jet.open();
    context = InterpreterContext.builder().build();
  }

  @AfterClass
  public static void tearDown() {
    jet.close();
  }

  @Test
  public void testStaticRepl() {

    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    out.println("public class HelloWorld {");
    out.println("  public static void main(String args[]) {");
    out.println("    System.out.println(\"This is in another java file\");");
    out.println("  }");
    out.println("}");
    out.close();

    InterpreterResult res = jet.interpret(writer.toString(), context);

    assertEquals(InterpreterResult.Code.SUCCESS, res.code());
    assertEquals(InterpreterResult.Type.TEXT, res.message().get(0).getType());
  }

  @Test
  public void testStaticReplWithoutMain() {

    StringBuffer sourceCode = new StringBuffer();
    sourceCode.append("package org.mdkt;\n");
    sourceCode.append("public class HelloClass {\n");
    sourceCode.append("   public String hello() { return \"hello\"; }");
    sourceCode.append("}");
    InterpreterResult res = jet.interpret(sourceCode.toString(), context);
    assertEquals(InterpreterResult.Code.ERROR, res.code());
  }

  @Test
  public void testStaticReplWithSyntaxError() {

    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);
    out.println("public class HelloWorld {");
    out.println("  public static void main(String args[]) {");
    out.println("    System.out.prin(\"This is in another java file\");");
    out.println("  }");
    out.println("}");
    out.close();
    InterpreterResult res = jet.interpret(writer.toString(), context);

    assertEquals(InterpreterResult.Code.ERROR, res.code());
  }

}
