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

import java.io.File;
import java.io.IOException;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.Message.OP;
import org.junit.After;
import org.junit.Before;
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
public class NotebookServerTest implements JobListenerFactory {

  private File tmpDir;
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private File notebookDir;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private InterpreterFactory factory;
  private NotebookServer notebookServer;
  private Gson gson;

  @Before
  public void setUp() throws Exception {
    gson = new Gson();
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();
    notebookDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/notebook");
    notebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    factory = new InterpreterFactory(conf, new InterpreterOption(false), null);

    notebookRepo = new VFSNotebookRepo(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, this);

    notebookServer = new NotebookServer();
    ZeppelinServer.notebook = notebook;
    ZeppelinServer.notebookServer = notebookServer;
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
  }

  private void delete(File file){
    if(file.isFile()) file.delete();
    else if(file.isDirectory()){
      File [] files = file.listFiles();
      if(files!=null && files.length>0){
        for(File f : files){
          delete(f);
        }
      }
      file.delete();
    }
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

    // open the same notebook from sockets
    notebookServer.onMessage(sock1, gson.toJson(new Message(OP.GET_NOTE).put("id", note1.getId())));
    notebookServer.onMessage(sock2, gson.toJson(new Message(OP.GET_NOTE).put("id", note1.getId())));


    // update object from sock1
    notebookServer.onMessage(sock1, gson.toJson(
        new Message(OP.ANGULAR_OBJECT_UPDATED)
        .put("noteId", note1.getId())
        .put("name", "object1")
        .put("value", "value1")
        .put("interpreterGroupId", interpreterGroup.getId())));


    // expect object is broadcasted except for where the update is created
    verify(sock1, times(2)).send(anyString()); // getNote, getAngularObject
    verify(sock2, times(3)).send(anyString()); // getNote, getAngularObject, updateAngularObject

    notebook.removeNote(note1.getId());
  }

  private NotebookSocket createWebSocket() {
    NotebookSocket sock = mock(NotebookSocket.class);
    when(sock.getRequest()).thenReturn(createHttpServletRequest());
    return sock;
  }

  private HttpServletRequest createHttpServletRequest() {
    return mock(HttpServletRequest.class);
  }

  @Override
  public JobListener getParagraphJobListener(Note note) {
    return null;
  }
}

