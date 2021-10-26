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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.thrift.TException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectBuilder;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.Notebook.NoteProcessor;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;


/** Basic REST API tests for notebookServer. */
public class NotebookServerTest extends AbstractTestRestApi {
  private static Notebook notebook;
  private static NotebookServer notebookServer;
  private static NotebookService notebookService;
  private static AuthorizationService authorizationService;
  private HttpServletRequest mockRequest;
  private AuthenticationInfo anonymous;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp(NotebookServerTest.class.getSimpleName());
    notebook = TestUtils.getInstance(Notebook.class);
    authorizationService =  TestUtils.getInstance(AuthorizationService.class);
    notebookServer = TestUtils.getInstance(NotebookServer.class);
    notebookService = TestUtils.getInstance(NotebookService.class);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {
    mockRequest = mock(HttpServletRequest.class);
    anonymous = AuthenticationInfo.ANONYMOUS;
  }

  @Test
  public void checkOrigin() throws UnknownHostException {
    String origin = "http://" + InetAddress.getLocalHost().getHostName() + ":8080";
    assertTrue("Origin " + origin + " is not allowed. Please check your hostname.",
          notebookServer.checkOrigin(origin));
  }

  @Test
  public void checkInvalidOrigin(){
    assertFalse(notebookServer.checkOrigin("http://evillocalhost:8080"));
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
    NoteInfo createdNoteInfo = null;
    for (NoteInfo noteInfo : notebook.getNotesInfo()) {
      if (notebook.processNote(noteInfo.getId(), Note::getName).equals(noteName)) {
        createdNoteInfo = noteInfo;
        break;
      }
    }

    Message message = new Message(OP.GET_NOTE).put("id", createdNoteInfo.getId());
    notebookServer.onMessage(sock1, message.toJson());
    notebookServer.onMessage(sock2, message.toJson());

    Paragraph paragraph = notebook.processNote(createdNoteInfo.getId(),
      createdNote -> {
        return createdNote.getParagraphs().get(0);
      });
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

    notebook.removeNote(createdNoteInfo.getId(), anonymous);
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
    String note1Id = null;
    try {
      // create a notebook
      note1Id = notebook.createNote("note1", anonymous);

      // get reference to interpreterGroup
      InterpreterGroup interpreterGroup = null;
      List<InterpreterSetting> settings = notebook.getInterpreterSettingManager().get();
      for (InterpreterSetting setting : settings) {
        if (setting.getName().equals("md")) {
          interpreterGroup = setting.getOrCreateInterpreterGroup("anonymous", note1Id);
          break;
        }
      }

      notebook.processNote(note1Id,
        note1 -> {
          // start interpreter process
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%md start remote interpreter process");
          p1.setAuthenticationInfo(anonymous);
          note1.run(p1.getId());
          return null;
        });

      Status status = notebook.processNote(note1Id, note1-> note1.getParagraph(0).getStatus());
      // wait for paragraph finished
      while (true) {
        if (status == Job.Status.FINISHED) {
          break;
        }
        Thread.sleep(100);
        status = notebook.processNote(note1Id, note1-> note1.getParagraph(0).getStatus());
      }
      // sleep for 1 second to make sure job running thread finish to fire event. See ZEPPELIN-3277
      Thread.sleep(1000);

      // add angularObject
      interpreterGroup.getAngularObjectRegistry().add("object1", "value1", note1Id, null);

      // create two sockets and open it
      NotebookSocket sock1 = createWebSocket();
      NotebookSocket sock2 = createWebSocket();

      assertEquals(sock1, sock1);
      assertNotEquals(sock1, sock2);

      notebookServer.onOpen(sock1);
      notebookServer.onOpen(sock2);
      verify(sock1, times(0)).send(anyString()); // getNote, getAngularObject
      // open the same notebook from sockets
      notebookServer.onMessage(sock1, new Message(OP.GET_NOTE).put("id", note1Id).toJson());
      notebookServer.onMessage(sock2, new Message(OP.GET_NOTE).put("id", note1Id).toJson());

      reset(sock1);
      reset(sock2);

      // update object from sock1
      notebookServer.onMessage(sock1,
              new Message(OP.ANGULAR_OBJECT_UPDATED)
                      .put("noteId", note1Id)
                      .put("name", "object1")
                      .put("value", "value1")
                      .put("interpreterGroupId", interpreterGroup.getId()).toJson());


      // expect object is broadcasted except for where the update is created
      verify(sock1, times(0)).send(anyString());
      verify(sock2, times(1)).send(anyString());
    } finally {
      if (note1Id != null) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testAngularObjectSaveToNote()
      throws IOException, InterruptedException {
    // create a notebook
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note1", "angular", anonymous);

      // get reference to interpreterGroup
      InterpreterGroup interpreterGroup = null;
      List<InterpreterSetting> settings = notebook.processNote(note1Id, note1-> note1.getBindedInterpreterSettings(new ArrayList<>()));
      for (InterpreterSetting setting : settings) {
        if (setting.getName().equals("angular")) {
          interpreterGroup = setting.getOrCreateInterpreterGroup("anonymous", note1Id);
          break;
        }
      }

      String p1Id = notebook.processNote(note1Id,
        note1 -> {
          // start interpreter process
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%angular <h2>Bind here : {{COMMAND_TYPE}}</h2>");
          p1.setAuthenticationInfo(anonymous);
          note1.run(p1.getId());
          return p1.getId();
        });

      // wait for paragraph finished
      Status status = notebook.processNote(note1Id, note1-> note1.getParagraph(p1Id).getStatus());
      while (true) {
        if (status == Job.Status.FINISHED) {
          break;
        }
        Thread.sleep(100);
        status = notebook.processNote(note1Id, note1-> note1.getParagraph(p1Id).getStatus());
      }
      // sleep for 1 second to make sure job running thread finish to fire event. See ZEPPELIN-3277
      Thread.sleep(1000);

      // create two sockets and open it
      NotebookSocket sock1 = createWebSocket();

      notebookServer.onOpen(sock1);
      verify(sock1, times(0)).send(anyString()); // getNote, getAngularObject
      // open the same notebook from sockets
      notebookServer.onMessage(sock1, new Message(OP.GET_NOTE).put("id", note1Id).toJson());

      reset(sock1);

      // bind object from sock1
      notebookServer.onMessage(sock1,
              new Message(OP.ANGULAR_OBJECT_CLIENT_BIND)
                      .put("noteId", note1Id)
                      .put("paragraphId", p1Id)
                      .put("name", "COMMAND_TYPE")
                      .put("value", "COMMAND_TYPE_VALUE")
                      .put("interpreterGroupId", interpreterGroup.getId()).toJson());
      List<AngularObject> list = notebook.processNote(note1Id, note1-> note1.getAngularObjects("angular-shared_process"));
      assertEquals(1, list.size());
      assertEquals(note1Id, list.get(0).getNoteId());
      assertEquals(p1Id, list.get(0).getParagraphId());
      assertEquals("COMMAND_TYPE", list.get(0).getName());
      assertEquals("COMMAND_TYPE_VALUE", list.get(0).get());
      // Check if the interpreterGroup AngularObjectRegistry is updated
      Map<String, Map<String, AngularObject>> mapRegistry = interpreterGroup.getAngularObjectRegistry().getRegistry();
      AngularObject ao = mapRegistry.get(note1Id + "_" + p1Id).get("COMMAND_TYPE");
      assertEquals("COMMAND_TYPE", ao.getName());
      assertEquals("COMMAND_TYPE_VALUE", ao.get());

      // update bind object from sock1
      notebookServer.onMessage(sock1,
              new Message(OP.ANGULAR_OBJECT_UPDATED)
                      .put("noteId", note1Id)
                      .put("paragraphId", p1Id)
                      .put("name", "COMMAND_TYPE")
                      .put("value", "COMMAND_TYPE_VALUE_UPDATE")
                      .put("interpreterGroupId", interpreterGroup.getId()).toJson());
      list = notebook.processNote(note1Id, note1-> note1.getAngularObjects("angular-shared_process"));
      assertEquals(1, list.size());
      assertEquals(note1Id, list.get(0).getNoteId());
      assertEquals(p1Id, list.get(0).getParagraphId());
      assertEquals("COMMAND_TYPE", list.get(0).getName());
      assertEquals("COMMAND_TYPE_VALUE_UPDATE", list.get(0).get());
      // Check if the interpreterGroup AngularObjectRegistry is updated
      mapRegistry = interpreterGroup.getAngularObjectRegistry().getRegistry();
      AngularObject ao1 = mapRegistry.get(note1Id + "_" + p1Id).get("COMMAND_TYPE");
      assertEquals("COMMAND_TYPE", ao1.getName());
      assertEquals("COMMAND_TYPE_VALUE_UPDATE", ao1.get());

      // unbind object from sock1
      notebookServer.onMessage(sock1,
              new Message(OP.ANGULAR_OBJECT_CLIENT_UNBIND)
                      .put("noteId", note1Id)
                      .put("paragraphId", p1Id)
                      .put("name", "COMMAND_TYPE")
                      .put("value", "COMMAND_TYPE_VALUE")
                      .put("interpreterGroupId", interpreterGroup.getId()).toJson());
      list = notebook.processNote(note1Id, note1-> note1.getAngularObjects("angular-shared_process"));
      assertEquals(0, list.size());
      // Check if the interpreterGroup AngularObjectRegistry is delete
      mapRegistry = interpreterGroup.getAngularObjectRegistry().getRegistry();
      AngularObject ao2 = mapRegistry.get(note1Id + "_" + p1Id).get("COMMAND_TYPE");
      assertNull(ao2);
    } finally {
      if (note1Id != null) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
  }

  @Test
  public void testLoadAngularObjectFromNote() throws IOException, InterruptedException {
    // create a notebook
    String note1Id = null;
    try {
      note1Id = notebook.createNote("note1", anonymous);

      // get reference to interpreterGroup
      InterpreterGroup interpreterGroup = null;
      List<InterpreterSetting> settings = notebook.getInterpreterSettingManager().get();
      for (InterpreterSetting setting : settings) {
        if (setting.getName().equals("angular")) {
          interpreterGroup = setting.getOrCreateInterpreterGroup("anonymous", note1Id);
          break;
        }
      }
      String p1Id = notebook.processNote(note1Id,
        note1 -> {
          // start interpreter process
          Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          p1.setText("%angular <h2>Bind here : {{COMMAND_TYPE}}</h2>");
          p1.setAuthenticationInfo(anonymous);
          note1.run(p1.getId());
          return p1.getId();
        });


      // wait for paragraph finished
      Status status = notebook.processNote(note1Id, note1-> note1.getParagraph(p1Id).getStatus());
      while (true) {
        System.out.println("loop");
        if (status == Job.Status.FINISHED) {
          break;
        }
        Thread.sleep(100);
        status = notebook.processNote(note1Id, note1-> note1.getParagraph(p1Id).getStatus());
      }
      // sleep for 1 second to make sure job running thread finish to fire event. See ZEPPELIN-3277
      Thread.sleep(1000);

      // set note AngularObject
      AngularObject ao = new AngularObject("COMMAND_TYPE", "COMMAND_TYPE_VALUE", note1Id, p1Id, null);
      notebook.processNote(note1Id,
        note1 -> {
          note1.addOrUpdateAngularObject("angular-shared_process", ao);
          return null;
        });


      // create sockets and open it
      NotebookSocket sock1 = createWebSocket();
      notebookServer.onOpen(sock1);

      // Check the AngularObjectRegistry of the interpreterGroup before executing GET_NOTE
      Map<String, Map<String, AngularObject>> mapRegistry1 = interpreterGroup.getAngularObjectRegistry().getRegistry();
      assertEquals(0, mapRegistry1.size());

      // open the notebook from sockets, AngularObjectRegistry that triggers the update of the interpreterGroup
      notebookServer.onMessage(sock1, new Message(OP.GET_NOTE).put("id", note1Id).toJson());
      Thread.sleep(1000);

      // After executing GET_NOTE, check the AngularObjectRegistry of the interpreterGroup
      Map<String, Map<String, AngularObject>> mapRegistry2 = interpreterGroup.getAngularObjectRegistry().getRegistry();
      assertEquals(2, mapRegistry1.size());
      AngularObject ao1 = mapRegistry2.get(note1Id + "_" + p1Id).get("COMMAND_TYPE");
      assertEquals("COMMAND_TYPE", ao1.getName());
      assertEquals("COMMAND_TYPE_VALUE", ao1.get());
    } finally {
      if (note1Id != null) {
        notebook.removeNote(note1Id, anonymous);
      }
    }
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
    String noteId = null;
    ServiceContext context = new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());
    try {
      try {
        noteId = notebookServer.importNote(null, context, messageReceived);
      } catch (NullPointerException e) {
        //broadcastNoteList(); failed nothing to worry.
        LOG.error("Exception in NotebookServerTest while testImportNotebook, failed nothing to " +
                "worry ", e);
      }

      notebook.processNote(noteId,
        note -> {
          assertNotNull(note);
          assertEquals("Test Zeppelin notebook import", note.getName());
          assertEquals("Test paragraphs import", note.getParagraphs().get(0)
            .getText());
          return null;
        });

    } finally {
      if (noteId != null) {
        notebook.removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testImportJupyterNote() throws IOException {
    String jupyterNoteJson = IOUtils.toString(getClass().getResourceAsStream("/Lecture-4.ipynb"), StandardCharsets.UTF_8);
    String msg = "{\"op\":\"IMPORT_NOTE\",\"data\":" +
            "{\"note\": " + jupyterNoteJson + "}}";
    Message messageReceived = notebookServer.deserializeMessage(msg);
    String noteId = null;
    ServiceContext context = new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());
    try {
      try {
        noteId = notebookServer.importNote(null, context, messageReceived);
      } catch (NullPointerException e) {
        //broadcastNoteList(); failed nothing to worry.
        LOG.error("Exception in NotebookServerTest while testImportJupyterNote, failed nothing to " +
                "worry ", e);
      }

      notebook.processNote(noteId,
        note -> {
          assertNotNull(note);
          assertTrue(note.getName(), note.getName().startsWith("Note converted from Jupyter_"));
          assertEquals("md", note.getParagraphs().get(0).getIntpText());
          assertEquals("\n# matplotlib - 2D and 3D plotting in Python",
            note.getParagraphs().get(0).getScriptText());
          return null;
        });



    } finally {
      if (noteId != null) {
        notebook.removeNote(noteId, anonymous);
      }
    }
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

    try {
      final Notebook notebook = mock(Notebook.class);
      notebookServer.setNotebook(() -> notebook);
      notebookServer.setNotebookService(() -> notebookService);
      final Note note = mock(Note.class, RETURNS_DEEP_STUBS);

      when(notebook.processNote(eq("noteId"), Mockito.any())).then(e -> e.getArgumentAt(1,NoteProcessor.class).process(note));
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

      final String mdMsg1 = notebookServer.serializeMessage(new Message(OP.ANGULAR_OBJECT_UPDATE)
              .put("angularObject", ao1)
              .put("interpreterGroupId", "mdGroup")
              .put("noteId", "noteId")
              .put("paragraphId", "paragraphId"));

      notebookServer.getConnectionManager().noteSocketMap.put("noteId", asList(conn, otherConn));

      // When
      notebookServer.angularObjectClientBind(conn, messageReceived);

      // Then
      verify(mdRegistry, never()).addAndNotifyRemoteProcess(varName, value, "noteId", null);

      verify(otherConn).send(mdMsg1);
    } finally {
      // reset these so that it won't affect other tests
      notebookServer.setNotebook(() -> NotebookServerTest.notebook);
      notebookServer.setNotebookService(() -> NotebookServerTest.notebookService);
    }
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

    try {
      final Notebook notebook = mock(Notebook.class);
      notebookServer.setNotebook(() -> notebook);
      notebookServer.setNotebookService(() -> notebookService);
      final Note note = mock(Note.class, RETURNS_DEEP_STUBS);
      when(notebook.processNote(eq("noteId"), Mockito.any())).then(e -> e.getArgumentAt(1,NoteProcessor.class).process(note));
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

      final String mdMsg1 = notebookServer.serializeMessage(new Message(OP.ANGULAR_OBJECT_REMOVE)
              .put("angularObject", ao1)
              .put("interpreterGroupId", "mdGroup")
              .put("noteId", "noteId")
              .put("paragraphId", "paragraphId"));

      notebookServer.getConnectionManager().noteSocketMap.put("noteId", asList(conn, otherConn));

      // When
      notebookServer.angularObjectClientUnbind(conn, messageReceived);

      // Then
      verify(mdRegistry, never()).removeAndNotifyRemoteProcess(varName, "noteId", null);

      verify(otherConn).send(mdMsg1);
    } finally {
      // reset these so that it won't affect other tests
      notebookServer.setNotebook(() -> NotebookServerTest.notebook);
      notebookServer.setNotebookService(() -> NotebookServerTest.notebookService);
    }
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

    String createdNoteId = null;
    for (NoteInfo noteInfo : notebook.getNotesInfo()) {
      ;
      if (notebook.processNote(noteInfo.getId(), Note::getName).equals(noteName)) {
        createdNoteId = noteInfo.getId();
        break;
      }
    }

    if (settings.size() > 1) {
      assertEquals(notebook.getInterpreterSettingManager().getDefaultInterpreterSetting(
        createdNoteId).getId(), defaultInterpreterId);
    }
    notebook.removeNote(createdNoteId, anonymous);
  }

  @Test
  public void testRuntimeInfos() throws IOException {
    // mock note
    String msg = "{\"op\":\"IMPORT_NOTE\",\"data\":" +
        "{\"note\":{\"paragraphs\": [{\"text\": \"Test " +
        "paragraphs import\"," + "\"progressUpdateIntervalMs\":500," +
        "\"config\":{},\"settings\":{}}]," +
        "\"name\": \"Test RuntimeInfos\",\"config\": " +
        "{}}}}";
    Message messageReceived = notebookServer.deserializeMessage(msg);
    String noteId = null;
    ServiceContext context = new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());
    try {
      noteId = notebookServer.importNote(null, context, messageReceived);
    } catch (NullPointerException e) {
      //broadcastNoteList(); failed nothing to worry.
      LOG.error("Exception in NotebookServerTest while testImportNotebook, failed nothing to " +
          "worry ", e);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // update RuntimeInfos
    Map<String, String> infos = new java.util.HashMap<>();
    String paragraphId = notebook.processNote(noteId,
      note -> {
        assertNotNull(note);
        assertNotNull(note.getParagraph(0));
        infos.put("jobUrl", "jobUrl_value");
        infos.put("jobLabel", "jobLabel_value");
        infos.put("label", "SPARK JOB");
        infos.put("tooltip", "View in Spark web UI");
        infos.put("noteId", note.getId());
        infos.put("paraId", note.getParagraph(0).getId());
        return note.getParagraph(0).getId();
      });

    notebookServer.onParaInfosReceived(noteId, paragraphId, "spark", infos);
    notebook.processNote(noteId,
      note -> {
        Paragraph paragraph = note.getParagraph(paragraphId);
        // check RuntimeInfos
        assertTrue(paragraph.getRuntimeInfos().containsKey("jobUrl"));
        Queue<Object> list = paragraph.getRuntimeInfos().get("jobUrl").getValue();
        assertEquals(1, list.size());
        Map<String, String> map = (Map<String, String>) list.poll();
        assertEquals(2, map.size());
        assertEquals(map.get("jobUrl"), "jobUrl_value");
        assertEquals(map.get("jobLabel"), "jobLabel_value");
        return null;
      });
  }

  @Test
  public void testGetParagraphList() throws IOException {
    String noteId = null;

    try {
      noteId = notebook.createNote("note1", anonymous);
      notebook.processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(anonymous);
          p1.setText("%md start remote interpreter process");
          p1.setAuthenticationInfo(anonymous);
          notebook.saveNote(note, anonymous);
          return null;
        });

      String user1Id = "user1", user2Id = "user2";

      // test user1 can get anonymous's note
      List<ParagraphInfo> paragraphList0 = null;
      try {
        paragraphList0 = notebookServer.getParagraphList(user1Id, noteId);
      } catch (ServiceException e) {
        e.printStackTrace();
      } catch (TException e) {
        e.printStackTrace();
      }
      assertNotNull(user1Id + " can get anonymous's note", paragraphList0);

      // test user1 cannot get user2's note
      authorizationService.setOwners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      authorizationService.setReaders(noteId, new HashSet<>(Arrays.asList(user2Id)));
      authorizationService.setRunners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      authorizationService.setWriters(noteId, new HashSet<>(Arrays.asList(user2Id)));
      List<ParagraphInfo> paragraphList1 = null;
      try {
        paragraphList1 = notebookServer.getParagraphList(user1Id, noteId);
      } catch (ServiceException e) {
        e.printStackTrace();
      } catch (TException e) {
        e.printStackTrace();
      }
      assertNull(user1Id + " cannot get " + user2Id + "'s note", paragraphList1);

      // test user1 can get user2's shared note
      authorizationService.setOwners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      authorizationService.setReaders(noteId, new HashSet<>(Arrays.asList(user1Id, user2Id)));
      authorizationService.setRunners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      authorizationService.setWriters(noteId, new HashSet<>(Arrays.asList(user2Id)));
      List<ParagraphInfo> paragraphList2 = null;
      try {
        paragraphList2 = notebookServer.getParagraphList(user1Id, noteId);
      } catch (ServiceException e) {
        e.printStackTrace();
      } catch (TException e) {
        e.printStackTrace();
      }
      assertNotNull(user1Id + " can get " + user2Id + "'s shared note", paragraphList2);
    } finally {
      if (null != noteId) {
        notebook.removeNote(noteId, anonymous);
      }
    }
  }

  @Test
  public void testNoteRevision() throws IOException {
    String noteId = null;

    try {
      noteId = notebook.createNote("note1", anonymous);
      notebook.processNote(noteId,
        note -> {
          assertEquals(0, note.getParagraphCount());
          NotebookRepoWithVersionControl.Revision firstRevision = notebook.checkpointNote(note.getId(), note.getPath(), "first commit", AuthenticationInfo.ANONYMOUS);
          List<NotebookRepoWithVersionControl.Revision> revisionList = notebook.listRevisionHistory(note.getId(), note.getPath(), AuthenticationInfo.ANONYMOUS);
          assertEquals(1, revisionList.size());
          assertEquals(firstRevision.id, revisionList.get(0).id);
          assertEquals("first commit", revisionList.get(0).message);

          // add one new paragraph and commit it
          note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
          assertEquals(1, note.getParagraphCount());
          NotebookRepoWithVersionControl.Revision secondRevision = notebook.checkpointNote(note.getId(), note.getPath(), "second commit", AuthenticationInfo.ANONYMOUS);

          revisionList = notebook.listRevisionHistory(note.getId(), note.getPath(), AuthenticationInfo.ANONYMOUS);
          assertEquals(2, revisionList.size());
          assertEquals(secondRevision.id, revisionList.get(0).id);
          assertEquals("second commit", revisionList.get(0).message);
          assertEquals(firstRevision.id, revisionList.get(1).id);
          assertEquals("first commit", revisionList.get(1).message);

          // checkout the first commit
          note = notebook.getNoteByRevision(note.getId(), note.getPath(), firstRevision.id, AuthenticationInfo.ANONYMOUS);
          assertEquals(0, note.getParagraphCount());
          return null;
        });

    } finally {
      if (null != noteId) {
        notebook.removeNote(noteId, anonymous);
      }
    }
  }

  private NotebookSocket createWebSocket() {
    NotebookSocket sock = mock(NotebookSocket.class);
    return sock;
  }
}
