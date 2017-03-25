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

package org.apache.zeppelin.interpreter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NullArgumentException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.sonatype.aether.RepositoryException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.mockito.Mock;

public class InterpreterFactoryTest {

  private InterpreterFactory factory;
  private InterpreterSettingManager interpreterSettingManager;
  private File tmpDir;
  private ZeppelinConfiguration conf;
  private InterpreterContext context;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private DependencyResolver depResolver;
  private SchedulerFactory schedulerFactory;
  private NotebookAuthorization notebookAuthorization;
  @Mock
  private JobListenerFactory jobListenerFactory;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();
    FileUtils.copyDirectory(new File("src/test/resources/interpreter"), new File(tmpDir, "interpreter"));

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETER_GROUP_ORDER.getVarName(),
        "mock1,mock2,mock11,dev");
    conf = new ZeppelinConfiguration();
    schedulerFactory = new SchedulerFactory();
    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);
    context = new InterpreterContext("note", "id", null, "title", "text", null, null, null, null, null, null, null);

    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    Properties intp1Properties = new Properties();
    intp1Properties.put("PROPERTY_1", "VALUE_1");
    intp1Properties.put("property_2", "value_2");
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(true), intp1Properties);

    ArrayList<InterpreterInfo> interpreterInfos2 = new ArrayList<>();
    interpreterInfos2.add(new InterpreterInfo(MockInterpreter2.class.getName(), "mock2", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock2", interpreterInfos2, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock2", null);
    interpreterSettingManager.createNewSetting("mock2", "mock2", new ArrayList<Dependency>(), new InterpreterOption(), new Properties());

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo(conf);
    notebookAuthorization = NotebookAuthorization.init(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, interpreterSettingManager, jobListenerFactory, search,
        notebookAuthorization, null);
  }

  @After
  public void tearDown() throws Exception {
    FileUtils.deleteDirectory(tmpDir);
  }

  @Test
  public void testBasic() {
    List<InterpreterSetting> all = interpreterSettingManager.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }

//    mock1Setting = factory.createNewSetting("mock11", "mock1", new ArrayList<Dependency>(), new InterpreterOption(false), new Properties());

    InterpreterGroup interpreterGroup = mock1Setting.getInterpreterGroup("user", "sharedProcess");
    factory.createInterpretersForNote(mock1Setting, "user", "sharedProcess", "session");

    // get interpreter
    assertNotNull("get Interpreter", interpreterGroup.get("session").get(0));

    // try to get unavailable interpreter
    assertNull(interpreterSettingManager.get("unknown"));

    // restart interpreter
    interpreterSettingManager.restart(mock1Setting.getId());
    assertNull(mock1Setting.getInterpreterGroup("user", "sharedProcess").get("session"));
  }

  @Test
  public void testRemoteRepl() throws Exception {
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    Properties intp1Properties = new Properties();
    intp1Properties.put("PROPERTY_1", "VALUE_1");
    intp1Properties.put("property_2", "value_2");
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(true), intp1Properties);
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);
    List<InterpreterSetting> all = interpreterSettingManager.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }
    InterpreterGroup interpreterGroup = mock1Setting.getInterpreterGroup("user", "sharedProcess");
    factory.createInterpretersForNote(mock1Setting, "user", "sharedProcess", "session");
    // get interpreter
    assertNotNull("get Interpreter", interpreterGroup.get("session").get(0));
    assertTrue(interpreterGroup.get("session").get(0) instanceof LazyOpenInterpreter);
    LazyOpenInterpreter lazyInterpreter = (LazyOpenInterpreter)(interpreterGroup.get("session").get(0));
    assertTrue(lazyInterpreter.getInnerInterpreter() instanceof RemoteInterpreter);
    RemoteInterpreter remoteInterpreter = (RemoteInterpreter) lazyInterpreter.getInnerInterpreter();
    assertEquals("VALUE_1", remoteInterpreter.getEnv().get("PROPERTY_1"));
    assertEquals("value_2", remoteInterpreter.getProperty("property_2"));
  }

  /**
   * 2 users' interpreters in scoped mode. Each user has one session. Restarting user1's interpreter
   * won't affect user2's interpreter
   * @throws Exception
   */
  @Test
  public void testRestartInterpreterInScopedMode() throws Exception {
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    Properties intp1Properties = new Properties();
    intp1Properties.put("PROPERTY_1", "VALUE_1");
    intp1Properties.put("property_2", "value_2");
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(true), intp1Properties);
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);
    List<InterpreterSetting> all = interpreterSettingManager.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }
    mock1Setting.getOption().setPerUser("scoped");
    mock1Setting.getOption().setPerNote("shared");
    // set remote as false so that we won't create new remote interpreter process
    mock1Setting.getOption().setRemote(false);
    mock1Setting.getOption().setHost("localhost");
    mock1Setting.getOption().setPort(2222);
    InterpreterGroup interpreterGroup = mock1Setting.getInterpreterGroup("user1", "sharedProcess");
    factory.createInterpretersForNote(mock1Setting, "user1", "sharedProcess", "user1");
    factory.createInterpretersForNote(mock1Setting, "user2", "sharedProcess", "user2");

    LazyOpenInterpreter interpreter1 = (LazyOpenInterpreter)interpreterGroup.get("user1").get(0);
    interpreter1.open();
    LazyOpenInterpreter interpreter2 = (LazyOpenInterpreter)interpreterGroup.get("user2").get(0);
    interpreter2.open();

    mock1Setting.closeAndRemoveInterpreterGroup("sharedProcess", "user1");
    assertFalse(interpreter1.isOpen());
    assertTrue(interpreter2.isOpen());
  }

  /**
   * 2 users' interpreters in isolated mode. Each user has one interpreterGroup. Restarting user1's interpreter
   * won't affect user2's interpreter
   * @throws Exception
   */
  @Test
  public void testRestartInterpreterInIsolatedMode() throws Exception {
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    Properties intp1Properties = new Properties();
    intp1Properties.put("PROPERTY_1", "VALUE_1");
    intp1Properties.put("property_2", "value_2");
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(true), intp1Properties);
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);
    List<InterpreterSetting> all = interpreterSettingManager.get();
    InterpreterSetting mock1Setting = null;
    for (InterpreterSetting setting : all) {
      if (setting.getName().equals("mock1")) {
        mock1Setting = setting;
        break;
      }
    }
    mock1Setting.getOption().setPerUser("isolated");
    mock1Setting.getOption().setPerNote("shared");
    // set remote as false so that we won't create new remote interpreter process
    mock1Setting.getOption().setRemote(false);
    mock1Setting.getOption().setHost("localhost");
    mock1Setting.getOption().setPort(2222);
    InterpreterGroup interpreterGroup1 = mock1Setting.getInterpreterGroup("user1", "note1");
    InterpreterGroup interpreterGroup2 = mock1Setting.getInterpreterGroup("user2", "note2");
    factory.createInterpretersForNote(mock1Setting, "user1", "note1", "shared_session");
    factory.createInterpretersForNote(mock1Setting, "user2", "note2", "shared_session");

    LazyOpenInterpreter interpreter1 = (LazyOpenInterpreter)interpreterGroup1.get("shared_session").get(0);
    interpreter1.open();
    LazyOpenInterpreter interpreter2 = (LazyOpenInterpreter)interpreterGroup2.get("shared_session").get(0);
    interpreter2.open();

    mock1Setting.closeAndRemoveInterpreterGroup("note1", "user1");
    assertFalse(interpreter1.isOpen());
    assertTrue(interpreter2.isOpen());
  }

  @Test
  public void testFactoryDefaultList() throws IOException, RepositoryException {
    // get default settings
    List<String> all = interpreterSettingManager.getDefaultInterpreterSettingList();
    assertTrue(interpreterSettingManager.get().size() >= all.size());
  }

  @Test
  public void testExceptions() throws InterpreterException, IOException, RepositoryException {
    List<String> all = interpreterSettingManager.getDefaultInterpreterSettingList();
    // add setting with null option & properties expected nullArgumentException.class
    try {
      interpreterSettingManager.add("mock2", new ArrayList<InterpreterInfo>(), new LinkedList<Dependency>(), new InterpreterOption(false), Collections.EMPTY_MAP, "", null);
    } catch(NullArgumentException e) {
      assertEquals("Test null option" , e.getMessage(),new NullArgumentException("option").getMessage());
    }
    try {
      interpreterSettingManager.add("mock2", new ArrayList<InterpreterInfo>(), new LinkedList<Dependency>(), new InterpreterOption(false), Collections.EMPTY_MAP, "", null);
    } catch (NullArgumentException e){
      assertEquals("Test null properties" , e.getMessage(),new NullArgumentException("properties").getMessage());
    }
  }


  @Test
  public void testSaveLoad() throws IOException, RepositoryException {
    // interpreter settings
    int numInterpreters = interpreterSettingManager.get().size();

    // check if file saved
    assertTrue(new File(conf.getInterpreterSettingPath()).exists());

    interpreterSettingManager.createNewSetting("new-mock1", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), new Properties());
    assertEquals(numInterpreters + 1, interpreterSettingManager.get().size());

    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));

    /*
     Current situation, if InterpreterSettinfRef doesn't have the key of InterpreterSetting, it would be ignored.
     Thus even though interpreter.json have several interpreterSetting in that file, it would be ignored and would not be initialized from loadFromFile.
     In this case, only "mock11" would be referenced from file under interpreter/mock, and "mock11" group would be initialized.
     */
    // TODO(jl): Decide how to handle the know referenced interpreterSetting.
    assertEquals(1, interpreterSettingManager.get().size());
  }

  @Test
  public void testInterpreterSettingPropertyClass() throws IOException, RepositoryException {
    // check if default interpreter reference's property type is map
    Map<String, InterpreterSetting> interpreterSettingRefs = interpreterSettingManager.getAvailableInterpreterSettings();
    InterpreterSetting intpSetting = interpreterSettingRefs.get("mock1");
    Map<String, InterpreterProperty> intpProperties =
        (Map<String, InterpreterProperty>) intpSetting.getProperties();
    assertTrue(intpProperties instanceof Map);

    // check if interpreter instance is saved as Properties in conf/interpreter.json file
    Properties properties = new Properties();
    properties.put("key1", "value1");
    properties.put("key2", "value2");

    interpreterSettingManager.createNewSetting("newMock", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), properties);

    String confFilePath = conf.getInterpreterSettingPath();
    byte[] encoded = Files.readAllBytes(Paths.get(confFilePath));
    String json = new String(encoded, "UTF-8");

    Gson gson = new Gson();
    InterpreterInfoSaving infoSaving = gson.fromJson(json, InterpreterInfoSaving.class);
    Map<String, InterpreterSetting> interpreterSettings = infoSaving.interpreterSettings;
    for (String key : interpreterSettings.keySet()) {
      InterpreterSetting setting = interpreterSettings.get(key);
      if (setting.getName().equals("newMock")) {
        assertEquals(setting.getProperties().toString(), properties.toString());
      }
    }
  }

  @Test
  public void testInterpreterAliases() throws IOException, RepositoryException {
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);
    final InterpreterInfo info1 = new InterpreterInfo("className1", "name1", true, null);
    final InterpreterInfo info2 = new InterpreterInfo("className2", "name1", true, null);
    interpreterSettingManager.add("group1", new ArrayList<InterpreterInfo>() {{
      add(info1);
    }}, new ArrayList<Dependency>(), new InterpreterOption(true), Collections.EMPTY_MAP, "/path1", null);
    interpreterSettingManager.add("group2", new ArrayList<InterpreterInfo>(){{
      add(info2);
    }}, new ArrayList<Dependency>(), new InterpreterOption(true), Collections.EMPTY_MAP, "/path2", null);

    final InterpreterSetting setting1 = interpreterSettingManager.createNewSetting("test-group1", "group1", new ArrayList<Dependency>(), new InterpreterOption(true), new Properties());
    final InterpreterSetting setting2 = interpreterSettingManager.createNewSetting("test-group2", "group1", new ArrayList<Dependency>(), new InterpreterOption(true), new Properties());

    interpreterSettingManager.setInterpreters("user", "note", new ArrayList<String>() {{
      add(setting1.getId());
      add(setting2.getId());
    }});

    assertEquals("className1", factory.getInterpreter("user1", "note", "test-group1").getClassName());
    assertEquals("className1", factory.getInterpreter("user1", "note", "group1").getClassName());
  }

  @Test
  public void testMultiUser() throws IOException, RepositoryException {
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    factory = new InterpreterFactory(conf, null, null, null, depResolver, true, interpreterSettingManager);
    final InterpreterInfo info1 = new InterpreterInfo("className1", "name1", true, null);
    interpreterSettingManager.add("group1", new ArrayList<InterpreterInfo>(){{
      add(info1);
    }}, new ArrayList<Dependency>(), new InterpreterOption(true), Collections.EMPTY_MAP, "/path1", null);

    InterpreterOption perUserInterpreterOption = new InterpreterOption(true, InterpreterOption.ISOLATED, InterpreterOption.SHARED);
    final InterpreterSetting setting1 = interpreterSettingManager.createNewSetting("test-group1", "group1", new ArrayList<Dependency>(), perUserInterpreterOption, new Properties());

    interpreterSettingManager.setInterpreters("user1", "note", new ArrayList<String>() {{
      add(setting1.getId());
    }});

    interpreterSettingManager.setInterpreters("user2", "note", new ArrayList<String>() {{
      add(setting1.getId());
    }});

    assertNotEquals(factory.getInterpreter("user1", "note", "test-group1"), factory.getInterpreter("user2", "note", "test-group1"));
  }


  @Test
  public void testInvalidInterpreterSettingName() {
    try {
      interpreterSettingManager.createNewSetting("new.mock1", "mock1", new LinkedList<Dependency>(), new InterpreterOption(false), new Properties());
      fail("expect fail because of invalid InterpreterSetting Name");
    } catch (IOException e) {
      assertEquals("'.' is invalid for InterpreterSetting name.", e.getMessage());
    }
  }


  @Test
  public void getEditorSetting() throws IOException, RepositoryException, SchedulerException {
    List<String> intpIds = new ArrayList<>();
    for(InterpreterSetting intpSetting: interpreterSettingManager.get()) {
      if (intpSetting.getName().startsWith("mock1")) {
        intpIds.add(intpSetting.getId());
      }
    }
    Note note = notebook.createNote(intpIds, new AuthenticationInfo("anonymous"));

    Interpreter interpreter = factory.getInterpreter("user1", note.getId(), "mock11");
    // get editor setting from interpreter-setting.json
    Map<String, Object> editor = interpreterSettingManager.getEditorSetting(interpreter, "user1", note.getId(), "mock11");
    assertEquals("java", editor.get("language"));

    // when interpreter is not loaded via interpreter-setting.json
    // or editor setting doesn't exit
    editor = interpreterSettingManager.getEditorSetting(factory.getInterpreter("user1", note.getId(), "mock1"),"user1", note.getId(), "mock1");
    assertEquals(null, editor.get("language"));

    // when interpreter is not bound to note
    editor = interpreterSettingManager.getEditorSetting(factory.getInterpreter("user1", note.getId(), "mock11"),"user1", note.getId(), "mock2");
    assertEquals("text", editor.get("language"));
  }

  @Test
  public void registerCustomInterpreterRunner() throws IOException {
    InterpreterSettingManager spyInterpreterSettingManager = spy(interpreterSettingManager);

    doNothing().when(spyInterpreterSettingManager).saveToFile();

    ArrayList<InterpreterInfo> interpreterInfos1 = new ArrayList<>();
    interpreterInfos1.add(new InterpreterInfo("name1.class", "name1", true, Maps.<String, Object>newHashMap()));

    spyInterpreterSettingManager.add("normalGroup1", interpreterInfos1, Lists.<Dependency>newArrayList(), new InterpreterOption(true), Maps.<String, InterpreterProperty>newHashMap(), "/normalGroup1", null);

    spyInterpreterSettingManager.createNewSetting("normalGroup1", "normalGroup1", Lists.<Dependency>newArrayList(), new InterpreterOption(true), new Properties());

    ArrayList<InterpreterInfo> interpreterInfos2 = new ArrayList<>();
    interpreterInfos2.add(new InterpreterInfo("name1.class", "name1", true, Maps.<String, Object>newHashMap()));

    InterpreterRunner mockInterpreterRunner = mock(InterpreterRunner.class);

    when(mockInterpreterRunner.getPath()).thenReturn("custom-linux-path.sh");

    spyInterpreterSettingManager.add("customGroup1", interpreterInfos2, Lists.<Dependency>newArrayList(), new InterpreterOption(true), Maps.<String, InterpreterProperty>newHashMap(), "/customGroup1", mockInterpreterRunner);

    spyInterpreterSettingManager.createNewSetting("customGroup1", "customGroup1", Lists.<Dependency>newArrayList(), new InterpreterOption(true), new Properties());

    spyInterpreterSettingManager.setInterpreters("anonymous", "noteCustome", spyInterpreterSettingManager.getDefaultInterpreterSettingList());

    factory.getInterpreter("anonymous", "noteCustome", "customGroup1");

    verify(mockInterpreterRunner, times(1)).getPath();
  }

  @Test
  public void interpreterRunnerTest() {
    InterpreterRunner mockInterpreterRunner = mock(InterpreterRunner.class);
    String testInterpreterRunner = "relativePath.sh";
    when(mockInterpreterRunner.getPath()).thenReturn(testInterpreterRunner); // This test only for Linux
    Interpreter i = factory.createRemoteRepl("path1", "sessionKey", "className", new Properties(), interpreterSettingManager.get().get(0).getId(), "userName", false, mockInterpreterRunner);
    String interpreterRunner = ((RemoteInterpreter) ((LazyOpenInterpreter) i).getInnerInterpreter()).getInterpreterRunner();
    assertNotEquals(interpreterRunner, testInterpreterRunner);

    testInterpreterRunner = "/AbsolutePath.sh";
    when(mockInterpreterRunner.getPath()).thenReturn(testInterpreterRunner);
    i = factory.createRemoteRepl("path1", "sessionKey", "className", new Properties(), interpreterSettingManager.get().get(0).getId(), "userName", false, mockInterpreterRunner);
    interpreterRunner = ((RemoteInterpreter) ((LazyOpenInterpreter) i).getInnerInterpreter()).getInterpreterRunner();
    assertEquals(interpreterRunner, testInterpreterRunner);
  }
}
