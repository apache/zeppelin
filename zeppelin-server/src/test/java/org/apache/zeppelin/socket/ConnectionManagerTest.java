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
package org.apache.zeppelin.socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectionManagerTest {

  @Test
  void checkMapGrow() {
    AuthorizationService authService = mock(AuthorizationService.class);

    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket = mock(NotebookSocket.class);
    manager.addNoteConnection("test", socket);
    assertEquals(1, manager.noteSocketMap.size());
    // Remove Connection from wrong note
    manager.removeNoteConnection("test1", socket);
    assertEquals(1, manager.noteSocketMap.size());
    // Remove Connection from right note
    manager.removeNoteConnection("test", socket);
    assertEquals(0, manager.noteSocketMap.size());

    manager.addUserConnection("TestUser", socket);
    assertEquals(1, manager.userSocketMap.size());
    manager.removeUserConnection("TestUser", socket);
    assertEquals(0, manager.userSocketMap.size());
  }

  @Test
  void checkMapGrowRemoveAll() {
    AuthorizationService authService = mock(AuthorizationService.class);

    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket = mock(NotebookSocket.class);
    manager.addNoteConnection("test", socket);
    assertEquals(1, manager.noteSocketMap.size());
    manager.removeConnectionFromAllNote(socket);
    assertEquals(0, manager.noteSocketMap.size());
  }

  @Test
  void removeWatcherConnectionCleansQueue() {
    AuthorizationService authService = mock(AuthorizationService.class);

    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket = mock(NotebookSocket.class);

    manager.watcherSockets.add(socket);
    assertEquals(1, manager.watcherSockets.size());

    manager.removeWatcherConnection(socket);
    assertEquals(0, manager.watcherSockets.size());
  }

  @Test
  void removeWatcherConnectionWithMultipleWatchers() {
    AuthorizationService authService = mock(AuthorizationService.class);

    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket1 = mock(NotebookSocket.class);
    NotebookSocket socket2 = mock(NotebookSocket.class);
    NotebookSocket socket3 = mock(NotebookSocket.class);

    // Add multiple watchers
    manager.watcherSockets.add(socket1);
    manager.watcherSockets.add(socket2);
    manager.watcherSockets.add(socket3);
    assertEquals(3, manager.watcherSockets.size());

    // Remove only socket2
    manager.removeWatcherConnection(socket2);
    assertEquals(2, manager.watcherSockets.size());
    assertTrue(manager.watcherSockets.contains(socket1));
    assertFalse(manager.watcherSockets.contains(socket2));
    assertTrue(manager.watcherSockets.contains(socket3));
  }

  @Test
  void removeWatcherConnectionConcurrentTest() throws InterruptedException {
    AuthorizationService authService = mock(AuthorizationService.class);
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    
    int threadCount = 10;
    List<NotebookSocket> sockets = new ArrayList<>();
    
    // Create and add multiple watcher sockets
    for (int i = 0; i < threadCount; i++) {
      NotebookSocket socket = mock(NotebookSocket.class);
      sockets.add(socket);
      manager.watcherSockets.add(socket);
    }
    
    assertEquals(threadCount, manager.watcherSockets.size());
    
    // Remove sockets concurrently
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    
    for (NotebookSocket socket : sockets) {
      executor.submit(() -> {
        manager.removeWatcherConnection(socket);
        latch.countDown();
      });
    }
    
    // Wait for all threads to complete
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    executor.shutdown();
    
    // Verify all sockets were removed
    assertEquals(0, manager.watcherSockets.size());
  }

  @Test
  void userSocketMapConcurrentAccessTest() throws InterruptedException {
    AuthorizationService authService = mock(AuthorizationService.class);
    when(authService.getRoles(anyString())).thenAnswer(invocation -> new HashSet<>());
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());

    int writerCount = 8;
    int readerCount = 2;
    int iterations = 1000;

    List<String> users = new ArrayList<>();
    List<NotebookSocket> sockets = new ArrayList<>();
    for (int i = 0; i < writerCount; i++) {
      users.add("user-" + i);
      sockets.add(mock(NotebookSocket.class));
    }

    AtomicReference<Throwable> failure = new AtomicReference<>();
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(writerCount + readerCount);
    ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);

    for (int i = 0; i < writerCount; i++) {
      String user = users.get(i);
      NotebookSocket socket = sockets.get(i);
      executor.submit(() -> {
        try {
          startLatch.await();
          for (int j = 0; j < iterations; j++) {
            manager.addUserConnection(user, socket);
            manager.removeUserConnection(user, socket);
          }
        } catch (Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    for (int i = 0; i < readerCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          for (int j = 0; j < iterations; j++) {
            manager.forAllUsers((user, userAndRoles) -> { });
          }
        } catch (Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
    executor.shutdown();

    assertNull(failure.get(),
        "Concurrent access to userSocketMap should not throw, but got: " + failure.get());
  }

  @Test
  void userSocketMapConcurrentAddPreservesAllConnectionsTest() throws InterruptedException {
    AuthorizationService authService = mock(AuthorizationService.class);
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());

    String user = "shared-user";
    int threadCount = 16;
    int iterationsPerThread = 500;

    AtomicReference<Throwable> failure = new AtomicReference<>();
    List<NotebookSocket> addedSockets = new CopyOnWriteArrayList<>();

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          for (int j = 0; j < iterationsPerThread; j++) {
            NotebookSocket socket = mock(NotebookSocket.class);
            manager.addUserConnection(user, socket);
            addedSockets.add(socket);
          }
        } catch (Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
    executor.shutdown();

    assertNull(failure.get(), "Concurrent add should not throw, but got: " + failure.get());

    Queue<NotebookSocket> finalConnections = manager.userSocketMap.get(user);
    assertEquals(threadCount * iterationsPerThread, addedSockets.size());
    List<NotebookSocket> missing = new ArrayList<>();
    for (NotebookSocket socket : addedSockets) {
      if (finalConnections == null || !finalConnections.contains(socket)) {
        missing.add(socket);
      }
    }

    assertTrue(missing.isEmpty(),
        missing.size() + " of " + addedSockets.size()
            + " concurrently added connections were lost from userSocketMap");
    assertEquals(addedSockets.size(), finalConnections.size());
  }

  @Test
  void switchConnectionToWatcherAndRemove() {
    AuthorizationService authService = mock(AuthorizationService.class);
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    
    NotebookSocket socket = mock(NotebookSocket.class);
    when(socket.getUser()).thenReturn("testUser");
    when(socket.getHeader(WatcherSecurityKey.HTTP_HEADER)).thenReturn(WatcherSecurityKey.getKey());
    
    // Add socket as regular connection first
    manager.addConnection(socket);
    manager.addUserConnection("testUser", socket);
    
    // Switch to watcher
    manager.switchConnectionToWatcher(socket);
    
    // Verify it's in watcher queue
    assertTrue(manager.watcherSockets.contains(socket));
    assertFalse(manager.connectedSockets.contains(socket));
    
    // Remove watcher connection
    manager.removeWatcherConnection(socket);
    
    // Verify it's completely removed
    assertFalse(manager.watcherSockets.contains(socket));
  }

  @Test
  void removeUserConnectionWithNullUserDoesNotThrow() {
    AuthorizationService authService = mock(AuthorizationService.class);
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket = mock(NotebookSocket.class);

    assertDoesNotThrow(() -> manager.removeUserConnection(null, socket));
  }

  @Test
  void removeUserConnectionBeforeUserAssignment() {
    AuthorizationService authService = mock(AuthorizationService.class);
    ConnectionManager manager = new ConnectionManager(authService, ZeppelinConfiguration.load());
    NotebookSocket socket = mock(NotebookSocket.class);

    assertDoesNotThrow(() -> manager.removeUserConnection("", socket));
    assertTrue(manager.userSocketMap.isEmpty());
  }

  @Test
  void broadcastToNoteSerializesMessageOnce() throws IOException {
    CountingConnectionManager manager = newCountingManager();
    NotebookSocket first = mock(NotebookSocket.class);
    NotebookSocket second = mock(NotebookSocket.class);
    NotebookSocket third = mock(NotebookSocket.class);
    manager.addNoteConnection("note1", first);
    manager.addNoteConnection("note1", second);
    manager.addNoteConnection("note1", third);
    // adding a connection broadcasts the collaborative mode status, which is not under test
    manager.serializeCount.set(0);
    clearInvocations(first, second, third);

    manager.broadcast("note1", new Message(Message.OP.NOTE).put("note", "payload"));

    assertEquals(1, manager.serializeCount.get());
    assertEquals(payloadSentTo(first), payloadSentTo(second));
    assertEquals(payloadSentTo(first), payloadSentTo(third));
  }

  @Test
  void broadcastExceptSerializesMessageOnce() throws IOException {
    CountingConnectionManager manager = newCountingManager();
    NotebookSocket sender = mock(NotebookSocket.class);
    NotebookSocket first = mock(NotebookSocket.class);
    NotebookSocket second = mock(NotebookSocket.class);
    manager.addNoteConnection("note1", sender);
    manager.addNoteConnection("note1", first);
    manager.addNoteConnection("note1", second);
    manager.serializeCount.set(0);
    clearInvocations(sender, first, second);

    manager.broadcastExcept("note1",
        new Message(Message.OP.PATCH_PARAGRAPH).put("patch", "payload"), sender);

    assertEquals(1, manager.serializeCount.get());
    assertEquals(payloadSentTo(first), payloadSentTo(second));
    verify(sender, times(0)).send(anyString());
  }

  @Test
  void multicastToUserSerializesMessageOnceAndKeepsWatcherBroadcasts() throws IOException {
    CountingConnectionManager manager = newCountingManager();
    NotebookSocket first = mock(NotebookSocket.class);
    NotebookSocket second = mock(NotebookSocket.class);
    manager.addUserConnection("testUser", first);
    manager.addUserConnection("testUser", second);
    NotebookSocket watcher = mock(NotebookSocket.class);
    when(watcher.getHeader(WatcherSecurityKey.HTTP_HEADER)).thenReturn(WatcherSecurityKey.getKey());
    manager.switchConnectionToWatcher(watcher);
    manager.serializeCount.set(0);
    clearInvocations(first, second, watcher);

    manager.multicastToUser("testUser", new Message(Message.OP.NOTES_INFO).put("notes", "payload"));

    assertEquals(payloadSentTo(first), payloadSentTo(second));
    // one conversion for the two connections, plus one per watcher broadcast as before
    assertEquals(3, manager.serializeCount.get());
    verify(watcher, times(2)).send(anyString());
  }

  private static CountingConnectionManager newCountingManager() {
    return new CountingConnectionManager(mock(AuthorizationService.class),
        ZeppelinConfiguration.load());
  }

  private static String payloadSentTo(NotebookSocket socket) throws IOException {
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(socket).send(payload.capture());
    return payload.getValue();
  }

  /**
   * Counts how often a broadcast converts its message, which is what distinguishes a single
   * conversion reused across connections from one conversion per connection.
   */
  private static class CountingConnectionManager extends ConnectionManager {
    private final AtomicInteger serializeCount = new AtomicInteger();

    CountingConnectionManager(AuthorizationService authorizationService,
                              ZeppelinConfiguration zConf) {
      super(authorizationService, zConf);
    }

    @Override
    protected String serializeMessage(Message m) {
      serializeCount.incrementAndGet();
      return super.serializeMessage(m);
    }
  }
}
