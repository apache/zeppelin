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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.junit.jupiter.api.Test;

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
}
