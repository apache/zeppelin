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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.junit.Test;

public class ConfInterpreterTest extends AbstractInterpreterTest {

  @Test
  public void testCorrectConf() throws IOException, InterpreterException {
    assertTrue(
        interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test")
            instanceof ConfInterpreter);
    ConfInterpreter confInterpreter =
        (ConfInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test");

    InterpreterContext context =
        InterpreterContext.builder().setNoteId("noteId").setParagraphId("paragraphId").build();

    InterpreterResult result =
        confInterpreter.interpret("property_1\tnew_value\nnew_property\tdummy_value", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);

    assertTrue(
        interpreterFactory.getInterpreter("user1", "note1", "test", "test")
            instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test", "test");
    remoteInterpreter.interpret("hello world", context);
    assertEquals(7, remoteInterpreter.getProperties().size());
    assertEquals("new_value", remoteInterpreter.getProperty("property_1"));
    assertEquals("dummy_value", remoteInterpreter.getProperty("new_property"));
    assertEquals("value_3", remoteInterpreter.getProperty("property_3"));

    // rerun the paragraph with the same properties would result in SUCCESS
    result = confInterpreter.interpret("property_1\tnew_value\nnew_property\tdummy_value", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);

    // run the paragraph with the same properties would result in ERROR
    result =
        confInterpreter.interpret("property_1\tnew_value_2\nnew_property\tdummy_value", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code);
  }

  @Test
  public void testEmptyConf() throws IOException, InterpreterException {
    assertTrue(
        interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test")
            instanceof ConfInterpreter);
    ConfInterpreter confInterpreter =
        (ConfInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test");

    InterpreterContext context =
        InterpreterContext.builder().setNoteId("noteId").setParagraphId("paragraphId").build();
    InterpreterResult result = confInterpreter.interpret("", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);

    assertTrue(
        interpreterFactory.getInterpreter("user1", "note1", "test", "test")
            instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test", "test");
    assertEquals(6, remoteInterpreter.getProperties().size());
    assertEquals("value_1", remoteInterpreter.getProperty("property_1"));
    assertEquals("value_3", remoteInterpreter.getProperty("property_3"));
  }

  @Test
  public void testRunningAfterOtherInterpreter() throws IOException, InterpreterException {
    assertTrue(
        interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test")
            instanceof ConfInterpreter);
    ConfInterpreter confInterpreter =
        (ConfInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test.conf", "test");

    InterpreterContext context =
        InterpreterContext.builder().setNoteId("noteId").setParagraphId("paragraphId").build();

    RemoteInterpreter remoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test", "test");
    InterpreterResult result = remoteInterpreter.interpret("hello world", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code);

    result = confInterpreter.interpret("property_1\tnew_value\nnew_property\tdummy_value", context);
    assertEquals(InterpreterResult.Code.ERROR, result.code);
  }
}
