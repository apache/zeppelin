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

import org.apache.zeppelin.interpreter.InterpreterException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for HBase Interpreter.
 */
public class HbaseInterpreterTest {
  private static HbaseInterpreter hbaseInterpreter;

  @BeforeAll
  public static void setUp() throws NullPointerException, InterpreterException {
    Properties properties = new Properties();
    properties.put("hbase.home", "");

    hbaseInterpreter = new HbaseInterpreter(properties);
    hbaseInterpreter.open();
  }

  @Test
  void newObject() {
    assertNotNull(hbaseInterpreter);
  }
}
