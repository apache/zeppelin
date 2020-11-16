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

package org.apache.zeppelin.interpreter.lifecycle;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.ExecutionContextBuilder;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.scheduler.Job;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimeoutLifecycleManagerTest extends AbstractInterpreterTest {

  private File zeppelinSiteFile = new File("zeppelin-site.xml");

  @Override
  public void setUp() throws Exception {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(),
        TimeoutLifecycleManager.class.getName());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL.getVarName(), "1000");
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD.getVarName(), "10000");

    super.setUp();
  }

  @Override
  public void tearDown() {
    zeppelinSiteFile.delete();
  }

  @Test
  public void testTimeout_1() throws InterpreterException, InterruptedException, IOException {
    assertTrue(interpreterFactory.getInterpreter("test.echo", new ExecutionContextBuilder().setUser("user1").setNoteId("note1").setDefaultInterpreterGroup("test").createExecutionContext()) instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("test.echo", new ExecutionContextBuilder().setUser("user1").setNoteId("note1").setDefaultInterpreterGroup("test").createExecutionContext());
    assertFalse(remoteInterpreter.isOpened());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    Thread.sleep(15*1000);
    // InterpreterGroup is not removed after 15 seconds, as TimeoutLifecycleManager only manage it after it is started
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("noteId")
        .setParagraphId("paragraphId")
        .build();
    remoteInterpreter.interpret("hello world", context);
    assertTrue(remoteInterpreter.isOpened());

    Thread.sleep(15 * 1000);
    // interpreterGroup is timeout, so is removed.
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testTimeout_2() throws InterpreterException, InterruptedException, IOException {
    assertTrue(interpreterFactory.getInterpreter("test.sleep", new ExecutionContextBuilder().setUser("user1").setNoteId("note1").setDefaultInterpreterGroup("test").createExecutionContext()) instanceof RemoteInterpreter);
    final RemoteInterpreter remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("test.sleep", new ExecutionContextBuilder().setUser("user1").setNoteId("note1").setDefaultInterpreterGroup("test").createExecutionContext());

    // simulate how zeppelin submit paragraph
    remoteInterpreter.getScheduler().submit(new Job<Object>("test-job", null) {
      @Override
      public Object getReturn() {
        return null;
      }

      @Override
      public int progress() {
        return 0;
      }

      @Override
      public Map<String, Object> info() {
        return null;
      }

      @Override
      protected Object jobRun() throws Throwable {
        InterpreterContext context = InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .build();
        return remoteInterpreter.interpret("100000", context);
      }

      @Override
      protected boolean jobAbort() {
        return false;
      }

      @Override
      public void setResult(Object results) {

      }
    });

    int maxWait = 120; // 2 minute
    while(!remoteInterpreter.isOpened()) {
      Thread.sleep(1000);
      LOGGER.info("Wait for interpreter to be started");
      if (--maxWait==0) break;
    }

    assertTrue("Timedout waiting for interpreter to be started",remoteInterpreter.isOpened());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    Thread.sleep(15 * 1000);
    // interpreterGroup is not timeout because getStatus is called periodically.
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertTrue(remoteInterpreter.isOpened());
  }
}
