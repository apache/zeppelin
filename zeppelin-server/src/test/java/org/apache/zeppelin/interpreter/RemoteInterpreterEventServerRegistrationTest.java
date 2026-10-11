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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.util.HashMap;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An unregister is resolved by interpreter group id, and the group with that id can belong to
 * another process than the sender.
 */
class RemoteInterpreterEventServerRegistrationTest {

  private InterpreterSetting interpreterSetting;
  private RemoteInterpreterEventServer eventServer;
  private ManagedInterpreterGroup group;
  // Two processes of the same interpreter group id, e.g. one being stopped and its replacement.
  private RegisterInfo oldProcess;
  private RegisterInfo newProcess;

  @BeforeEach
  void setUp() {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    InterpreterOption option = new InterpreterOption();
    option.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(Lists.newArrayList(interpreterInfo))
        .setOption(option)
        .setConf(zConf)
        .create();

    InterpreterSettingManager interpreterSettingManager = mock(InterpreterSettingManager.class);
    when(interpreterSettingManager.getInterpreterGroupById(anyString()))
        .thenAnswer(invocation -> interpreterSetting.getInterpreterGroup(
            (String) invocation.getArgument(0)));
    eventServer = new RemoteInterpreterEventServer(zConf, interpreterSettingManager);

    group = interpreterSetting.getOrCreateInterpreterGroup(new ExecutionContext("user1", "note1",
        "test"));
    group.getOrCreateSession("user1", "shared_session");
    oldProcess = new RegisterInfo("10.0.0.1", 30001, group.getId());
    newProcess = new RegisterInfo("10.0.0.1", 30002, group.getId());
  }

  @Test
  void unregisterFromTheRegisteredProcessClosesItsGroup() throws Exception {
    setInterpreterProcess(group, mock(RemoteInterpreterProcess.class));
    eventServer.registerInterpreterProcess(newProcess);

    eventServer.unRegisterInterpreterProcess(group.getId(), newProcess);

    assertClosed();
  }

  @Test
  void unregisterFromAnotherProcessKeepsARegisteredGroup() throws Exception {
    // The old process was stopped or left behind, and the group with its id is now a new one.
    setInterpreterProcess(group, mock(RemoteInterpreterProcess.class));
    eventServer.registerInterpreterProcess(newProcess);

    eventServer.unRegisterInterpreterProcess(group.getId(), oldProcess);

    assertKept();
  }

  @Test
  void unregisterKeepsAGroupWhoseProcessIsStillLaunching() throws Exception {
    setInterpreterProcess(group, mock(RemoteInterpreterProcess.class));
    setLaunching(group, true);

    eventServer.unRegisterInterpreterProcess(group.getId(), oldProcess);

    assertKept();
  }

  @Test
  void unregisterKeepsAGroupWithoutProcess() throws Exception {
    eventServer.unRegisterInterpreterProcess(group.getId(), oldProcess);

    assertKept();
  }

  @Test
  void unregisterWithoutRegisterInfoClosesTheGroup() throws Exception {
    // An interpreter process from before the sender was sent along.
    setInterpreterProcess(group, mock(RemoteInterpreterProcess.class));
    eventServer.registerInterpreterProcess(newProcess);

    eventServer.unRegisterInterpreterProcess(group.getId(), null);

    assertClosed();
  }

  @Test
  void unregisterClosesAGroupWhoseProcessWasAttachedWithoutRegistration() throws Exception {
    // A recovered or an externally running process does not register.
    setInterpreterProcess(group, mock(RemoteInterpreterProcess.class));

    eventServer.unRegisterInterpreterProcess(group.getId(), oldProcess);

    assertClosed();
  }

  private void assertClosed() {
    assertEquals(0, group.getSessionNum());
    assertNull(interpreterSetting.getInterpreterGroup(group.getId()));
  }

  private void assertKept() {
    assertEquals(1, group.getSessionNum());
    assertSame(group, interpreterSetting.getInterpreterGroup(group.getId()));
  }

  private static void setInterpreterProcess(ManagedInterpreterGroup interpreterGroup,
                                            RemoteInterpreterProcess process) throws Exception {
    setField(interpreterGroup, "remoteInterpreterProcess", process);
  }

  private static void setLaunching(ManagedInterpreterGroup interpreterGroup, boolean launching)
      throws Exception {
    setField(interpreterGroup, "launchingInterpreterProcess", launching);
  }

  private static void setField(ManagedInterpreterGroup interpreterGroup, String name, Object value)
      throws Exception {
    Field field = ManagedInterpreterGroup.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(interpreterGroup, value);
  }
}
