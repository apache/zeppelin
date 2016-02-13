/**
 * Created by joelz on 8/6/15.
 *
 *
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

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.Message.OP;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;

import java.net.UnknownHostException;
import java.net.InetAddress;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.*;


/**
 * BASIC Zeppelin rest api tests
 */
public class NotebookServerTest extends AbstractTestRestApi {


  private static Notebook notebook;
  private static NotebookServer notebookServer;
  private static Gson gson;

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
    gson = new Gson();
    notebook = ZeppelinServer.notebook;
    notebookServer = ZeppelinServer.notebookWsServer;
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void checkOrigin() throws UnknownHostException {
    NotebookServer server = new NotebookServer();
    String origin = "http://" + InetAddress.getLocalHost().getHostName() + ":8080";

    assertTrue("Origin " + origin + " is not allowed. Please check your hostname.",
          server.checkOrigin(new TestHttpServletRequest(), origin));
  }

  @Test
  public void checkInvalidOrigin(){
    NotebookServer server = new NotebookServer();
    assertFalse(server.checkOrigin(new TestHttpServletRequest(), "http://evillocalhost:8080"));
  }

  @Test
  public void testMakeSureNoAngularObjectBroadcastToWebsocketWhoFireTheEvent() throws IOException {
    // create a notebook
    Note note1 = notebook.createNote();

    // get reference to interpreterGroup
    InterpreterGroup interpreterGroup = null;
    List<InterpreterSetting> settings = note1.getNoteReplLoader().getInterpreterSettings();
    for (InterpreterSetting setting : settings) {
      if (setting.getInterpreterGroup() == null) {
        continue;
      }

      interpreterGroup = setting.getInterpreterGroup();
      break;
    }

    // add angularObject
    interpreterGroup.getAngularObjectRegistry().add("object1", "value1", note1.getId());

    // create two sockets and open it
    NotebookSocket sock1 = createWebSocket();
    NotebookSocket sock2 = createWebSocket();

    assertEquals(sock1, sock1);
    assertNotEquals(sock1, sock2);

    notebookServer.onOpen(sock1);
    notebookServer.onOpen(sock2);
    verify(sock1, times(0)).send(anyString()); // getNote, getAngularObject
    // open the same notebook from sockets
    notebookServer.onMessage(sock1, gson.toJson(new Message(OP.GET_NOTE).put("id", note1.getId())));
    notebookServer.onMessage(sock2, gson.toJson(new Message(OP.GET_NOTE).put("id", note1.getId())));

    reset(sock1);
    reset(sock2);

    // update object from sock1
    notebookServer.onMessage(sock1, gson.toJson(
        new Message(OP.ANGULAR_OBJECT_UPDATED)
        .put("noteId", note1.getId())
        .put("name", "object1")
        .put("value", "value1")
        .put("interpreterGroupId", interpreterGroup.getId())));


    // expect object is broadcasted except for where the update is created
    verify(sock1, times(0)).send(anyString());
    verify(sock2, times(1)).send(anyString());

    notebook.removeNote(note1.getId());
  }

  @Test
  public void testImportNotebook() throws IOException {
    String msg = "{\"op\":\"IMPORT_NOTE\",\"data\":" +
        "{\"notebook\":{\"paragraphs\": [{\"text\": \"Test " +
        "paragraphs import\",\"config\":{},\"settings\":{}}]," +
        "\"name\": \"Test Zeppelin notebook import\",\"config\": " +
        "{}}}}";
    Message messageReceived = notebookServer.deserializeMessage(msg);
    Note note = null;
    try {
      note = notebookServer.importNote(null, notebook, messageReceived);
    } catch (NullPointerException e) {
      //broadcastNoteList(); failed nothing to worry.
    }

    assertNotEquals(null, notebook.getNote(note.getId()));
    assertEquals("Test Zeppelin notebook import", notebook.getNote(note.getId()).getName());
    assertEquals("Test paragraphs import", notebook.getNote(note.getId()).getParagraphs().get(0).getText());
    notebook.removeNote(note.getId());
  }

  private NotebookSocket createWebSocket() {
    NotebookSocket sock = mock(NotebookSocket.class);
    when(sock.getRequest()).thenReturn(createHttpServletRequest());
    return sock;
  }

  private HttpServletRequest createHttpServletRequest() {
    return mock(HttpServletRequest.class);
  }
}

