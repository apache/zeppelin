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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectBuilder;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/** Basic REST API tests for notebookServer. */
public class NotebookServerTest extends AbstractTestRestApi {
  private static Notebook notebook;
  private static Provider<Notebook> notebookProvider;
  private static NotebookServer notebookServer;
  private static NotebookService notebookService;
  private HttpServletRequest mockRequest;
  private AuthenticationInfo anonymous;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp(NotebookServerTest.class.getSimpleName());
    notebook = Notebook.getInstance();
    notebookProvider = () -> notebook;
    notebookServer = spy(NotebookServer.getInstance());
    notebookService =
        new NotebookService(
            notebook, NotebookAuthorization.getInstance(), ZeppelinConfiguration.create());
    ConfigurationService configurationService = new ConfigurationService(notebook.getConf());
    when(notebookServer.getNotebookService()).thenReturn(notebookService);
    when(notebookServer.getConfigurationService()).thenReturn(configurationService);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {
    mockRequest = mock(HttpServletRequest.class);
    anonymous = new AuthenticationInfo("anonymous");
  }

  @Test
  public void checkOrigin() throws UnknownHostException {
    NotebookServer server = new NotebookServer();
    server.setNotebookProvider(notebookProvider);
    server.setNotebookServiceProvider(() -> notebookService);
    String origin = "http://" + InetAddress.getLocalHost().getHostName() + ":8080";

    assertTrue("Origin " + origin + " is not allowed. Please check your hostname.",
          server.checkOrigin(mockRequest, origin));
  }

  @Test
  public void checkInvalidOrigin(){
    NotebookServer server = new NotebookServer();
    server.setNotebookProvider(notebookProvider);
    server.setNotebookServiceProvider(() -> notebookService);
    assertFalse(server.checkOrigin(mockRequest, "http://evillocalhost:8080"));
  }

  @Test
  public void testCollaborativeEditing() throws IOException {
    if (!ZeppelinConfiguration.create().isZeppelinNotebookCollaborativeModeEnable()) {
      return;
    }
    NotebookSocket sock1 = createWebSocket();
    NotebookSocket sock2 = createWebSocket();

    String noteName = "Note with millis " + System.currentTimeMillis();
    notebookServer.onMessage(sock1, new Message(OP.NEW_NOTE).put("name", noteName).toJson());
    Note createdNote = null;
    for (Note note : notebook.getAllNotes()) {
      if (note.getName().equals(noteName)) {
        createdNote = note;
        break;
      }
    }

    Message message = new Message(OP.GET_NOTE).put("id", createdNote.getId());
    notebookServer.onMessage(sock1, message.toJson());
    notebookServer.onMessage(sock2, message.toJson());

    Paragraph paragraph = createdNote.getParagraphs().get(0);
    String paragraphId = paragraph.getId();

    String[] patches = new String[]{
        "@@ -0,0 +1,3 @@\n+ABC\n",            // ABC
        "@@ -1,3 +1,4 @@\n ABC\n+%0A\n",      // press Enter
        "@@ -1,4 +1,7 @@\n ABC%0A\n+abc\n",   // abc
        "@@ -1,7 +1,45 @@\n ABC\n-%0Aabc\n+ ssss%0Aabc ssss\n" // add text in two string
    };

    int sock1SendCount = 0;
    int sock2SendCount = 0;
    reset(sock1);
    reset(sock2);
    patchParagraph(sock1, paragraphId, patches[0]);
    assertEquals("ABC", paragraph.getText());
    verify(sock1, times(sock1SendCount)).send(anyString());
    verify(sock2, times(++sock2SendCount)).send(anyString());

    patchParagraph(sock2, paragraphId, patches[1]);
    assertEquals("ABC\n", paragraph.getText());
    verify(sock1, times(++sock1SendCount)).send(anyString());
    verify(sock2, times(sock2SendCount)).send(anyString());

    patchParagraph(sock1, paragraphId, patches[2]);
    assertEquals("ABC\nabc", paragraph.getText());
    verify(sock1, times(sock1SendCount)).send(anyString());
    verify(sock2, times(++sock2SendCount)).send(anyString());

    patchParagraph(sock2, paragraphId, patches[3]);
    assertEquals("ABC ssss\nabc ssss", paragraph.getText());
    verify(sock1, times(++sock1SendCount)).send(anyString());
    verify(sock2, times(sock2SendCount)).send(anyString());

    notebook.removeNote(createdNote.getId(), anonymous);
  }

  private void patchParagraph(NotebookSocket noteSocket, String paragraphId, String patch) {
    Message message = new Message(OP.PATCH_PARAGRAPH);
    message.put("patch", patch);
    message.put("id", paragraphId);
    notebookServer.onMessage(noteSocket, message.toJson());
  }

  @Test
  public void testMakeSureNoAngularObjectBroadcastToWebsocketWhoFireTheEvent()
          throws IOException, InterruptedException {
    // create a notebook
    Note note1 = notebook.createNote("note1", anonymous);

    // get reference to interpreterGroup
    InterpreterGroup interpreterGroup = null;
    List<InterpreterSetting> settings = notebook.getInterpreterSettingManager()
            .getInterpreterSettings(note1.getId());
    for (InterpreterSetting setting : settings) {
      if (setting.getName().equals("md")) {
        interpreterGroup = setting.getOrCreateInterpreterGroup("anonymous", "sharedProcess");
        break;
      }
    }

    // start interpreter process
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%md start remote interpreter process");
    p1.setAuthenticationInfo(anonymous);
    note1.run(p1.getId());

    // wait for paragraph finished
    while (true) {
      if (p1.getStatus() == Job.Status.FINISHED) {
        break;
      }
      Thread.sleep(100);
    }
    // sleep for 1 second to make sure job running thread finish to fire event. See ZEPPELIN-3277
    Thread.sleep(1000);

    // add angularObject
    interpreterGroup.getAngularObjectRegistry().add("object1", "value1", note1.getId(), null);

    // create two sockets and open it
    NotebookSocket sock1 = createWebSocket();
    NotebookSocket sock2 = createWebSocket();

    assertEquals(sock1, sock1);
    assertNotEquals(sock1, sock2);

    notebookServer.onOpen(sock1);
    notebookServer.onOpen(sock2);
    verify(sock1, times(0)).send(anyString()); // getNote, getAngularObject
    // open the same notebook from sockets
    notebookServer.onMessage(sock1, new Message(OP.GET_NOTE).put("id", note1.getId()).toJson());
    notebookServer.onMessage(sock2, new Message(OP.GET_NOTE).put("id", note1.getId()).toJson());

    reset(sock1);
    reset(sock2);

    // update object from sock1
    notebookServer.onMessage(sock1,
        new Message(OP.ANGULAR_OBJECT_UPDATED)
        .put("noteId", note1.getId())
        .put("name", "object1")
        .put("value", "value1")
        .put("interpreterGroupId", interpreterGroup.getId()).toJson());


    // expect object is broadcasted except for where the update is created
    verify(sock1, times(0)).send(anyString());
    verify(sock2, times(1)).send(anyString());

    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testImportNotebook() throws IOException {
    String msg = "{\"op\":\"IMPORT_NOTE\",\"data\":" +
        "{\"note\":{\"paragraphs\": [{\"text\": \"Test " +
        "paragraphs import\"," + "\"progressUpdateIntervalMs\":500," +
        "\"config\":{},\"settings\":{}}]," +
        "\"name\": \"Test Zeppelin notebook import\",\"config\": " +
        "{}}}}";
    Message messageReceived = notebookServer.deserializeMessage(msg);
    Note note = null;
    try {
      note = notebookServer.importNote(null, messageReceived);
    } catch (NullPointerException e) {
      //broadcastNoteList(); failed nothing to worry.
      LOG.error("Exception in NotebookServerTest while testImportNotebook, failed nothing to " +
          "worry ", e);
    }

    assertNotEquals(null, notebook.getNote(note.getId()));
    assertEquals("Test Zeppelin notebook import", notebook.getNote(note.getId()).getName());
    assertEquals("Test paragraphs import", notebook.getNote(note.getId()).getParagraphs().get(0)
            .getText());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void bindAngularObjectToRemoteForParagraphs() throws Exception {
    //Given
    final String varName = "name";
    final String value = "DuyHai DOAN";
    final Message messageReceived = new Message(OP.ANGULAR_OBJECT_CLIENT_BIND)
            .put("noteId", "noteId")
            .put("name", varName)
            .put("value", value)
            .put("paragraphId", "paragraphId");

    final Notebook notebook = mock(Notebook.class);
    final NotebookServer server = new NotebookServer();
    server.setNotebookProvider(() -> notebook);
    server.setNotebookServiceProvider(() -> notebookService);
    final Note note = mock(Note.class, RETURNS_DEEP_STUBS);

    when(notebook.getNote("noteId")).thenReturn(note);
    final Paragraph paragraph = mock(Paragraph.class, RETURNS_DEEP_STUBS);
    when(note.getParagraph("paragraphId")).thenReturn(paragraph);

    final RemoteAngularObjectRegistry mdRegistry = mock(RemoteAngularObjectRegistry.class);
    final InterpreterGroup mdGroup = new InterpreterGroup("mdGroup");
    mdGroup.setAngularObjectRegistry(mdRegistry);

    when(paragraph.getBindedInterpreter().getInterpreterGroup()).thenReturn(mdGroup);

    final AngularObject<String> ao1 = AngularObjectBuilder.build(varName, value, "noteId",
            "paragraphId");

    when(mdRegistry.addAndNotifyRemoteProcess(varName, value, "noteId", "paragraphId"))
            .thenReturn(ao1);

    NotebookSocket conn = mock(NotebookSocket.class);
    NotebookSocket otherConn = mock(NotebookSocket.class);

    final String mdMsg1 =  server.serializeMessage(new Message(OP.ANGULAR_OBJECT_UPDATE)
            .put("angularObject", ao1)
            .put("interpreterGroupId", "mdGroup")
            .put("noteId", "noteId")
            .put("paragraphId", "paragraphId"));

    server.getConnectionManager().noteSocketMap.put("noteId", asList(conn, otherConn));

    // When
    server.angularObjectClientBind(conn, messageReceived);

    // Then
    verify(mdRegistry, never()).addAndNotifyRemoteProcess(varName, value, "noteId", null);

    verify(otherConn).send(mdMsg1);

    // reset it to original notebook
    //ZeppelinServer.notebook = originalNotebook;
  }

  @Test
  public void unbindAngularObjectFromRemoteForParagraphs() throws Exception {
    //Given
    final String varName = "name";
    final String value = "val";
    final Message messageReceived = new Message(OP.ANGULAR_OBJECT_CLIENT_UNBIND)
            .put("noteId", "noteId")
            .put("name", varName)
            .put("paragraphId", "paragraphId");

    final Notebook notebook = mock(Notebook.class);
    final NotebookServer server = new NotebookServer();
    server.setNotebookProvider(() -> notebook);
    server.setNotebookServiceProvider(() -> notebookService);
    final Note note = mock(Note.class, RETURNS_DEEP_STUBS);
    when(notebook.getNote("noteId")).thenReturn(note);
    final Paragraph paragraph = mock(Paragraph.class, RETURNS_DEEP_STUBS);
    when(note.getParagraph("paragraphId")).thenReturn(paragraph);

    final RemoteAngularObjectRegistry mdRegistry = mock(RemoteAngularObjectRegistry.class);
    final InterpreterGroup mdGroup = new InterpreterGroup("mdGroup");
    mdGroup.setAngularObjectRegistry(mdRegistry);

    when(paragraph.getBindedInterpreter().getInterpreterGroup()).thenReturn(mdGroup);

    final AngularObject<String> ao1 = AngularObjectBuilder.build(varName, value, "noteId",
            "paragraphId");
    when(mdRegistry.removeAndNotifyRemoteProcess(varName, "noteId", "paragraphId")).thenReturn(ao1);
    NotebookSocket conn = mock(NotebookSocket.class);
    NotebookSocket otherConn = mock(NotebookSocket.class);

    final String mdMsg1 =  server.serializeMessage(new Message(OP.ANGULAR_OBJECT_REMOVE)
            .put("angularObject", ao1)
            .put("interpreterGroupId", "mdGroup")
            .put("noteId", "noteId")
            .put("paragraphId", "paragraphId"));

    server.getConnectionManager().noteSocketMap.put("noteId", asList(conn, otherConn));

    // When
    server.angularObjectClientUnbind(conn, messageReceived);

    // Then
    verify(mdRegistry, never()).removeAndNotifyRemoteProcess(varName, "noteId", null);

    verify(otherConn).send(mdMsg1);
  }

  @Test
  public void testCreateNoteWithDefaultInterpreterId() throws IOException {
    // create two sockets and open it
    NotebookSocket sock1 = createWebSocket();
    NotebookSocket sock2 = createWebSocket();

    assertEquals(sock1, sock1);
    assertNotEquals(sock1, sock2);

    notebookServer.onOpen(sock1);
    notebookServer.onOpen(sock2);

    String noteName = "Note with millis " + System.currentTimeMillis();
    String defaultInterpreterId = "";
    List<InterpreterSetting> settings = notebook.getInterpreterSettingManager().get();
    if (settings.size() > 1) {
      defaultInterpreterId = settings.get(0).getId();
    }
    // create note from sock1
    notebookServer.onMessage(sock1,
        new Message(OP.NEW_NOTE)
        .put("name", noteName)
        .put("defaultInterpreterId", defaultInterpreterId).toJson());

    int sendCount = 2;
    if (ZeppelinConfiguration.create().isZeppelinNotebookCollaborativeModeEnable()) {
      sendCount++;
    }
    // expect the events are broadcasted properly
    verify(sock1, times(sendCount)).send(anyString());

    Note createdNote = null;
    for (Note note : notebook.getAllNotes()) {
      if (note.getName().equals(noteName)) {
        createdNote = note;
        break;
      }
    }

    if (settings.size() > 1) {
      assertEquals(notebook.getInterpreterSettingManager().getDefaultInterpreterSetting(
              createdNote.getId()).getId(), defaultInterpreterId);
    }
    notebook.removeNote(createdNote.getId(), anonymous);
  }

  private NotebookSocket createWebSocket() {
    NotebookSocket sock = mock(NotebookSocket.class);
    when(sock.getRequest()).thenReturn(mockRequest);
    return sock;
  }
}
