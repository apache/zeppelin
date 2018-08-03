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

package org.apache.zeppelin.java;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class JavaInterpreterUtilsTest {

    private static final String TABLE_RESULT_1 = "%table\n" +
            "Word\tCount\n" +
            "world\t5\n" +
            "hello\t4";

    private static JavaInterpreter java;
    private static InterpreterContext context;

    @BeforeClass
    public static void setUp() {
        Properties p = new Properties();
        java = new JavaInterpreter(p);
        java.open();
        context = InterpreterContext.builder().build();
    }

    @AfterClass
    public static void tearDown() {
        java.close();
    }

    @Test
    public void testDisplayTableFromSimpleMapUtil() {

        Map<String, Long> counts = new HashMap<>();
        counts.put("hello",4L);
        counts.put("world",5L);

        assertEquals(
                TABLE_RESULT_1,
                JavaInterpreterUtils.displayTableFromSimpleMap("Word", "Count", counts)
        );

    }

    @Test
    public void testStaticReplWithDisplayTableFromSimpleMapUtilReturnTableType() {

        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("import java.util.HashMap;");
        out.println("import java.util.Map;");
        out.println("import org.apache.zeppelin.java.JavaInterpreterUtils;");
        out.println("public class HelloWorld {");
        out.println("  public static void main(String args[]) {");
        out.println("    Map<String, Long> counts = new HashMap<>();");
        out.println("    counts.put(\"hello\",4L);");
        out.println("    counts.put(\"world\",5L);");
        out.println("    System.out.println(JavaInterpreterUtils.displayTableFromSimpleMap(\"Word\", \"Count\", counts));");
        out.println("  }");
        out.println("}");
        out.close();

        InterpreterResult res = java.interpret(writer.toString(), context);

        assertEquals(InterpreterResult.Code.SUCCESS, res.code());
        assertEquals(InterpreterResult.Type.TABLE, res.message().get(0).getType());
    }

}
