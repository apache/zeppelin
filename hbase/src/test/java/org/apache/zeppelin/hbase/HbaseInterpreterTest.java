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

import org.apache.log4j.BasicConfigurator;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

/**
 * Tests for HBase Interpreter
 */
public class HbaseInterpreterTest {
  private static Logger logger = LoggerFactory.getLogger(HbaseInterpreterTest.class);
  private static HbaseInterpreter hbaseInterpreter;

  @BeforeClass
  public static void setUp() throws NullPointerException {
    BasicConfigurator.configure();
    Properties properties = new Properties();
    properties.put("hbase.home", "");
    properties.put("hbase.ruby.sources", "");
    properties.put("zeppelin.hbase.test.mode", "true");

    hbaseInterpreter = new HbaseInterpreter(properties);
    hbaseInterpreter.open();
  }
  
  @Test
  public void newObject() {
    assertThat(hbaseInterpreter, notNullValue());
  }

  @Test
  public void putsTest() {
    InterpreterResult result = hbaseInterpreter.interpret("puts \"Hello World\"", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(result.type(), InterpreterResult.Type.TEXT);
    assertEquals("Hello World\n", result.message());
  }
  
  public void putsLoadPath() {
    InterpreterResult result = hbaseInterpreter.interpret("require 'two_power'; puts twoToThePowerOf(4)", null);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(result.type(), InterpreterResult.Type.TEXT);
    assertEquals("16\n", result.message());
  }

  @Test
  public void testException() {
    InterpreterResult result = hbaseInterpreter.interpret("plot practical joke", null);
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertEquals("(NameError) undefined local variable or method `joke' for main:Object", result.message());
  }
}
