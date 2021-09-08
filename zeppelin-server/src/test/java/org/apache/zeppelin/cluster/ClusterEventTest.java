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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.thrift.TException;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.rest.message.NewParagraphRequest;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.hamcrest.MatcherAssert;
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
import java.nio.charset.StandardCharsets;
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
  private static List<ClusterIntpSettingEventListenerTest> clusterIntpSettingEventListenerTests = new ArrayList<>();

  private static List<ClusterManagerServer> clusterServers = new ArrayList<>();
  private static ClusterManagerClient clusterClient = null;
  static final String metaKey = "ClusterEventTestKey";

  private static Notebook notebook;
  private static NotebookServer notebookServer;
  private static QuartzSchedulerService schedulerService;
  private static NotebookService notebookService;
  private static AuthorizationService authorizationService;
  private HttpServletRequest mockRequest;
  private AuthenticationInfo anonymous;

  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    ZeppelinConfiguration zconf = genZeppelinConf();

    ZeppelinServerMock.startUp("ClusterEventTest", zconf);
    notebook = TestUtils.getInstance(Notebook.class);
    authorizationService = TestUtils.getInstance(AuthorizationService.class);

    schedulerService = new QuartzSchedulerService(zconf, notebook);
    schedulerService.waitForFinishInit();
    notebookServer = spy(NotebookServer.getInstance());
    notebookService = new NotebookService(notebook, authorizationService, zconf, schedulerService);

    ConfigurationService configurationService = new ConfigurationService(notebook.getConf());
    when(notebookServer.getNotebookService()).thenReturn(notebookService);
    when(notebookServer.getConfigurationService()).thenReturn(configurationService);

    startOtherZeppelinClusterNode(zconf);

    // wait zeppelin cluster startup
    Thread.sleep(10000);
    // mock cluster manager client
    clusterClient = ClusterManagerClient.getInstance(zconf);
    clusterClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while(wait++ < 100) {
      if (clusterIsStartup() && clusterClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait*500);
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
  }

  @AfterClass
  public static void destroy() throws Exception {
    try {
      if (null != clusterClient) {
        clusterClient.shutdown();
      }
      for (ClusterManagerServer clusterServer : clusterServers) {
        clusterServer.shutdown();
      }

      ZeppelinServerMock.shutDown();
    } finally {
      // Because zconf is a single instance, it needs clean cluster address
      ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
      zconf.setClusterAddress("");
    }
    ZeppelinConfiguration.reset();
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

  public static ClusterManagerServer startClusterSingleNode(String clusterAddrList,
                                                            String clusterHost,
                                                            int clusterPort,
                                                            ZeppelinConfiguration zConf)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class clazz = ClusterManagerServer.class;
    Constructor constructor = clazz.getDeclaredConstructor(ZeppelinConfiguration.class);
    constructor.setAccessible(true);
    ClusterManagerServer clusterServer = (ClusterManagerServer) constructor.newInstance(zConf);
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

        ClusterManagerServer clusterServer
            = startClusterSingleNode(clusterAddrList, clusterHost, clusterPort, zconf);
        clusterServers.add(clusterServer);
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

      ClusterIntpSettingEventListenerTest clusterIntpSettingEventListenerTest = new ClusterIntpSettingEventListenerTest();
      clusterIntpSettingEventListenerTests.add(clusterIntpSettingEventListenerTest);
      clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_INTP_SETTING_EVENT_TOPIC, clusterIntpSettingEventListenerTest);

      clusterServer.start();
    }

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

  private void checkClusterIntpSettingEventListener() {
    for (ClusterIntpSettingEventListenerTest clusterIntpSettingEventListenerTest : clusterIntpSettingEventListenerTests) {
      assertNotNull(clusterIntpSettingEventListenerTest.receiveMsg);
    }
  }

  static boolean clusterIsStartup() {
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

    LOGGER.info("getClusterServerMeta <<< ");
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

      CloseableHttpResponse put = AbstractTestRestApi.httpPut("/notebook/" + noteId + "/rename/", jsonRequest);
      assertThat("test testRenameNote:", put, AbstractTestRestApi.isAllowed());
      put.close();

      assertEquals(note.getName(), newName);

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testCloneNoteEvent() throws IOException {
    Note note1 = null;
    String clonedNoteId = null;
    try {
      note1 = TestUtils.getInstance(Notebook.class).createNote("note1", anonymous);
      Thread.sleep(1000);

      CloseableHttpResponse post = AbstractTestRestApi.httpPost("/notebook/" + note1.getId(), "");
      LOG.info("testCloneNote response\n" + post.getStatusLine().getReasonPhrase());
      assertThat(post, AbstractTestRestApi.isAllowed());

      Map<String, Object> resp = gson.fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>() {}.getType());
      clonedNoteId = (String) resp.get("body");
      post.close();
      Thread.sleep(1000);

      CloseableHttpResponse get = AbstractTestRestApi.httpGet("/notebook/" + clonedNoteId);
      assertThat(get, AbstractTestRestApi.isAllowed());
      Map<String, Object> resp2 = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
          new TypeToken<Map<String, Object>>() {}.getType());
      Map<String, Object> resp2Body = (Map<String, Object>) resp2.get("body");

      get.close();

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note1) {
        TestUtils.getInstance(Notebook.class).removeNote(note1, anonymous);
      }
      Note clonedNote = TestUtils.getInstance(Notebook.class).getNote(clonedNoteId);
      if (null != clonedNote) {
        TestUtils.getInstance(Notebook.class).removeNote(clonedNote, anonymous);
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

      CloseableHttpResponse post = AbstractTestRestApi.httpPost("/notebook/" + note.getId() + "/paragraph", newParagraphRequest.toJson());
      LOG.info("test clear paragraph output response\n" + EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8));
      assertThat(post, AbstractTestRestApi.isAllowed());
      post.close();

      // wait cluster sync event
      Thread.sleep(1000);
      checkClusterNoteEventListener();
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      // cleanup
      if (null != note) {
        TestUtils.getInstance(Notebook.class).removeNote(note, anonymous);
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

      Set<String> roles = new HashSet<>(Arrays.asList("admin"));
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
        notebook.removeNote(note, anonymous);
      }
    }
  }

  @Test
  public void testInterpreterEvent() throws IOException, InterruptedException {
    // when: Create 1 interpreter settings `sh1`
    String md1Name = "sh1";

    String md1Dep = "org.apache.drill.exec:drill-jdbc:jar:1.7.0";

    String reqBody1 = "{\"name\":\"" + md1Name + "\",\"group\":\"sh\"," +
        "\"properties\":{\"propname\": {\"value\": \"propvalue\", \"name\": \"propname\", " +
        "\"type\": \"textarea\"}}," +
        "\"interpreterGroup\":[{\"class\":\"org.apache.zeppelin.shell.ShellInterpreter\"," +
        "\"name\":\"md\"}]," +
        "\"dependencies\":[ {\n" +
        "      \"groupArtifactVersion\": \"" + md1Dep + "\",\n" +
        "      \"exclusions\":[]\n" +
        "    }]," +
        "\"option\": { \"remote\": true, \"session\": false }}";
    CloseableHttpResponse post = AbstractTestRestApi.httpPost("/interpreter/setting", reqBody1);
    String postResponse = EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8);
    LOG.info("testCreatedInterpreterDependencies create response\n" + postResponse);
    InterpreterSetting created = convertResponseToInterpreterSetting(postResponse);
    MatcherAssert.assertThat("test create method:", post, AbstractTestRestApi.isAllowed());
    post.close();

    // 1. Call settings API
    CloseableHttpResponse get = AbstractTestRestApi.httpGet("/interpreter/setting");
    String rawResponse = EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8);
    get.close();

    // 2. Parsing to List<InterpreterSettings>
    JsonObject responseJson = gson.fromJson(rawResponse, JsonElement.class).getAsJsonObject();
    JsonArray bodyArr = responseJson.getAsJsonArray("body");
    List<InterpreterSetting> settings = new Gson().fromJson(bodyArr,
        new TypeToken<ArrayList<InterpreterSetting>>() {
        }.getType());

    // 3. Filter interpreters out we have just created
    InterpreterSetting md1 = null;
    for (InterpreterSetting setting : settings) {
      if (md1Name.equals(setting.getName())) {
        md1 = setting;
      }
    }

    // then: should get created interpreters which have different dependencies

    // 4. Validate each md interpreter has its own dependencies
    assertEquals(1, md1.getDependencies().size());
    assertEquals(md1Dep, md1.getDependencies().get(0).getGroupArtifactVersion());
    Thread.sleep(1000);
    checkClusterIntpSettingEventListener();

    // 2. test update Interpreter
    String rawRequest = "{\"name\":\"sh1\",\"group\":\"sh\"," +
        "\"properties\":{\"propname\": {\"value\": \"propvalue\", \"name\": \"propname\", " +
        "\"type\": \"textarea\"}}," +
        "\"interpreterGroup\":[{\"class\":\"org.apache.zeppelin.markdown.Markdown\"," +
        "\"name\":\"md\"}],\"dependencies\":[]," +
        "\"option\": { \"remote\": true, \"session\": false }}";
    JsonObject jsonRequest = gson.fromJson(rawRequest, JsonElement.class).getAsJsonObject();

    // when: call update setting API
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("name", "propname2");
    jsonObject.addProperty("value", "this is new prop");
    jsonObject.addProperty("type", "textarea");
    jsonRequest.getAsJsonObject("properties").add("propname2", jsonObject);
    CloseableHttpResponse put = AbstractTestRestApi.httpPut("/interpreter/setting/" + created.getId(), jsonRequest.toString());
    LOG.info("testSettingCRUD update response\n" + EntityUtils.toString(put.getEntity(), StandardCharsets.UTF_8));
    // then: call update setting API
    MatcherAssert.assertThat("test update method:", put, AbstractTestRestApi.isAllowed());
    put.close();
    Thread.sleep(1000);
    checkClusterIntpSettingEventListener();

    // 3: call delete setting API
    CloseableHttpResponse delete = AbstractTestRestApi.httpDelete("/interpreter/setting/" + created.getId());
    LOG.info("testSettingCRUD delete response\n" + EntityUtils.toString(delete.getEntity(), StandardCharsets.UTF_8));
    // then: call delete setting API
    MatcherAssert.assertThat("Test delete method:", delete, AbstractTestRestApi.isAllowed());
    delete.close();
    Thread.sleep(1000);
    checkClusterIntpSettingEventListener();
  }

  private JsonObject getBodyFieldFromResponse(String rawResponse) {
    JsonObject response = gson.fromJson(rawResponse, JsonElement.class).getAsJsonObject();
    return response.getAsJsonObject("body");
  }

  private InterpreterSetting convertResponseToInterpreterSetting(String rawResponse) {
    return gson.fromJson(getBodyFieldFromResponse(rawResponse), InterpreterSetting.class);
  }
}
