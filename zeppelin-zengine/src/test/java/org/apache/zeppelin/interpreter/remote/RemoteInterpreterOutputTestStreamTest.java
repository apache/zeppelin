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

package org.apache.zeppelin.interpreter.remote;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Test for remote interpreter output stream
 */
class RemoteInterpreterOutputTestStreamTest extends AbstractInterpreterTest {


  private InterpreterSetting interpreterSetting;
  private String noteId;

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    interpreterSetting = interpreterSettingManager.get("test");
    noteId = notebook.createNote(File.separator + RandomStringUtils.randomAlphabetic(6),
        AuthenticationInfo.ANONYMOUS);
  }

  @Override
  @AfterEach
  public void tearDown() throws Exception {
    interpreterSetting.close();
    notebook.removeNote(noteId, AuthenticationInfo.ANONYMOUS);
    super.tearDown();
  }

  private InterpreterContext createInterpreterContext() {
    return InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("id")
        .build();
  }

  @Test
  void testInterpreterResultOnly() throws InterpreterException, IOException {
    RemoteInterpreter intp = (RemoteInterpreter) interpreterSetting.getInterpreter("user1", noteId, "mock_stream");
    InterpreterResult ret = intp.interpret("SUCCESS::staticresult", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("staticresult", ret.message().get(0).getData());

    ret = intp.interpret("SUCCESS::staticresult2", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("staticresult2", ret.message().get(0).getData());

    ret = intp.interpret("ERROR::staticresult3", createInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertEquals("staticresult3", ret.message().get(0).getData());
  }

  @Test
  void testInterpreterOutputStreamOnly() throws InterpreterException {
    RemoteInterpreter intp =
        (RemoteInterpreter) interpreterSetting.getInterpreter("user1", noteId, "mock_stream");
    InterpreterResult ret = intp.interpret("SUCCESS:streamresult:", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("streamresult", ret.message().get(0).getData());

    ret = intp.interpret("ERROR:streamresult2:", createInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertEquals("streamresult2", ret.message().get(0).getData());
  }

  @Test
  void testInterpreterResultOutputStreamMixed() throws InterpreterException {
    RemoteInterpreter intp =
        (RemoteInterpreter) interpreterSetting.getInterpreter("user1", noteId, "mock_stream");
    InterpreterResult ret = intp.interpret("SUCCESS:stream:static", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("stream", ret.message().get(0).getData());
    assertEquals("static", ret.message().get(1).getData());
  }

  @Test
  void testOutputType() throws InterpreterException {
    RemoteInterpreter intp =
        (RemoteInterpreter) interpreterSetting.getInterpreter("user1", noteId, "mock_stream");

    InterpreterResult ret = intp.interpret("SUCCESS:%html hello:", createInterpreterContext());
    assertEquals(InterpreterResult.Type.HTML, ret.message().get(0).getType());
    assertEquals("hello", ret.message().get(0).getData());

    ret = intp.interpret("SUCCESS:%html\nhello:", createInterpreterContext());
    assertEquals(InterpreterResult.Type.HTML, ret.message().get(0).getType());
    assertEquals("hello", ret.message().get(0).getData());

    ret = intp.interpret("SUCCESS:%html hello:%angular world", createInterpreterContext());
    assertEquals(InterpreterResult.Type.HTML, ret.message().get(0).getType());
    assertEquals("hello", ret.message().get(0).getData());
    assertEquals(InterpreterResult.Type.ANGULAR, ret.message().get(1).getType());
    assertEquals("world", ret.message().get(1).getData());
  }

}
