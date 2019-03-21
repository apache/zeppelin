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

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.display.ui.OptionInput;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RemoteInterpreterTest extends AbstractInterpreterTest {

  private InterpreterSetting interpreterSetting;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
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

    assertEquals(remoteInterpreter1.getScheduler(), remoteInterpreter2.getScheduler());

    InterpreterContext context1 = createDummyInterpreterContext();
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

    assertNotEquals(interpreter1.getScheduler(), interpreter2.getScheduler());

    InterpreterContext context1 = createDummyInterpreterContext();
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
    assertNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
    try {
      assertEquals("hello", remoteInterpreter2.interpret("hello", context1));
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }

    // [ZEPPELIN-4031] Fixed : Unable to detect interpreter process killed when it is killed manully
    assertNotNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
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

    assertNotEquals(interpreter1.getScheduler(), interpreter2.getScheduler());

    InterpreterContext context1 = createDummyInterpreterContext();
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
    assertNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
    try {
      assertEquals("hello", remoteInterpreter2.interpret("hello", context1).message().get(0).getData());
      fail("Should not be able to call interpret after interpreter is closed");
    } catch (Exception e) {
      e.printStackTrace();
    }

    // [ZEPPELIN-4031] Fixed : Unable to detect interpreter process killed when it is killed manully
    assertNotNull(remoteInterpreter2.getInterpreterGroup().getRemoteInterpreterProcess());
  }

  @Test
  public void testExecuteIncorrectPrecode() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("zeppelin.SleepInterpreter.precode", "fail test");
    Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    InterpreterContext context1 = createDummyInterpreterContext();;
    assertEquals(Code.ERROR, interpreter1.interpret("10", context1).code());
  }

  @Test
  public void testExecuteCorrectPrecode() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    interpreterSetting.setProperty("zeppelin.SleepInterpreter.precode", "1");
    Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    InterpreterContext context1 = createDummyInterpreterContext();
    assertEquals(Code.SUCCESS, interpreter1.interpret("10", context1).code());
  }

  @Test
  public void testRemoteInterperterErrorStatus() throws TTransportException, IOException, InterpreterException {
    interpreterSetting.setProperty("zeppelin.interpreter.echo.fail", "true");
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;

    InterpreterContext context1 = createDummyInterpreterContext();;
    assertEquals(Code.ERROR, remoteInterpreter1.interpret("hello", context1).code());
  }

  @Test
  public void testFIFOScheduler() throws InterruptedException, InterpreterException {
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);
    // by default SleepInterpreter would use FIFOScheduler

    final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
    final InterpreterContext context1 = createDummyInterpreterContext();
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
    final InterpreterContext context1 = createDummyInterpreterContext();

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

    final InterpreterContext context = createDummyInterpreterContext();
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
    final InterpreterContext context1 = createDummyInterpreterContext();

    assertEquals("VALUE_1", interpreter1.interpret("getEnv ENV_1", context1).message().get(0).getData());
    assertEquals("null", interpreter1.interpret("getEnv ENV_2", context1).message().get(0).getData());

    assertEquals("value_1", interpreter1.interpret("getProperty property_1", context1).message().get(0).getData());
    assertEquals("null", interpreter1.interpret("getProperty not_existed_property", context1).message().get(0).getData());
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
    InterpreterContext context = createDummyInterpreterContext();

    interpreter.interpret("text", context);
    assertArrayEquals(expected.values().toArray(), gui.getForms().values().toArray());
  }

  @Test
  public void testFailToLaunchInterpreterProcess_InvalidRunner() {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName(), "invalid_runner");
      final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
      final InterpreterContext context1 = createDummyInterpreterContext();
      // run this dummy interpret method first to launch the RemoteInterpreterProcess to avoid the
      // time overhead of launching the process.
      try {
        interpreter1.interpret("1", context1);
        fail("Should not be able to launch interpreter process");
      } catch (InterpreterException e) {
        assertTrue(ExceptionUtils.getStackTrace(e).contains("No such file or directory"));
      }
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName());
    }
  }

  @Test
  public void testFailToLaunchInterpreterProcess_ErrorInRunner() {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName(),
               zeppelinHome.getAbsolutePath() + "/zeppelin-zengine/src/test/resources/bin/interpreter_invalid.sh");
      final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
      final InterpreterContext context1 = createDummyInterpreterContext();
      // run this dummy interpret method first to launch the RemoteInterpreterProcess to avoid the
      // time overhead of launching the process.
      try {
        interpreter1.interpret("1", context1);
        fail("Should not be able to launch interpreter process");
      } catch (InterpreterException e) {
        assertTrue(ExceptionUtils.getStackTrace(e).contains("invalid_command: command not found"));
      }
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName());
    }
  }

  @Test
  public void testFailToLaunchInterpreterProcess_Timeout() {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName(),
          zeppelinHome.getAbsolutePath() + "/zeppelin-zengine/src/test/resources/bin/interpreter_timeout.sh");
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "10000");
      final Interpreter interpreter1 = interpreterSetting.getInterpreter("user1", "note1", "sleep");
      final InterpreterContext context1 = createDummyInterpreterContext();
      // run this dummy interpret method first to launch the RemoteInterpreterProcess to avoid the
      // time overhead of launching the process.
      try {
        interpreter1.interpret("1", context1);
        fail("Should not be able to launch interpreter process");
      } catch (InterpreterException e) {
        assertTrue(ExceptionUtils.getStackTrace(e).contains("Interpreter Process creation is time out"));
      }
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_REMOTE_RUNNER.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName());
    }
  }

  public void testDetectIntpProcessKilled() throws InterpreterException, IOException {
    // [ZEPPELIN-4031] Fixed : Unable to detect interpreter process killed when it is killed manully
    interpreterSetting.getOption().setPerUser(InterpreterOption.SHARED);

    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");

    assertTrue(interpreter1 instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter1 = (RemoteInterpreter) interpreter1;

    InterpreterContext context1 = createDummyInterpreterContext();
    assertEquals("hello", remoteInterpreter1.interpret("hello", context1).message().get(0).getData());
    assertEquals(Interpreter.FormType.NATIVE, interpreter1.getFormType());
    assertEquals(0, remoteInterpreter1.getProgress(context1));
    assertNotNull(remoteInterpreter1.getOrCreateInterpreterProcess());
    assertTrue(remoteInterpreter1.getInterpreterGroup().getRemoteInterpreterProcess().isRunning());

    // Test close RemoteInterpreterProcess
    // Call InterpreterGroup.close instead of Interpreter.close, otherwise we will have the
    // RemoteInterpreterProcess leakage.
    remoteInterpreter1.getInterpreterGroup().close(remoteInterpreter1.getSessionId());
    interpreter1 = interpreterSetting.getDefaultInterpreter("user1", "note1");
    // RemoteInterpreterProcess close, equals null;
    assertNull(remoteInterpreter1.getInterpreterGroup().getInterpreterProcess());
    try {
      remoteInterpreter1.interpret("hello", context1);
    } catch (Exception e) {
      e.printStackTrace();
    }
    // Check if the interpreter is recreated
    assertNotNull(remoteInterpreter1.getInterpreterGroup().getInterpreterProcess());
  }
}
