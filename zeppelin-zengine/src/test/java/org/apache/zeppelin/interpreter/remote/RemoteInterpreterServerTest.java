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

import java.io.IOException;

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteInterpreterServerTest {
  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testStartStop() throws InterruptedException, IOException, TException {
    RemoteInterpreterServer server = new RemoteInterpreterServer(
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces());
    assertEquals(false, server.isRunning());

    server.start();
    long startTime = System.currentTimeMillis();
    boolean running = false;

    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        running = true;
        break;
      } else {
        Thread.sleep(200);
      }
    }

    assertEquals(true, running);
    assertEquals(true, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", server.getPort()));

    server.shutdown();

    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        Thread.sleep(200);
      } else {
        running = false;
        break;
      }
    }
    assertEquals(false, running);
  }


}
