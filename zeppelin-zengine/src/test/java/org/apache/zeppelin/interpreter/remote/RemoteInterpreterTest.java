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

import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.display.ui.OptionInput;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.remote.mock.GetAngularObjectSizeInterpreter;
import org.apache.zeppelin.interpreter.remote.mock.GetEnvPropertyInterpreter;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RemoteInterpreterTest {


  private static final String INTERPRETER_SCRIPT =
      System.getProperty("os.name").startsWith("Windows") ?
          "../bin/interpreter.cmd" :
          "../bin/interpreter.sh";

  private InterpreterSetting interpreterSetting;

  @Before
  public void setUp() throws Exception {
    InterpreterOption interpreterOption = new InterpreterOption();

    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo3 = new InterpreterInfo(SleepInterpreter.class.getName(), "sleep", false, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo4 = new InterpreterInfo(GetEnvPropertyInterpreter.class.getName(), "get", false, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo5 = new InterpreterInfo(GetAngularObjectSizeInterpreter.class.getName(), "angular_obj",false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    interpreterInfos.add(interpreterInfo3);
    interpreterInfos.add(interpreterInfo4);
    interpreterInfos.add(interpreterInfo5);
    InterpreterRunner runner = new InterpreterRunner(INTERPRETER_SCRIPT, INTERPRETER_SCRIPT);
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("test")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setRunner(runner)
        .setInterpreterDir("../interpeters/test")
        .create();
  }

  @After
  public void tearDown() throws Exception {
    interpreterSetting.close();
  }

  @Test
  public void testSharedMode() throws InterpreterException, IOException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;
    assertTrue(interpreter2 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter2 = (RemoteInterpreter) interpreter2;

    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
    assertEquals(Interpreter.FormType.NATIVE, interpreter1.getFormType());
    assertEquals(0, remoteInterpreter1.getProgress(context1));
    assertNotNull(remoteInterpreter1.getOrCreateInterpreterProcess());
    assertTrue(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());

    assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
    assertEquals(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess(),
        remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());

    // Call InterpreterGroup.close instead of Interpreter.close, otherwise we will have the
    // RemoteInterpreterProcess leakage.
    remoteInterpreter1.getInterpreterGroup().close(remoteInterpreter1.getSessionId());
    assertNull(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess());
    try {
      assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
      fail("Should not be able to call getProgress after RemoterInterpreterProcess is stoped");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testScopedMode() throws InterpreterException, IOException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SCOPED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;
    assertTrue(interpreter2 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter2 = (RemoteInterpreter) interpreter2;

    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
    assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
    assertEquals(Interpreter.FormType.NATIVE, interpreter1.getFormType());
    assertEquals(0, remoteInterpreter1.getProgress(context1));

    assertNotNull(remoteInterpreter1.getOrCreateInterpreterProcess());
    assertTrue(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());

    assertEquals(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess(),
        remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
    // Call InterpreterGroup.close instead of Interpreter.close, otherwise we will have the
    // RemoteInterpreterProcess leakage.
    remoteInterpreter1.getInterpreterGroup().close(remoteInterpreter1.getSessionId());
    try {
      assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }

    assertTrue(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());
    assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
    remoteInterpreter2.getInterpreterGroup().close(remoteInterpreter2.getSessionId());
    try {
      assertEquals("hello", remoteInterpreter2.interpret("hello", context1));
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
  }

  @Test
  public void testIsolatedMode() throws InterpreterException, IOException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.ISOLATED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;
    assertTrue(interpreter2 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter2 = (RemoteInterpreter) interpreter2;

    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
    assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
    assertEquals(Interpreter.FormType.NATIVE, interpreter1.getFormType());
    assertEquals(0, remoteInterpreter1.getProgress(context1));
    assertNotNull(remoteInterpreter1.getOrCreateInterpreterProcess());
    assertTrue(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());

    assertNotEquals(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess(),
        remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
    // Call InterpreterGroup.close instead of Interpreter.close, otherwise we will have the
    // RemoteInterpreterProcess leakage.
    remoteInterpreter1.getInterpreterGroup().close(remoteInterpreter1.getSessionId());
    assertNull(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess());
    assertTrue(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());
    try {
      remoteInterpreter1.interpret("hello", context1);
      fail("Should not be able to call getProgress after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }

    assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
    remoteInterpreter2.getInterpreterGroup().close(remoteInterpreter2.getSessionId());
    try {
      assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());

  }

  @Test
  public void testExecuteIncorrectPrecode() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("zeppelin.SleepInterpreter.precode", "fail test");
    Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals(Code.ERROR, interpreter1.interpret("10", context1).code());
  }

  @Test
  public void testExecuteCorrectPrecode() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("zeppelin.SleepInterpreter.precode", "1");
    Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals(Code.SUCCESS, interpreter1.interpret("10", context1).code());
  }

  @Test
  public void testRemoteInterperterErrorStatus() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.setProperty("zeppelin.interpreter.echo.fail", "true");
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;

    InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    assertEquals(Code.ERROR, remoteInterpreter1.interpret("hello", context1).code());
  }

  @Test
  public void testFIFOScheduler() throws InterruptedException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    // by default SleepInterpreter would use FIFOScheduler

    final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    final InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    // run this dummy interpret method first to launch the RemoteInterpreterProcess to avoid the
    // time overhead of launching the process.
    interpreter1.interpret("1", context1);
    Thread thread1 = new Thread() {
      @Override
      public void run() {
        try {
          assertEquals(Code.SUCCESS, interpreter1.interpret("100", context1).code());
        } catch (InterpreterException e) {
          e.printStackTrace();
          fail();
        }
      }
    };
    Thread thread2 = new Thread() {
      @Override
      public void run() {
        try {
          assertEquals(Code.SUCCESS, interpreter1.interpret("100", context1).code());
        } catch (InterpreterException e) {
          e.printStackTrace();
          fail();
        }
      }
    };
    long start = System.currentTimeMillis();
    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();
    long end = System.currentTimeMillis();
    assertTrue((end - start) >= 200);
  }

  @Test
  public void testParallelScheduler() throws InterruptedException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("zeppelin.SleepInterpreter.parallel", "true");

    final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    final InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);

    // run this dummy interpret method first to launch the RemoteInterpreterProcess to avoid the
    // time overhead of launching the process.
    interpreter1.interpret("1", context1);
    Thread thread1 = new Thread() {
      @Override
      public void run() {
        try {
          assertEquals(Code.SUCCESS, interpreter1.interpret("100", context1).code());
        } catch (InterpreterException e) {
          e.printStackTrace();
          fail();
        }
      }
    };
    Thread thread2 = new Thread() {
      @Override
      public void run() {
        try {
          assertEquals(Code.SUCCESS, interpreter1.interpret("100", context1).code());
        } catch (InterpreterException e) {
          e.printStackTrace();
          fail();
        }
      }
    };
    long start = System.currentTimeMillis();
    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();
    long end = System.currentTimeMillis();
    assertTrue((end - start) <= 200);
  }

  @Test
  public void testRemoteInterpreterSharesTheSameSchedulerInstanceInTheSameGroup() {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    Interpreter interpreter2 = interpreterSetting.getInterpreter("user1", "note1", "echo");
    assertEquals(interpreter1.getInterpreterGroup(), interpreter2.getInterpreterGroup());
    assertEquals(interpreter1.getScheduler(), interpreter2.getScheduler());
  }

  @Test
  public void testMultiInterpreterSession() {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SCOPED);
    Interpreter interpreter1_user1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    Interpreter interpreter2_user1 = interpreterSetting.getInterpreter("user1", "note1", "echo");
    assertEquals(interpreter1_user1.getInterpreterGroup(), interpreter2_user1.getInterpreterGroup());
    assertEquals(interpreter1_user1.getScheduler(), interpreter2_user1.getScheduler());

    Interpreter interpreter1_user2 = interpreterSetting.getInterpreter("user2", "note1", "sleep");
    Interpreter interpreter2_user2 = interpreterSetting.getInterpreter("user2", "note1", "echo");
    assertEquals(interpreter1_user2.getInterpreterGroup(), interpreter2_user2.getInterpreterGroup());
    assertEquals(interpreter1_user2.getScheduler(), interpreter2_user2.getScheduler());

    // scheduler is shared in session but not across session
    assertNotEquals(interpreter1_user1.getScheduler(), interpreter1_user2.getScheduler());
  }

  @Test
  public void should_push_local_angular_repo_to_remote() throws Exception {

    final AngularObjectRegistry registry = new AngularObjectRegistry("spark", null);
    registry.add("name_1", "value_1", "note_1", "paragraphId_1");
    registry.add("name_2", "value_2", "node_2", "paragraphId_2");
    Interpreter interpreter = interpreterSetting.getInterpreter("user1", "note1", "angular_obj");
    interpreter.getInterpreterGroup().setAngularObjectRegistry(registry);

    final InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);

    InterpreterResult result = interpreter.interpret("dummy", context);
    assertEquals(Code.SUCCESS, result.code());
    assertEquals("2", result.message().get(0).getData());
  }

  @Test
  public void testEnvStringPattern() {
    assertFalse(RemoteInterpreterUtils.isEnvString(null));
    assertFalse(RemoteInterpreterUtils.isEnvString(""));
    assertFalse(RemoteInterpreterUtils.isEnvString("abcDEF"));
    assertFalse(RemoteInterpreterUtils.isEnvString("ABC-DEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABCDEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABC_DEF"));
    assertTrue(RemoteInterpreterUtils.isEnvString("ABC_DEF123"));
  }

  @Test
  public void testEnvironmentAndProperty() throws InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("ENV_1", "VALUE_1");
    interpreterSetting.setProperty("property_1", "value_1");

    final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "get");
    final InterpreterContext context1 = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);

    assertEquals("VALUE_1", interpreter1.interpret("getEnv ENV_1", context1).message().get(0).getData());
    assertEquals("null", interpreter1.interpret("getEnv ENV_2", context1).message().get(0).getData());

    assertEquals("value_1", interpreter1.interpret("getProperty property_1", context1).message().get(0).getData());
    assertEquals("null", interpreter1.interpret("getProperty property_2", context1).message().get(0).getData());
  }

  @Test
  public void testConvertDynamicForms() throws InterpreterException {
    GUI gui = new GUI();
    OptionInput.ParamOption[] paramOptions = {
        new OptionInput.ParamOption("value1", "param1"),
        new OptionInput.ParamOption("value2", "param2")
    };
    List<Object> defaultValues = new ArrayList();
    defaultValues.add("default1");
    defaultValues.add("default2");
    gui.checkbox("checkbox_id", defaultValues, paramOptions);
    gui.select("select_id", "default", paramOptions);
    gui.textbox("textbox_id");
    Map<String, Input> expected = new LinkedHashMap<>(gui.getForms());
    Interpreter interpreter = interpreterSetting.getDefaultInterpreter("user1", "note1");
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "repl", null,
        null, AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), gui, new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);

    interpreter.interpret("text", context);
    assertArrayEquals(expected.values().toArray(), gui.getForms().values().toArray());
  }

}
