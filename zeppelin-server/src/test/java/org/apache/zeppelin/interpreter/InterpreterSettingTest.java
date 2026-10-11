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

import com.google.common.collect.Lists;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterpreterSettingTest extends AbstractInterpreterTest{

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterSettingTest.class);

  private InterpreterSettingManager interpreterSettingManager;
  private String note1Id;
  private String note2Id;


  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    interpreterSettingManager = mock(InterpreterSettingManager.class);
    when(interpreterSettingManager.getNotebook()).thenReturn(notebook);

    note1Id = notebook.createNote("/note_1", AuthenticationInfo.ANONYMOUS);
    note2Id = notebook.createNote("/note_2", AuthenticationInfo.ANONYMOUS);
  }

  @Test
  void testCreateInterpreters() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(),
        new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);

    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create default interpreter for user1 and note1
    assertEquals(EchoInterpreter.class.getName(), interpreterSetting.getDefaultInterpreter("user1", note1Id).getClassName());

    // create interpreter echo for user1 and note1
    assertEquals(EchoInterpreter.class.getName(), interpreterSetting.getInterpreter("user1", note1Id, "echo").getClassName());
    assertEquals(interpreterSetting.getDefaultInterpreter("user1", note1Id), interpreterSetting.getInterpreter("user1", note1Id, "echo"));

    // create interpreter double_echo for user1 and note1
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreterSetting.getInterpreter("user1", note1Id, "double_echo").getClassName());

    // create non-existed interpreter
    assertNull(interpreterSetting.getInterpreter("user1", note1Id, "invalid_echo"));
  }

  @Test
  void testSharedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create default interpreter for user1 and note1
    Interpreter interpreter = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-shared_process", interpreter.getInterpreterGroup().getId());

    // create default interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // create default interpreter user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // only 1 session is created, this session is shared across users and notes
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerUserScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-shared_process", interpreter.getInterpreterGroup().getId());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note1Id);
    // InterpreterGroup is still there, but one session is removed
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user2", note1Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-shared_process", interpreter.getInterpreterGroup().getId());

    // create interpreter for user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note1Id);
    // InterpreterGroup is still there, but one session is removed
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note2Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerUserIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-user1", interpreter1.getInterpreterGroup().getId());

    // create interpreter for user2 and note1
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-user2", interpreter2.getInterpreterGroup().getId());

    // Each user own one InterpreterGroup and one session per InterpreterGroup
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(1).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    interpreterSetting.closeInterpreters("user2", note1Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerNoteIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-" + note1Id, interpreter1.getInterpreterGroup().getId());

    // create interpreter for user2 and note2
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-" + note2Id, interpreter2.getInterpreterGroup().getId());

    // Each user own one InterpreterGroup and one session per InterpreterGroup
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(1).getSessionNum());

    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    interpreterSetting.closeInterpreters("user1", note2Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerUserIsolatedPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-user1", interpreter1.getInterpreterGroup().getId());

    interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-user2", interpreter2.getInterpreterGroup().getId());

    // group1 for user1 has 2 sessions, and group2 for user2 has 1 session
    assertEquals(interpreterSetting.getInterpreterGroup("user1", note1Id), interpreterSetting.getInterpreterGroup("user1", note2Id));
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", note1Id).getSessionNum());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", note2Id).getSessionNum());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2", note1Id).getSessionNum());

    // close one session for user1
    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", note1Id).getSessionNum());

    // close another session for user1
    interpreterSetting.closeInterpreters("user1", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // close session for user2
    interpreterSetting.closeInterpreters("user2", note1Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerUserIsolatedPerNoteIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-user1-" + note1Id, interpreter1.getInterpreterGroup().getId());

    // create interpreter for user1 and note2
    Interpreter interpreter2 = interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-user1-" + note2Id, interpreter2.getInterpreterGroup().getId());

    // create interpreter for user2 and note1
    Interpreter interpreter3 = interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals("test-user2-" + note1Id, interpreter3.getInterpreterGroup().getId());

    // create interpreter for user2 and note2
    Interpreter interpreter4 = interpreterSetting.getDefaultInterpreter("user2", note2Id);
    assertEquals(4, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals("test-user2-" + note2Id, interpreter4.getInterpreterGroup().getId());

    for (InterpreterGroup interpreterGroup : interpreterSetting.getAllInterpreterGroups()) {
      // each InterpreterGroup has one session
      assertEquals(1, interpreterGroup.getSessionNum());
    }

    // close one session for user1 and note1
    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user1 and note2
    interpreterSetting.closeInterpreters("user1", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user2 and note1
    interpreterSetting.closeInterpreters("user2", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user2 and note2
    interpreterSetting.closeInterpreters("user2", note2Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testPerUserScopedPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(), new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();

    // create interpreter for user1 and note1
    Interpreter interpreter1 = interpreterSetting.getDefaultInterpreter("user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals("test-shared_process", interpreter1.getInterpreterGroup().getId());

    // create interpreter for user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note2
    interpreterSetting.getDefaultInterpreter("user2", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(4, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user1 and note1
    interpreterSetting.closeInterpreters("user1", note1Id);
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user1 and note2
    interpreterSetting.closeInterpreters("user1", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user2 and note1
    interpreterSetting.closeInterpreters("user2", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user2 and note2
    interpreterSetting.closeInterpreters("user2", note2Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testInterpreterJsonSerializable() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(),
        "double_echo", false, new HashMap<String, Object>(),
        new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("id")
        .setGroup("group")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    String json = InterpreterSetting.toJson(interpreterSetting);

    InterpreterSetting checkIntpSetting = InterpreterSetting.fromJson(json);
    assertEquals("id", checkIntpSetting.getId());
    assertEquals("id", checkIntpSetting.getName());
    assertEquals("group", checkIntpSetting.getGroup());
    assertTrue(checkIntpSetting.getOption().perUserShared());
    assertNotNull(checkIntpSetting.getInterpreterInfo("echo"));
    assertNotNull(checkIntpSetting.getInterpreterInfo("double_echo"));
  }

  @Test
  void testIsUserAuthorized() {
      List<String> userAndRoles = new ArrayList<>();
      userAndRoles.add("User1");
      userAndRoles.add("Role1");
      userAndRoles.add("Role2");
      List<String> owners;
      InterpreterSetting interpreterSetting;
      InterpreterOption interpreterOption;

      // With match
      owners = new ArrayList<>();
      owners.add("Role1");
      interpreterOption = new InterpreterOption();
      interpreterOption.setUserPermission(true);
      interpreterOption.owners = owners;
      interpreterSetting = new InterpreterSetting.Builder()
          .setId("id")
          .setName("id")
          .setGroup("group")
          .setOption(interpreterOption)
          .setConf(zConf)
          .create();
      assertTrue(interpreterSetting.isUserAuthorized(userAndRoles));

      // Without match
      owners = new ArrayList<>();
      owners.add("Role88");
      interpreterOption = new InterpreterOption();
      interpreterOption.setUserPermission(true);
      interpreterOption.owners = owners;
      interpreterSetting = new InterpreterSetting.Builder()
          .setId("id")
          .setName("id")
          .setGroup("group")
          .setOption(interpreterOption)
          .setConf(zConf)
          .create();
      assertFalse(interpreterSetting.isUserAuthorized(userAndRoles));

      // Without permissions
      owners = new ArrayList<>();
      interpreterOption = new InterpreterOption();
      interpreterOption.setUserPermission(false);
      interpreterOption.owners = owners;
      interpreterSetting = new InterpreterSetting.Builder()
          .setId("id")
          .setName("id")
          .setGroup("group")
          .setOption(interpreterOption)
          .create();
      assertTrue(interpreterSetting.isUserAuthorized(userAndRoles));
  }

  @Test
  void testLoadDependency() throws InterruptedException {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setUserPermission(true);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
            .setId("id")
            .setName("id")
            .setGroup("group")
            .setOption(interpreterOption)
            .setIntepreterSettingManager(interpreterSettingManager)
            .setDependencyResolver(new DependencyResolver("/tmp", null, null, null, null))
            .setConf(zConf)
            .create();

    // set invalid dependency
    interpreterSetting.setDependencies(Lists.newArrayList(new Dependency("a:b:0.1")));
    long start = System.currentTimeMillis();
    long threshold = 60 * 1000;
    while(interpreterSetting.getStatus() != InterpreterSetting.Status.ERROR &&
            (System.currentTimeMillis() - start) < threshold) {
      Thread.sleep(1000);
      LOGGER.warn("Downloading dependency");
    }
    assertTrue(
            interpreterSetting.getErrorReason().contains("Cannot fetch dependencies"),
            interpreterSetting.getErrorReason());

    // clean dependency
    interpreterSetting.setDependencies(new ArrayList<>());
    assertEquals(InterpreterSetting.Status.READY, interpreterSetting.getStatus());
    assertNull(interpreterSetting.getErrorReason());

  }

  @Test
  void testCloseInterpretersKeepsGroupCreatedWhileProcessStops() throws Exception {
    InterpreterSetting interpreterSetting = createEchoInterpreterSetting(InterpreterOption.SHARED);
    interpreterSetting.getDefaultInterpreter("user1", note1Id);
    ManagedInterpreterGroup closingGroup =
        interpreterSetting.getInterpreterGroup("user1", note1Id);

    // A paragraph that runs while the old process is stopping creates a new group with the same id.
    AtomicReference<ManagedInterpreterGroup> newGroup = new AtomicReference<>();
    setInterpreterProcess(closingGroup, processThatRunsOnStop(() ->
        newGroup.set(interpreterSetting.getOrCreateInterpreterGroup("user1", note2Id))));

    interpreterSetting.closeInterpreters("user1", note1Id);

    assertNotSame(closingGroup, newGroup.get());
    assertSame(newGroup.get(), interpreterSetting.getInterpreterGroup("user1", note2Id));
  }

  @Test
  @Timeout(30)
  void testConcurrentCloseOfLastSessionsKeepsGroupCreatedWhileProcessStops() throws Exception {
    InterpreterSetting interpreterSetting = createEchoInterpreterSetting(InterpreterOption.SCOPED);
    ManagedInterpreterGroup closingGroup =
        interpreterSetting.getOrCreateInterpreterGroup("user1", note1Id);

    // Both closers remove their session before either of them checks whether the group is empty.
    CyclicBarrier sessionsRemoved = new CyclicBarrier(2);
    closingGroup.sessions.put(note1Id, Lists.newArrayList(new BarrierInterpreter(sessionsRemoved)));
    closingGroup.sessions.put(note2Id, Lists.newArrayList(new BarrierInterpreter(sessionsRemoved)));

    AtomicReference<ManagedInterpreterGroup> newGroup = new AtomicReference<>();
    setInterpreterProcess(closingGroup, processThatRunsOnStop(() ->
        newGroup.set(interpreterSetting.getOrCreateInterpreterGroup("user1", note1Id))));

    Thread closer1 = new Thread(() -> interpreterSetting.closeInterpreters("user1", note1Id));
    Thread closer2 = new Thread(() -> interpreterSetting.closeInterpreters("user1", note2Id));
    closer1.start();
    closer2.start();
    closer1.join();
    closer2.join();

    assertNotSame(closingGroup, newGroup.get());
    assertSame(newGroup.get(), interpreterSetting.getInterpreterGroup("user1", note1Id));
  }

  private InterpreterSetting createEchoInterpreterSetting(String perNote) {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(perNote);
    InterpreterInfo interpreterInfo = new InterpreterInfo(EchoInterpreter.class.getName(),
        "echo", true, new HashMap<String, Object>(), new HashMap<String, Object>());
    return new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(Lists.newArrayList(interpreterInfo))
        .setOption(interpreterOption)
        .setIntepreterSettingManager(interpreterSettingManager)
        .setConf(zConf)
        .create();
  }

  private static RemoteInterpreterProcess processThatRunsOnStop(Runnable onStop) {
    RemoteInterpreterProcess process = mock(RemoteInterpreterProcess.class);
    doAnswer(invocation -> {
      onStop.run();
      return null;
    }).when(process).stop();
    return process;
  }

  private static void setInterpreterProcess(ManagedInterpreterGroup interpreterGroup,
                                            RemoteInterpreterProcess process) throws Exception {
    Field field = ManagedInterpreterGroup.class.getDeclaredField("remoteInterpreterProcess");
    field.setAccessible(true);
    field.set(interpreterGroup, process);
  }

  private static class BarrierInterpreter extends Interpreter {

    private final CyclicBarrier barrier;

    BarrierInterpreter(CyclicBarrier barrier) {
      super(new Properties());
      this.barrier = barrier;
    }

    @Override
    public void close() {
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void open() {
    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) {
    }

    @Override
    public FormType getFormType() {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) {
      return 0;
    }
  }
}
