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

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InterpreterSettingTest {

  @Test
  public void testCreateInterpreters() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create default interpreter for user1 and note1
    assertEquals(EchoInterpreter.class.getName(), interpreterSetting.getDefaultInterpreter("user1", "note1").getClassName());

    // create interpreter echo for user1 and note1
    assertEquals(EchoInterpreter.class.getName(), interpreterSetting.getInterpreter("user1", "note1", "echo").getClassName());
    assertEquals(interpreterSetting.getDefaultInterpreter("user1", "note1"), interpreterSetting.getInterpreter("user1", "note1", "echo"));

    // create interpreter double_echo for user1 and note1
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreterSetting.getInterpreter("user1", "note1", "double_echo").getClassName());

    // create non-existed interpreter
    assertNull(interpreterSetting.getInterpreter("user1", "note1", "invalid_echo"));
  }

  @Test
  public void testSharedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SHARED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create default interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // create default interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // create default interpreter user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // only 1 session is created, this session is shared across users and notes
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("note1", "user1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerUserScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", "note1");
    // InterpreterGroup is still there, but one session is removed
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user2", "note1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", "note1");
    // InterpreterGroup is still there, but one session is removed
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.closeInterpreters("user1", "note2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerUserIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    // Each user own one InterpreterGroup and one session per InterpreterGroup
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(1).getSessionNum());

    interpreterSetting.closeInterpreters("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    interpreterSetting.closeInterpreters("user2", "note1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerNoteIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note2
    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    // Each user own one InterpreterGroup and one session per InterpreterGroup
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(1).getSessionNum());

    interpreterSetting.closeInterpreters("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    interpreterSetting.closeInterpreters("user1", "note2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerUserIsolatedPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    // group1 for user1 has 2 sessions, and group2 for user2 has 1 session
    assertEquals(interpreterSetting.getInterpreterGroup("user1", "note1"), interpreterSetting.getInterpreterGroup("user1", "note2"));
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", "note1").getSessionNum());
    assertEquals(2, interpreterSetting.getInterpreterGroup("user1", "note2").getSessionNum());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user2", "note1").getSessionNum());

    // close one session for user1
    interpreterSetting.closeInterpreters("user1", "note1");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getInterpreterGroup("user1", "note1").getSessionNum());

    // close another session for user1
    interpreterSetting.closeInterpreters("user1", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // close session for user2
    interpreterSetting.closeInterpreters("user2", "note1");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerUserIsolatedPerNoteIsolatedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.ISOLATED);
    interpreterOption.setPerNote(InterpreterOption.ISOLATED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // create interpreter for user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().size());

    // create interpreter for user2 and note2
    interpreterSetting.getDefaultInterpreter("user2", "note2");
    assertEquals(4, interpreterSetting.getAllInterpreterGroups().size());

    for (InterpreterGroup interpreterGroup : interpreterSetting.getAllInterpreterGroups()) {
      // each InterpreterGroup has one session
      assertEquals(1, interpreterGroup.getSessionNum());
    }

    // close one session for user1 and note1
    interpreterSetting.closeInterpreters("user1", "note1");
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user1 and note2
    interpreterSetting.closeInterpreters("user1", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user2 and note1
    interpreterSetting.closeInterpreters("user2", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    // close one session for user2 and note2
    interpreterSetting.closeInterpreters("user2", "note2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  public void testPerUserScopedPerNoteScopedMode() {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    interpreterOption.setPerNote(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    InterpreterSetting interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();

    // create interpreter for user1 and note1
    interpreterSetting.getDefaultInterpreter("user1", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user1 and note2
    interpreterSetting.getDefaultInterpreter("user1", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note1
    interpreterSetting.getDefaultInterpreter("user2", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // create interpreter for user2 and note2
    interpreterSetting.getDefaultInterpreter("user2", "note2");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(4, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user1 and note1
    interpreterSetting.closeInterpreters("user1", "note1");
    assertEquals(3, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user1 and note2
    interpreterSetting.closeInterpreters("user1", "note2");
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user2 and note1
    interpreterSetting.closeInterpreters("user2", "note1");
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    // close one session for user2 and note2
    interpreterSetting.closeInterpreters("user2", "note2");
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }
}
