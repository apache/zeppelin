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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InterpreterOutputTest {
  private InterpreterOutput out;


  @Before
  public void setUp() {
    out = new InterpreterOutput();
  }

  @After
  public void tearDown() throws IOException {
    out.close();
  }


  @Test
  public void testWrite() throws IOException {
    out.write(1);
    assertEquals(1, out.toByteArray()[0]);
  }

  @Test
  public void testStringWrite() throws IOException {
    Writer writer = new OutputStreamWriter(out);
    writer.write("hello");
    writer.flush();
    assertEquals("hello", new String(out.toByteArray()));
  }
}