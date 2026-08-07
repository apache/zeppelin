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
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.recovery.RecoveryUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.apache.zeppelin.interpreter.thrift.RunParagraphsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
    allowRegistration(groupId);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      assertEquals(Collections.emptyList(), client.getAllLibraryMetadatas(""));
      assertThrows(RuntimeException.class, () -> client.callRemoteFunction(
          remote -> remote.getAllResources(groupId)));
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
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
        assertThrows(RuntimeException.class, () -> rotatedClient.callRemoteFunction(
            remote -> remote.getAllResources(groupId)));
        rotatedClient.registerInterpreterProcess(
            new RegisterInfo("127.0.0.1", 23456, groupId));
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
    RemoteInterpreterProcess interpreterProcess = allowRegistration(groupId);
    AtomicBoolean activeDuringProcessStarted = new AtomicBoolean();
    doAnswer(invocation -> {
      try (RemoteInterpreterEventClient activeClient = new RemoteInterpreterEventClient(
          eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
        activeDuringProcessStarted.set(Collections.emptyList().equals(
            activeClient.callRemoteFunction(remote -> remote.getAllResources(groupId))));
      }
      return null;
    }).when(interpreterProcess).processStarted(12345, "127.0.0.1");

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 54321, groupId)));
    }

    verify(interpreterProcess, times(1)).processStarted(12345, "127.0.0.1");
    assertTrue(activeDuringProcessStarted.get());
  }

  @Test
  void revokesBootstrapCredentialWhenProcessStartupNotificationFails() {
    String groupId = "group-a";
    String callbackToken = eventServer.issueCallbackToken(groupId);
    RemoteInterpreterProcess interpreterProcess = allowRegistration(groupId);
    doThrow(new IllegalStateException("failed to publish endpoint"))
        .when(interpreterProcess).processStarted(12345, "127.0.0.1");

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 12345, groupId)));
    }

    assertNull(eventServer.getCallbackToken(groupId));
  }

  @Test
  void recoveredCredentialRetainsItsRegisteredEndpoint() {
    String groupId = "group-a";
    String callbackToken = "recovered-token";
    RemoteInterpreterEventServer.CallbackCredentialRegistration registration =
        eventServer.registerCallbackToken(
        groupId, callbackToken, "127.0.0.1", 12345);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      RunParagraphsEvent runEvent = new RunParagraphsEvent(
          "note", Collections.emptyList(), Collections.emptyList(), "paragraph");
      assertThrows(RuntimeException.class, () -> client.callRemoteFunction(remote -> {
        remote.runParagraphs(runEvent);
        return null;
      }));
      assertThrows(RuntimeException.class, () -> client.getAllLibraryMetadatas(""));
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 54321, groupId)));
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      assertEquals(Collections.emptyList(), client.callRemoteFunction(
          remote -> remote.getAllResources(groupId)));
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("127.0.0.1", 54321, groupId)));
    }

    assertTrue(eventServer.isCallbackTokenActive(registration));
  }

  @Test
  void staleRecoveryRegistrationCannotRevokeReplacementCredential() {
    String groupId = "group-a";
    RemoteInterpreterEventServer.CallbackCredentialRegistration staleRegistration =
        eventServer.registerCallbackToken(
            groupId, "stale-token", "127.0.0.1", 12345);
    String currentToken = eventServer.issueCallbackToken(groupId);
    allowRegistration(groupId);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, currentToken)) {
      client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 23456, groupId));
    }

    eventServer.revokeCallbackToken(staleRegistration);

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, currentToken)) {
      assertEquals(Collections.emptyList(), client.callRemoteFunction(
          remote -> remote.getAllResources(groupId)));
    }
  }

  @Test
  void recoveryRequiresCallbackProofAndRevokesUnprovenCredential() {
    String groupId = "group-a";
    String callbackToken = "recovered-token";
    RemoteInterpreterEventServer.CallbackCredentialRegistration registration =
        eventServer.registerCallbackToken(
            groupId, callbackToken, "127.0.0.1", 12345);
    RemoteInterpreterRunningProcess process = recoveryProcess(
        groupId, registration, () -> { });

    assertFalse(process.recover());
    assertFalse(eventServer.isCallbackTokenActive(registration));
    assertNull(eventServer.getCallbackToken(groupId));
  }

  @Test
  void recoveryAcceptsProofFromInterpreterHoldingPersistedCredential() {
    String groupId = "group-a";
    String callbackToken = "recovered-token";
    RemoteInterpreterEventServer.CallbackCredentialRegistration registration =
        eventServer.registerCallbackToken(
            groupId, callbackToken, "127.0.0.1", 12345);
    RemoteInterpreterRunningProcess process = recoveryProcess(groupId, registration, () -> {
      try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
          eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
        client.registerInterpreterProcess(new RegisterInfo("127.0.0.1", 12345, groupId));
      }
    });

    assertTrue(process.recover());
    assertTrue(eventServer.isCallbackTokenActive(registration));
  }

  @Test
  void persistsCallbackIdentitySeparatelyFromEffectiveCommandEndpoint() {
    String groupId = "group-a";
    String callbackToken = eventServer.issueCallbackToken(groupId);
    allowRegistration(groupId);
    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      client.registerInterpreterProcess(
          new RegisterInfo("advertised-host", 2222, groupId));
    }

    when(interpreterSettingManager.getInterpreterEventServer()).thenReturn(eventServer);
    InterpreterSetting interpreterSetting = mock(InterpreterSetting.class);
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess interpreterProcess = mock(RemoteInterpreterProcess.class);
    when(interpreterSetting.getAllInterpreterGroups())
        .thenReturn(Collections.singletonList(interpreterGroup));
    when(interpreterSetting.getInterpreterSettingManager())
        .thenReturn(interpreterSettingManager);
    when(interpreterGroup.getId()).thenReturn(groupId);
    when(interpreterGroup.getInterpreterProcess()).thenReturn(interpreterProcess);
    when(interpreterProcess.isRunning()).thenReturn(true);
    when(interpreterProcess.getHost()).thenReturn("command-host");
    when(interpreterProcess.getPort()).thenReturn(1111);

    String recoveryData = RecoveryUtils.getRecoveryData(interpreterSetting);
    assertEquals("group-a\tcommand-host:1111\t" + callbackToken
        + "\tadvertised-host\t2222", recoveryData);

    ZeppelinConfiguration recoveryConfig = mock(ZeppelinConfiguration.class);
    when(recoveryConfig.getTime(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT))
        .thenReturn(1_000L);
    when(interpreterSettingManager.getByName("setting")).thenReturn(interpreterSetting);
    when(interpreterSetting.getJavaProperties()).thenReturn(new Properties());
    Map<String, InterpreterClient> recovered = RecoveryUtils.restoreFromRecoveryData(
        recoveryData, "setting", interpreterSettingManager, recoveryConfig);
    RemoteInterpreterRunningProcess process =
        (RemoteInterpreterRunningProcess) recovered.get(groupId);
    assertEquals("command-host", process.getHost());
    assertEquals(1111, process.getPort());

    try (RemoteInterpreterEventClient client = new RemoteInterpreterEventClient(
        eventServer.getHost(), eventServer.getPort(), 1, groupId, callbackToken)) {
      assertThrows(RuntimeException.class, () -> client.registerInterpreterProcess(
          new RegisterInfo("command-host", 1111, groupId)));
      client.registerInterpreterProcess(
          new RegisterInfo("advertised-host", 2222, groupId));
    }
  }

  private RemoteInterpreterProcess allowRegistration(String groupId) {
    ManagedInterpreterGroup interpreterGroup = mock(ManagedInterpreterGroup.class);
    RemoteInterpreterProcess interpreterProcess = mock(RemoteInterpreterProcess.class);
    when(interpreterSettingManager.getInterpreterGroupById(groupId)).thenReturn(interpreterGroup);
    when(interpreterGroup.getInterpreterProcess()).thenReturn(interpreterProcess);
    return interpreterProcess;
  }

  private RemoteInterpreterRunningProcess recoveryProcess(
      String groupId,
      RemoteInterpreterEventServer.CallbackCredentialRegistration registration,
      Runnable reconnectAction) {
    return new RemoteInterpreterRunningProcess(
        "setting", groupId, 1, 1, "localhost", 1,
        "127.0.0.1", 12345, true, eventServer, registration) {
      @Override
      protected void reconnectToEventServer() {
        reconnectAction.run();
      }
    };
  }
}
