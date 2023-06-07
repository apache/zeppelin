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
import static org.mockito.Mockito.mock;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.junit.jupiter.api.Test;

class ConnectionManagerTest {

  @Test
  void checkMapGrow() {
    AuthorizationService authService = mock(AuthorizationService.class);

    ConnectionManager manager = new ConnectionManager(authService);
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

    ConnectionManager manager = new ConnectionManager(authService);
    NotebookSocket socket = mock(NotebookSocket.class);
    manager.addNoteConnection("test", socket);
    assertEquals(1, manager.noteSocketMap.size());
    manager.removeConnectionFromAllNote(socket);
    assertEquals(0, manager.noteSocketMap.size());
  }
}
