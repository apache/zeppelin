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

package org.apache.zeppelin.helium;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeppelinDevServerTest {

  @Test
  void startsWithoutInterpreterCallbackCredentials() throws Exception {
    int port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    ZeppelinDevServer server = new ZeppelinDevServer(port);
    try {
      server.start();
      long deadline = System.currentTimeMillis() + 10_000;
      while (!server.isRunning() && System.currentTimeMillis() < deadline) {
        Thread.sleep(50);
      }

      assertTrue(server.isRunning());
      assertTrue(RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", port));
      server.init(new HashMap<>());
    } finally {
      if (server.isRunning()) {
        server.shutdown();
      }
      server.join(10_000);
    }
    assertFalse(server.isRunning());
  }
}
