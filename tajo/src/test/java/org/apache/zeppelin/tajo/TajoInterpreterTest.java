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

package org.apache.zeppelin.tajo;

import com.google.gson.JsonParseException;
import org.apache.tajo.jdbc.TajoDriver;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tajo interpreter unit tests
 */
public class TajoInterpreterTest {
  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() {
    TajoInterpreter t = new TesterTajoInterpreter(new Properties());
    t.open();

    Class clazz;
    try {
      clazz = Class.forName(t.TAJO_DRIVER_NAME);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new JsonParseException(e);
    }

    // check tajo jdbc driver
    assertNotNull(clazz);

    // simple select test
    InterpreterResult result = t.interpret("select * from t", null);
    assertEquals(result.type(), InterpreterResult.Type.TABLE);

    // explain test
    result = t.interpret("explain select * from t", null);
    assertEquals(result.type(), InterpreterResult.Type.TEXT);
    t.close();
  }
}