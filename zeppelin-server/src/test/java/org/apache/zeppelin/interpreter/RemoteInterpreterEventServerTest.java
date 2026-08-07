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

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteInterpreterEventServerTest {

  private RemoteInterpreterEventServer eventServer;
  private InterpreterSettingManager interpreterSettingManager;

  @BeforeEach
  void setUp() throws Exception {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getZeppelinServerRpcPort()).thenReturn(OptionalInt.of(0));

    interpreterSettingManager = mock(InterpreterSettingManager.class);
    when(interpreterSettingManager.getAllInterpreterGroup())
        .thenReturn(Collections.emptyList());

    eventServer = new RemoteInterpreterEventServer(zConf, interpreterSettingManager);
    eventServer.start();
  }

  @AfterEach
  void tearDown() {
    eventServer.stop();
  }

  @Test
  void authenticatesCallbackConnectionsAndInvalidatesRotatedCredentials() throws Exception {
    String groupId = "group-a";
    String callbackToken = eventServer.issueCallbackToken(groupId);
    String otherToken = eventServer.issueCallbackToken("group-b");
    assertNotEquals(callbackToken, otherToken);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      assertEquals(Collections.emptyList(), client.callRemoteFunction(
          remote -> remote.getAllResources(groupId)));

      RuntimeException crossGroupFailure = assertThrows(RuntimeException.class,
          () -> client.callRemoteFunction(remote -> remote.getAllResources("group-b")));
      assertEquals(
          "Authenticated interpreter group does not match the requested interpreter group",
          crossGroupFailure.getMessage());

      String rotatedToken = eventServer.issueCallbackToken(groupId);
      assertThrows(RuntimeException.class, () -> client.callRemoteFunction(
          remote -> remote.getAllResources(groupId)));

      try (RemoteInterpreterEventClient rotatedClient = new RemoteInterpreterEventClient(
          eventServer.getHost(), eventServer.getPort(), 1, groupId, rotatedToken)) {
        assertEquals(Collections.emptyList(), rotatedClient.callRemoteFunction(
            remote -> remote.getAllResources(groupId)));
      }
    }

    assertThrows(RuntimeException.class, () -> {
      try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
          eventServer.getHost(), eventServer.getPort(), 1, groupId, "wrong-token")) {
        client.callRemoteFunction(remote -> remote.getAllResources(groupId));
      }
    });

    try (TSocket transport = new TSocket(eventServer.getHost(), eventServer.getPort(), 2_000)) {
      transport.open();
      RemoteInterpreterEventService.Client rawClient =
          new RemoteInterpreterEventService.Client(new TBinaryProtocol(transport));
      assertThrows(Exception.class, () -> rawClient.getAllResources(groupId));
    }
  }

  @Test
  void rejectsEndpointReplacementForTheSameLaunchCredential() {
    String groupId = "group-a";
    String callbackToken = eventServer.issueCallbackToken(groupId);
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess interpreterProcess = mock(RemoteInterpreterProcess.class);
    when(interpreterSettingManager.getInterpreterGroupById(groupId)).thenReturn(interpreterGroup);
    when(interpreterGroup.getInterpreterProcess()).thenReturn(interpreterProcess);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 54321, groupId)));
    }

    verify(interpreterProcess, times(1)).processStarted(12345, "127.0.0.1");
  }

  @Test
  void recoveredCredentialRetainsItsRegisteredEndpoint() {
    String groupId = "group-a";
    String callbackToken = "recovered-token";
    eventServer.registerCallbackToken(
        groupId, callbackToken, "127.0.0.1", 12345);
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess interpreterProcess = mock(RemoteInterpreterProcess.class);
    when(interpreterSettingManager.getInterpreterGroupById(groupId)).thenReturn(interpreterGroup);
    when(interpreterGroup.getInterpreterProcess()).thenReturn(interpreterProcess);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 54321, groupId)));
    }

    verify(interpreterProcess, never()).processStarted(12345, "127.0.0.1");
  }
}
