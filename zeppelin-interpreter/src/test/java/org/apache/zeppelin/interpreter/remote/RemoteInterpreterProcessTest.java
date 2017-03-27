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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.junit.Test;

public class RemoteInterpreterProcessTest {
  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";
  private static final int DUMMY_PORT=3678;

  @Test
  public void testStartStop() {
    InterpreterGroup intpGroup = new InterpreterGroup();
    RemoteInterpreterManagedProcess rip = new RemoteInterpreterManagedProcess(
        INTERPRETER_SCRIPT, "nonexists", "fakeRepo", new HashMap<String, String>(),
        10 * 1000, null, null,"fakeName");
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup, "anonymous", false));
    assertEquals(2, rip.reference(intpGroup, "anonymous", false));
    assertEquals(true, rip.isRunning());
    assertEquals(1, rip.dereference());
    assertEquals(true, rip.isRunning());
    assertEquals(0, rip.dereference());
    assertEquals(false, rip.isRunning());
  }

  @Test
  public void testClientFactory() throws Exception {
    InterpreterGroup intpGroup = new InterpreterGroup();
    RemoteInterpreterManagedProcess rip = new RemoteInterpreterManagedProcess(
        INTERPRETER_SCRIPT, "nonexists", "fakeRepo", new HashMap<String, String>(),
        mock(RemoteInterpreterEventPoller.class), 10 * 1000, "fakeName");
    rip.reference(intpGroup, "anonymous", false);
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    Client client = rip.getClient();
    assertEquals(1, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    rip.releaseClient(client);
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(1, rip.getNumIdleClient());

    rip.dereference();
  }

  @Test
  public void testStartStopRemoteInterpreter() throws TException, InterruptedException {
    RemoteInterpreterServer server = new RemoteInterpreterServer(3678);
    server.start();
    boolean running = false;
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        running = true;
        break;
      } else {
        Thread.sleep(200);
      }
    }
    Properties properties = new Properties();
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_PORT, "3678");
    properties.setProperty(Constants.ZEPPELIN_INTERPRETER_HOST, "localhost");
    InterpreterGroup intpGroup = mock(InterpreterGroup.class);
    when(intpGroup.getProperty()).thenReturn(properties);
    when(intpGroup.containsKey(Constants.EXISTING_PROCESS)).thenReturn(true);

    RemoteInterpreterProcess rip = new RemoteInterpreterManagedProcess(
        INTERPRETER_SCRIPT,
        "nonexists",
        "fakeRepo",
        new HashMap<String, String>(),
        mock(RemoteInterpreterEventPoller.class)
        , 10 * 1000,
        "fakeName");
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference(intpGroup, "anonymous", false));
    assertEquals(true, rip.isRunning());
  }


  @Test
  public void testPropagateError() throws TException, InterruptedException {
    InterpreterGroup intpGroup = new InterpreterGroup();
    RemoteInterpreterManagedProcess rip = new RemoteInterpreterManagedProcess(
        "echo hello_world", "nonexists", "fakeRepo", new HashMap<String, String>(),
        10 * 1000, null, null, "fakeName");
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    try {
      assertEquals(1, rip.reference(intpGroup, "anonymous", false));
    } catch (InterpreterException e) {
      e.getMessage().contains("hello_world");
    }
    assertEquals(0, rip.referenceCount());
  }
}
