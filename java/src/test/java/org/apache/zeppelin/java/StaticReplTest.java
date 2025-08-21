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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thoughtworks.qdox.parser.ParseException;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class StaticReplTest {

  @Test
  void testExecuteWithSimplePrint() throws Exception {
    String code = "public class SimpleClass { public static void main(String[] args) { System.out.println(\"Hello World\");} }";

    String result = StaticRepl.execute("generatedClassName", code);

    assertTrue(result.contains("Hello World"));
  }

  @Test
  void testExecuteWithNoMainMethod() {
    String code = "public class NoMain { public void hello() { System.out.println(\"Hello\");} }";

    Exception exception = assertThrows(Exception.class, () ->
        StaticRepl.execute("generatedClassName", code)
    );

    assertTrue(
        exception.getMessage().contains("There isn't any class containing static main method."));
  }

  @Test
  void testExecuteWithSyntaxError() {
    String code = "public class BadCode { public static void main(String[] args) { System.out.println(\"Missing braces\");  }";

    Exception exception = assertThrows(ParseException.class, () ->
        StaticRepl.execute("generatedClassName", code)
    );

    assertTrue(exception.getMessage().contains("syntax error"));
  }

  @Test
  public void testCompilationFailure() {
    String badCode = "public class Bad { public static void main(String[] args) { int x = \"inCompatible\";} } ";

    Exception exception = assertThrows(Exception.class,
        () -> StaticRepl.execute("generatedClassName", badCode));

    assertTrue(exception.getMessage().contains("line 1 : incompatible types"));
  }


  @Test
  public void testExecutionFailure() {
    String code = "public class FailRun { public static void main(String[] args) { throw new RuntimeException(\"fail\"); } }";

    Exception exception = assertThrows(Exception.class, () -> StaticRepl.execute("generatedClassName", code));

    assertTrue(exception.getCause() instanceof InvocationTargetException);
    assertTrue(exception.getMessage().contains("Caused by: java.lang.RuntimeException: fail"));
  }
}