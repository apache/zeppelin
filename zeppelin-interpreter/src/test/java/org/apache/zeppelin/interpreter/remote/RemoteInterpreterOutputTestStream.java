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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;


/**
 * Test for remote interpreter output stream
 */
public class RemoteInterpreterOutputTestStream implements RemoteInterpreterProcessListener {
  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";
  private InterpreterGroup intpGroup;
  private HashMap<String, String> env;

  @Before
  public void setUp() throws Exception {
    intpGroup = new InterpreterGroup();
    intpGroup.put("note", new LinkedList<Interpreter>());

    env = new HashMap<>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());
  }

  @After
  public void tearDown() throws Exception {
    intpGroup.close();
  }

  private RemoteInterpreter createMockInterpreter() {
    RemoteInterpreter intp = new RemoteInterpreter(
        new Properties(),
        "note",
        MockInterpreterOutputStream.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        this,
        null,
        "anonymous",
        false);

    intpGroup.get("note").add(intp);
    intp.setInterpreterGroup(intpGroup);
    return intp;
  }

  private InterpreterContext createInterpreterContext() {
    return new InterpreterContext(
        "noteId",
        "id",
        null,
        "title",
        "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        null,
        new LinkedList<InterpreterContextRunner>(), null);
  }

  @Test
  public void testInterpreterResultOnly() {
    RemoteInterpreter intp = createMockInterpreter();
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
  public void testInterpreterOutputStreamOnly() {
    RemoteInterpreter intp = createMockInterpreter();
    InterpreterResult ret = intp.interpret("SUCCESS:streamresult:", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("streamresult", ret.message().get(0).getData());

    ret = intp.interpret("ERROR:streamresult2:", createInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, ret.code());
    assertEquals("streamresult2", ret.message().get(0).getData());
  }

  @Test
  public void testInterpreterResultOutputStreamMixed() {
    RemoteInterpreter intp = createMockInterpreter();
    InterpreterResult ret = intp.interpret("SUCCESS:stream:static", createInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, ret.code());
    assertEquals("stream", ret.message().get(0).getData());
    assertEquals("static", ret.message().get(1).getData());
  }

  @Test
  public void testOutputType() {
    RemoteInterpreter intp = createMockInterpreter();

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

  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String output) {

  }

  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index, InterpreterResult.Type type, String output) {

  }

  @Override
  public void onOutputClear(String noteId, String paragraphId) {

  }

  @Override
  public void onMetaInfosReceived(String settingId, Map<String, String> metaInfos) {

  }

  @Override
  public void onGetParagraphRunners(String noteId, String paragraphId, RemoteWorksEventListener callback) {
    if (callback != null) {
      callback.onFinished(new LinkedList<>());
    }
  }

  @Override
  public void onRemoteRunParagraph(String noteId, String ParagraphID) throws Exception {

  }
}
