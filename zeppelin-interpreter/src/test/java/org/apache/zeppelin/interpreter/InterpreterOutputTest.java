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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class InterpreterOutputTest implements InterpreterOutputListener {
  private InterpreterOutput out;
  int numAppendEvent;
  int numUpdateEvent;

  @Before
  public void setUp() {
    out = new InterpreterOutput(this);
    numAppendEvent = 0;
    numUpdateEvent = 0;
  }

  @After
  public void tearDown() throws IOException {
    out.close();
  }

  @Test
  public void testDetectNewline() throws IOException {
    out.write("hello\nworld");
    assertEquals(1, out.size());
    assertEquals(InterpreterResult.Type.TEXT, out.getOutputAt(0).getType());
    assertEquals("hello\n", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(1, numAppendEvent);
    assertEquals(1, numUpdateEvent);

    out.write("\n");
    assertEquals("hello\nworld\n", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(2, numAppendEvent);
    assertEquals(1, numUpdateEvent);
  }

  @Test
  public void testFlush() throws IOException {
    out.write("hello\nworld");
    assertEquals("hello\n", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(1, numAppendEvent);
    assertEquals(1, numUpdateEvent);

    out.flush();
    assertEquals("hello\nworld", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(2, numAppendEvent);
    assertEquals(1, numUpdateEvent);

    out.clear();
    out.write("%html div");
    assertEquals("", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(0).getType());

    out.flush();
    assertEquals("div", new String(out.getOutputAt(0).toByteArray()));
  }


  @Test
  public void testType() throws IOException {
    // default output stream type is TEXT
    out.write("Text\n");
    assertEquals(InterpreterResult.Type.TEXT, out.getOutputAt(0).getType());
    assertEquals("Text\n", new String(out.getOutputAt(0).toByteArray()));
    assertEquals(1, numAppendEvent);
    assertEquals(1, numUpdateEvent);

    // change type
    out.write("%html\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(1).getType());
    assertEquals("", new String(out.getOutputAt(1).toByteArray()));
    assertEquals(1, numAppendEvent);
    assertEquals(1, numUpdateEvent);

    // none TEXT type output stream does not generate append event
    out.write("<div>html</div>\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(1).getType());
    assertEquals(1, numAppendEvent);
    assertEquals(2, numUpdateEvent);
    out.flush();
    assertEquals("<div>html</div>\n", new String(out.getOutputAt(1).toByteArray()));

    // change type to text again
    out.write("%text hello\n");
    assertEquals(InterpreterResult.Type.TEXT, out.getOutputAt(2).getType());
    assertEquals(2, numAppendEvent);
    assertEquals(4, numUpdateEvent);
    assertEquals("hello\n", new String(out.getOutputAt(2).toByteArray()));
  }

  @Test
  public void testChangeTypeInTheBeginning() throws IOException {
    out.write("%html\nHello");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(0).getType());
  }

  @Test
  public void testChangeTypeWithMultipleNewline() throws IOException {
    out.write("%html\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(0).getType());

    out.write("%text\n");
    assertEquals(InterpreterResult.Type.TEXT, out.getOutputAt(1).getType());

    out.write("\n%html\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(2).getType());

    out.write("\n\n%text\n");
    assertEquals(InterpreterResult.Type.TEXT, out.getOutputAt(3).getType());

    out.write("\n\n\n%html\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(4).getType());
  }

  @Test
  public void testChangeTypeWithoutData() throws IOException {
    out.write("%html\n%table\n");
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(0).getType());
    assertEquals(InterpreterResult.Type.TABLE, out.getOutputAt(1).getType());
  }

  @Test
  public void testMagicData() throws IOException {
    out.write("%table col1\tcol2\n\n%html <h3> This is a hack </h3>\t234\n".getBytes());
    assertEquals(InterpreterResult.Type.TABLE, out.getOutputAt(0).getType());
    assertEquals(InterpreterResult.Type.HTML, out.getOutputAt(1).getType());
    assertEquals("col1\tcol2\n", new String(out.getOutputAt(0).toByteArray()));
    out.flush();
    assertEquals("<h3> This is a hack </h3>\t234\n", new String(out.getOutputAt(1).toByteArray()));
  }


  @Test
  public void testTableCellFormatting() throws IOException {
    out.write("%table col1\tcol2\n\n%html val1\tval2\n".getBytes());
    assertEquals(InterpreterResult.Type.TABLE, out.getOutputAt(0).getType());
    assertEquals("col1\tcol2\n", new String(out.getOutputAt(0).toByteArray()));
    out.flush();
    assertEquals("val1\tval2\n", new String(out.getOutputAt(1).toByteArray()));
  }


  @Override
  public void onUpdateAll(InterpreterOutput out) {

  }

  @Override
  public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
    numAppendEvent++;
  }

  @Override
  public void onUpdate(int index, InterpreterResultMessageOutput out) {
    numUpdateEvent++;
  }
}