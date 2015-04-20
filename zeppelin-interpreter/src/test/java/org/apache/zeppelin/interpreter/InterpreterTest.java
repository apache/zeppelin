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

package org.apache.zeppelin.interpreter;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterA;
import org.junit.Test;

public class InterpreterTest {

  @Test
  public void testDefaultProperty() {
    Properties p = new Properties();
    MockInterpreterA intp = new MockInterpreterA(p);

    assertEquals(1, intp.getProperty().size());
    assertEquals("v1", intp.getProperty().get("p1"));
    assertEquals("v1", intp.getProperty("p1"));
  }

  @Test
  public void testOverridedProperty() {
    Properties p = new Properties();
    p.put("p1", "v2");
    MockInterpreterA intp = new MockInterpreterA(p);

    assertEquals(1, intp.getProperty().size());
    assertEquals("v2", intp.getProperty().get("p1"));
    assertEquals("v2", intp.getProperty("p1"));
  }

  @Test
  public void testAdditionalProperty() {
    Properties p = new Properties();
    p.put("p2", "v2");
    MockInterpreterA intp = new MockInterpreterA(p);

    assertEquals(2, intp.getProperty().size());
    assertEquals("v1", intp.getProperty().get("p1"));
    assertEquals("v1", intp.getProperty("p1"));
    assertEquals("v2", intp.getProperty().get("p2"));
    assertEquals("v2", intp.getProperty("p2"));
  }

}
