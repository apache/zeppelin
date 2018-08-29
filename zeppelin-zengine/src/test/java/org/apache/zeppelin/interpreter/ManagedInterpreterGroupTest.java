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

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class ManagedInterpreterGroupTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedInterpreterGroupTest.class);

  private InterpreterSetting interpreterSetting;

  @Before
  public void setUp() throws IOException, RepositoryException {
    InterpreterOption interpreterOption = new InterpreterOption();
    interpreterOption.setPerUser(InterpreterOption.SCOPED);
    InterpreterInfo interpreterInfo1 = new InterpreterInfo(EchoInterpreter.class.getName(), "echo", true, new HashMap<String, Object>());
    InterpreterInfo interpreterInfo2 = new InterpreterInfo(DoubleEchoInterpreter.class.getName(), "double_echo", false, new HashMap<String, Object>());
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(interpreterInfo1);
    interpreterInfos.add(interpreterInfo2);
    interpreterSetting = new InterpreterSetting.Builder()
        .setId("id")
        .setName("test")
        .setGroup("test")
        .setInterpreterInfos(interpreterInfos)
        .setOption(interpreterOption)
        .create();
  }

  @Test
  public void testInterpreterGroup() {
    ManagedInterpreterGroup interpreterGroup = new ManagedInterpreterGroup("group_1", interpreterSetting);
    assertEquals(0, interpreterGroup.getSessionNum());

    // create session_1
    List<Interpreter> interpreters = interpreterGroup.getOrCreateSession("user1", "session_1");
    assertEquals(3, interpreters.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters.get(1).getClassName());
    assertEquals(1, interpreterGroup.getSessionNum());

    // get the same interpreters when interpreterGroup.getOrCreateSession is invoked again
    assertEquals(interpreters, interpreterGroup.getOrCreateSession("user1", "session_1"));
    assertEquals(1, interpreterGroup.getSessionNum());

    // create session_2
    List<Interpreter> interpreters2 = interpreterGroup.getOrCreateSession("user1", "session_2");
    assertEquals(3, interpreters2.size());
    assertEquals(EchoInterpreter.class.getName(), interpreters2.get(0).getClassName());
    assertEquals(DoubleEchoInterpreter.class.getName(), interpreters2.get(1).getClassName());
    assertEquals(2, interpreterGroup.getSessionNum());

    // close session_1
    interpreterGroup.close("session_1");
    assertEquals(1, interpreterGroup.getSessionNum());

    // close InterpreterGroup
    interpreterGroup.close();
    assertEquals(0, interpreterGroup.getSessionNum());
  }
}
