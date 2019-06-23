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
package org.apache.zeppelin.cluster;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.thrift.TException;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.rest.message.NewParagraphRequest;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ClusterEventTest extends ZeppelinServerMock {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterEventTest.class);

  private static List<ClusterAuthEventListenerTest> clusterAuthEventListenerTests = new ArrayList<>();
  private static List<ClusterNoteEventListenerTest> clusterNoteEventListenerTests = new ArrayList<>();
  private static List<ClusterNoteAuthEventListenerTest> clusterNoteAuthEventListenerTests = new ArrayList<>();

  private static List<ClusterManagerServer> clusterServers = new ArrayList<>();
  private static ClusterManagerClient clusterClient = null;
  static final String metaKey = "ClusterMultiNodeTestKey";

  private static Notebook notebook;
  private static NotebookServer notebookServer;
  private static SchedulerService schedulerService;
  private static NotebookService notebookService;
  private static AuthorizationService authorizationService;
  private HttpServletRequest mockRequest;
  private AuthenticationInfo anonymous;

  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    ZeppelinConfiguration zconf = genZeppelinConf();

    ZeppelinServerMock.startUp(ClusterEventTest.class.getSimpleName(), zconf);
    notebook = TestUtils.getInstance(Notebook.class);
    authorizationService = new AuthorizationService(notebook, notebook.getConf());
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    schedulerService = new QuartzSchedulerService(conf, notebook);
    notebookServer = spy(NotebookServer.getInstance());
    notebookService =
        new NotebookService(notebook, authorizationService, conf, schedulerService);

    ConfigurationService configurationService = new ConfigurationService(notebook.getConf());
    when(notebookServer.getNotebookService()).thenReturn(notebookService);
    when(notebookServer.getConfigurationService()).thenReturn(configurationService);

    startOtherZeppelinClusterNode(zconf);
  }

  @AfterClass
  public static void destroy() throws Exception {
    ZeppelinServerMock.shutDown();

    if (null != clusterClient) {
      clusterClient.shutdown();
    }
    for (ClusterManagerServer clusterServer : clusterServers) {
      clusterServer.shutdown();
    }
    LOGGER.info("stopCluster <<<");
  }

  @Before
  public void setUp() {
    mockRequest = mock(HttpServletRequest.class);
    anonymous = new AuthenticationInfo("anonymous");
  }

  private static ZeppelinConfiguration genZeppelinConf()
      throws IOException, InterruptedException {
    String clusterAddrList = "";
    String zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    for (int i = 0; i < 3; i ++) {
      // Set the cluster IP and port
      int zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      clusterAddrList += zServerHost + ":" + zServerPort;
      if (i != 2) {
        clusterAddrList += ",";
      }
    }
    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    zconf.setClusterAddress(clusterAddrList);
    LOGGER.info("clusterAddrList = {}", clusterAddrList);

    return zconf;
  }

  public static ClusterManagerServer startClusterSingleNode(String clusterAddrList, String clusterHost, int clusterPort)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class clazz = ClusterManagerServer.class;
    Constructor constructor = clazz.getDeclaredConstructor();
    constructor.setAccessible(true);
    ClusterManagerServer clusterServer = (ClusterManagerServer) constructor.newInstance();
    clusterServer.initTestCluster(clusterAddrList, clusterHost, clusterPort);

    clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_NOTE_EVENT_TOPIC, notebookServer);
    clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_AUTH_EVENT_TOPIC, authorizationService);
    return clusterServer;
  }

  //
  public static void startOtherZeppelinClusterNode(ZeppelinConfiguration zconf)
      throws IOException, InterruptedException {
    LOGGER.info("startCluster >>>");
    String clusterAddrList = zconf.getClusterAddress();

    // mock cluster manager server
    String cluster[] = clusterAddrList.split(",");
    try {
      // NOTE: cluster[2] is ZeppelinServerMock
      for (int i = 0; i < 2; i ++) {
        String[] parts = cluster[i].split(":");
        String clusterHost = parts[0];
        int clusterPort = Integer.valueOf(parts[1]);

        // ClusterSingleNodeMock clusterSingleNodeMock = new ClusterSingleNodeMock();
        ClusterManagerServer clusterServer
            = startClusterSingleNode(clusterAddrList, clusterHost, clusterPort);
        clusterServers.add(clusterServer);
        // clusterSingleNodeMockList.add(clusterSingleNodeMock);
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }

    for (ClusterManagerServer clusterServer : clusterServers) {
      ClusterAuthEventListenerTest clusterAuthEventListenerTest = new ClusterAuthEventListenerTest();
      clusterAuthEventListenerTests.add(clusterAuthEventListenerTest);
      clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_AUTH_EVENT_TOPIC, clusterAuthEventListenerTest);

      ClusterNoteEventListenerTest clusterNoteEventListenerTest = new ClusterNoteEventListenerTest();
      clusterNoteEventListenerTests.add(clusterNoteEventListenerTest);
      clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_NOTE_EVENT_TOPIC, clusterNoteEventListenerTest);

      ClusterNoteAuthEventListenerTest clusterNoteAuthEventListenerTest = new ClusterNoteAuthEventListenerTest();
      clusterNoteAuthEventListenerTests.add(clusterNoteAuthEventListenerTest);
      clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_NB_AUTH_EVENT_TOPIC, clusterNoteAuthEventListenerTest);

      clusterServer.start();
    }

    // mock cluster manager client
    clusterClient = ClusterManagerClient.getInstance();
    clusterClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while(wait++ < 100) {
      if (clusterIsStartup() && clusterClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait*3000);
        break;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    Thread.sleep(3000);
    assertEquals(true, clusterIsStartup());

    getClusterServerMeta();
    LOGGER.info("startCluster <<<");
  }

  private void checkClusterNoteEventListener() {
    for (ClusterNoteEventListenerTest clusterNoteEventListenerTest : clusterNoteEventListenerTests) {
      assertNotNull(clusterNoteEventListenerTest.receiveMsg);
    }
  }

  private void checkClusterAuthEventListener() {
    for (ClusterAuthEventListenerTest clusterAuthEventListenerTest : clusterAuthEventListenerTests) {
      assertNotNull(clusterAuthEventListenerTest.receiveMsg);
    }
  }

  private void checkClusterNoteAuthEventListener() {
    for (ClusterNoteAuthEventListenerTest clusterNoteAuthEventListenerTest : clusterNoteAuthEventListenerTests) {
      assertNotNull(clusterNoteAuthEventListenerTest.receiveMsg);
    }
  }

  static boolean clusterIsStartup() {
    boolean foundLeader = false;
    for (ClusterManagerServer clusterServer : clusterServers) {
      if (!clusterServer.raftInitialized()) {
        LOGGER.warn("clusterServer not Initialized!");
        return false;
      }
    }

    return true;
  }

  public static void getClusterServerMeta() {
    LOGGER.info("getClusterServerMeta >>>");
    // Get metadata for all services
    Object srvMeta = clusterClient.getClusterMeta(ClusterMetaType.SERVER_META, "");
    LOGGER.info(srvMeta.toString());

    Object intpMeta = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    LOGGER.info(intpMeta.toString());

    assertNotNull(srvMeta);
    assertEquals(true, (srvMeta instanceof HashMap));
    HashMap hashMap = (HashMap) srvMeta;

    assertEquals(hashMap.size(), 3);
    LOGGER.info("getClusterServerMeta <<<");
  }

  @Test
  public void testRenameNoteEvent() throws IOException {
    Note note = null;
    try {
      String oldName = "old_name";
      note = TestUtils.getInstance(Notebook.class).createNote(oldName, anonymous);
      assertEquals(note.getName(), oldName);
      String noteId = note.getId();

      final String newName = "testName";
      String jsonRequest = "{\"name\": " + newName + "}";

      PutMethod put = httpPut("/notebook/" + noteId + "/rename/", jsonRequest);
      assertThat("test testRenameNote:", put, isAllowed());
      put.releaseConnection();

      assertEquals(note.getName(), newName);

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note.getId(), anonymous);
      }
    }
  }
  @Test
  public void testCloneNoteEvent() throws IOException {
    Note note1 = null;
    String clonedNoteId = null;
    try {
      note1 = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      PostMethod post = httpPost("/notebook/" + note1.getId(), "");
      LOG.info("testCloneNote response\n" + post.getResponseBodyAsString());
      assertThat(post, isAllowed());
      Map<String, Object> resp = gson.fromJson(post.getResponseBodyAsString(),
          new TypeToken<Map<String, Object>>() {}.getType());
      clonedNoteId = (String) resp.get("body");
      post.releaseConnection();

      GetMethod get = httpGet("/notebook/" + clonedNoteId);
      assertThat(get, isAllowed());
      Map<String, Object> resp2 = gson.fromJson(get.getResponseBodyAsString(),
          new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> resp2Body = (Map<String, Object>) resp2.get("body");

      get.releaseConnection();

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note1) {
        TestUtils.getInstance(Notebook.class).removeNote(note1.getId(), anonymous);
      }
      if (null != clonedNoteId) {
        TestUtils.getInstance(Notebook.class).removeNote(clonedNoteId, anonymous);
      }
    }
  }

  @Test
  public void insertParagraphEvent() throws IOException {
    Note note = null;
    try {
      // Create note and set result explicitly
      note = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
          InterpreterResult.Type.TEXT, "result");
      p1.setResult(result);

      // insert new paragraph
      NewParagraphRequest newParagraphRequest = new NewParagraphRequest();

      PostMethod post = httpPost("/notebook/" + note.getId() + "/paragraph", newParagraphRequest.toJson());
      LOG.info("test clear paragraph output response\n" + post.getResponseBodyAsString());
      assertThat(post, isAllowed());
      post.releaseConnection();

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note.getId(), anonymous);
      }
    }
  }

  @Test
  public void testClusterAuthEvent() throws IOException {
    Note note = null;

    try {
      note = notebook.createNote("note1", anonymous);
      Paragraph p1 = note.addNewParagraph(anonymous);
      p1.setText("%md start remote interpreter process");
      p1.setAuthenticationInfo(anonymous);
      notebookServer.getNotebook().saveNote(note, anonymous);

      String noteId = note.getId();
      String user1Id = "user1", user2Id = "user2";

      // test user1 can get anonymous's note
      List<ParagraphInfo> paragraphList0 = null;
      try {
        paragraphList0 = notebookServer.getParagraphList(user1Id, noteId);
      } catch (ServiceException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (TException e) {
        LOGGER.error(e.getMessage(), e);
      }
      assertNotNull(user1Id + " can get anonymous's note", paragraphList0);

      // test user1 cannot get user2's note
      authorizationService.setOwners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();

      authorizationService.setReaders(noteId, new HashSet<>(Arrays.asList(user2Id)));
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();

      authorizationService.setRunners(noteId, new HashSet<>(Arrays.asList(user2Id)));
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();

      authorizationService.setWriters(noteId, new HashSet<>(Arrays.asList(user2Id)));
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();

      Set<String> roles = Sets.newHashSet("admin");
      // set admin roles for both user1 and user2
      authorizationService.setRoles(user2Id, roles);
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();

      authorizationService.clearPermission(noteId);
      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterAuthEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (null != note) {
        notebook.removeNote(note.getId(), anonymous);
      }
    }
  }
}
