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
