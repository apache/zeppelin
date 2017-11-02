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

import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InterpreterFactoryTest extends AbstractInterpreterTest {

  @Test
  public void testGetFactory() throws IOException {
    // no default interpreter because there's no interpreter setting binded to this note
    assertNull(interpreterFactory.getInterpreter("user1", "note1", ""));

    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getSettingIds());
    assertTrue(interpreterFactory.getInterpreter("user1", "note1", "") instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "");
    // EchoInterpreter is the default interpreter because mock1 is the default interpreter group
    assertEquals(EchoInterpreter.class.getName(), remoteInterpreter.getClassName());

    assertTrue(interpreterFactory.getInterpreter("user1", "note1", "test") instanceof RemoteInterpreter);
    remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test");
    assertEquals(EchoInterpreter.class.getName(), remoteInterpreter.getClassName());

    assertTrue(interpreterFactory.getInterpreter("user1", "note1", "test2") instanceof RemoteInterpreter);
    remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test2");
    assertEquals(EchoInterpreter.class.getName(), remoteInterpreter.getClassName());

    assertTrue(interpreterFactory.getInterpreter("user1", "note1", "test2.double_echo") instanceof RemoteInterpreter);
    remoteInterpreter = (RemoteInterpreter) interpreterFactory.getInterpreter("user1", "note1", "test2.double_echo");
    assertEquals(DoubleEchoInterpreter.class.getName(), remoteInterpreter.getClassName());
  }

  @Test
  public void testUnknownRepl1() throws IOException {
    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getSettingIds());
    try {
      interpreterFactory.getInterpreter("user1", "note1", "test.unknown_repl");
      fail("should fail due to no such interpreter");
    } catch (RuntimeException e) {
      assertEquals("No such interpreter: test.unknown_repl", e.getMessage());
    }
  }

  @Test
  public void testUnknownRepl2() throws IOException {
    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getSettingIds());
    try {
      interpreterFactory.getInterpreter("user1", "note1", "unknown_repl");
      fail("should fail due to no such interpreter");
    } catch (RuntimeException e) {
      assertEquals("Either no interpreter named unknown_repl or it is not binded to this note", e.getMessage());
    }
  }
}
