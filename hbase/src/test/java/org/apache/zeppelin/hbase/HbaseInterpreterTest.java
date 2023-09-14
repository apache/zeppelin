/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.hbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for HBase Interpreter.
 */
public class HbaseInterpreterTest {
  private static HbaseInterpreter hbaseInterpreter;

  @BeforeAll
  public static void setUp() throws NullPointerException, InterpreterException {
    Properties properties = new Properties();
    properties.put("hbase.home", "");
    properties.put("hbase.ruby.sources", "");
    properties.put("zeppelin.hbase.test.mode", "true");

    hbaseInterpreter = new HbaseInterpreter(properties);
    hbaseInterpreter.open();
  }

  @Test
  void newObject() {
    assertNotNull(hbaseInterpreter);
  }

  @Test
  void putsTest() {
    InterpreterResult result = hbaseInterpreter.interpret("puts \"Hello World\"", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("Hello World\n", result.message().get(0).getData());
  }

  public void putsLoadPath() {
    InterpreterResult result = hbaseInterpreter.interpret(
            "require 'two_power'; puts twoToThePowerOf(4)", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("16\n", result.message().get(0).getData());
  }

  @Test
  void testException() {
    InterpreterResult result = hbaseInterpreter.interpret("plot practical joke", null);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertEquals("(NameError) undefined local variable or method `joke' for main:Object",
            result.message().get(0).getData());
  }
}
