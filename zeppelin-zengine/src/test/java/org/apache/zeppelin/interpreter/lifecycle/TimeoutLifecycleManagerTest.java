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
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimeoutLifecycleManagerTest extends AbstractInterpreterTest {

  @Override
  public void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_CLASS.getVarName(),
        TimeoutLifecycleManager.class.getName());
    conf.setProperty("zeppelin.interpreter.lifecyclemanager.timeout.checkinterval", 5000);
    conf.setProperty("zeppelin.interpreter.lifecyclemanager.timeout.threshold", 10000);
    super.setUp();
  }

  @Test
  public void testTimeout_1() throws InterpreterException, InterruptedException, IOException {
    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getSettingIds());
    assertTrue(interpreterFactory.getInterpreter("user1", "note1", "test.echo") instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test.echo");
    InterpreterContext context = new InterpreterContext("noteId", "paragraphId", "repl",
        "title", "text", AuthenticationInfo.ANONYMOUS, new HashMap<String, Object>(), new GUI(),
        null, null, new ArrayList<InterpreterContextRunner>(), null);
    remoteInterpreter.interpret("hello world", context);
    assertTrue(remoteInterpreter.isOpened());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("test");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    Thread.sleep(15 * 1000);
    // interpreterGroup is timeout, so is removed.
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
    assertFalse(remoteInterpreter.isOpened());
  }
}
