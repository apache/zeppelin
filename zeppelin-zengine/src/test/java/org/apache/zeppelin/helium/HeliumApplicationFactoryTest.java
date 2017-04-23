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
package org.apache.zeppelin.helium;

import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class HeliumApplicationFactoryTest implements JobListenerFactory {
  private File tmpDir;
  private File notebookDir;
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private DependencyResolver depResolver;
  private InterpreterFactory factory;
  private InterpreterSettingManager interpreterSettingManager;
  private VFSNotebookRepo notebookRepo;
  private Notebook notebook;
  private HeliumApplicationFactory heliumAppFactory;
  private AuthenticationInfo anonymous;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZepelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    File confDir = new File(tmpDir, "conf");
    confDir.mkdirs();
    notebookDir = new File(tmpDir + "/notebook");
    notebookDir.mkdirs();

    File home = new File(getClass().getClassLoader().getResource("note").getFile()) // zeppelin/zeppelin-zengine/target/test-classes/note
        .getParentFile()               // zeppelin/zeppelin-zengine/target/test-classes
        .getParentFile()               // zeppelin/zeppelin-zengine/target
        .getParentFile()               // zeppelin/zeppelin-zengine
        .getParentFile();              // zeppelin

    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(), home.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(), tmpDir.getAbsolutePath() + "/conf");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());

    conf = new ZeppelinConfiguration();

    this.schedulerFactory = new SchedulerFactory();

    heliumAppFactory = new HeliumApplicationFactory();
    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    factory = new InterpreterFactory(conf, null, null, heliumAppFactory, depResolver, false, interpreterSettingManager);
    HashMap<String, String> env = new HashMap<>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());
    factory.setEnv(env);

    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(true), new Properties());

    ArrayList<InterpreterInfo> interpreterInfos2 = new ArrayList<>();
    interpreterInfos2.add(new InterpreterInfo(MockInterpreter2.class.getName(), "mock2", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock2", interpreterInfos2, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock2", null);
    interpreterSettingManager.createNewSetting("mock2", "mock2", new ArrayList<Dependency>(), new InterpreterOption(), new Properties());

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo(conf);
    NotebookAuthorization notebookAuthorization = NotebookAuthorization.init(conf);
    notebook = new Notebook(
        conf,
        notebookRepo,
        schedulerFactory,
        factory,
        interpreterSettingManager,
        this,
        search,
        notebookAuthorization,
        new Credentials(false, null));

    heliumAppFactory.setNotebook(notebook);

    notebook.addNotebookEventListener(heliumAppFactory);

    anonymous = new AuthenticationInfo("anonymous");
  }

  @After
  public void tearDown() throws Exception {
    List<InterpreterSetting> settings = interpreterSettingManager.get();
    for (InterpreterSetting setting : settings) {
      for (InterpreterGroup intpGroup : setting.getAllInterpreterGroups()) {
        intpGroup.close();
      }
    }

    FileUtils.deleteDirectory(tmpDir);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
        ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getStringValue());
  }


  @Test
  public void testLoadRunUnloadApplication()
      throws IOException, ApplicationException, InterruptedException {
    // given
    HeliumPackage pkg1 = new HeliumPackage(HeliumType.APPLICATION,
        "name1",
        "desc1",
        "",
        HeliumTestApplication.class.getName(),
        new String[][]{},
        "", "");

    Note note1 = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreters("user", note1.getId(),interpreterSettingManager.getDefaultInterpreterSettingList());

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // make sure interpreter process running
    p1.setText("%mock1 job");
    p1.setAuthenticationInfo(anonymous);
    note1.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();

    assertEquals("repl1: job", p1.getResult().message().get(0).getData());

    // when
    assertEquals(0, p1.getAllApplicationStates().size());
    String appId = heliumAppFactory.loadAndRun(pkg1, p1);
    assertEquals(1, p1.getAllApplicationStates().size());
    ApplicationState app = p1.getApplicationState(appId);
    Thread.sleep(500); // wait for enough time

    // then
    assertEquals("Hello world 1", app.getOutput());

    // when
    heliumAppFactory.run(p1, appId);
    Thread.sleep(500); // wait for enough time

    // then
    assertEquals("Hello world 2", app.getOutput());

    // clean
    heliumAppFactory.unload(p1, appId);
    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testUnloadOnParagraphRemove() throws IOException {
    // given
    HeliumPackage pkg1 = new HeliumPackage(HeliumType.APPLICATION,
        "name1",
        "desc1",
        "",
        HeliumTestApplication.class.getName(),
        new String[][]{},
        "", "");

    Note note1 = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreters("user", note1.getId(), interpreterSettingManager.getDefaultInterpreterSettingList());

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // make sure interpreter process running
    p1.setText("%mock1 job");
    p1.setAuthenticationInfo(anonymous);
    note1.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();

    assertEquals(0, p1.getAllApplicationStates().size());
    String appId = heliumAppFactory.loadAndRun(pkg1, p1);
    ApplicationState app = p1.getApplicationState(appId);
    while (app.getStatus() != ApplicationState.Status.LOADED) {
      Thread.yield();
    }

    // when remove paragraph
    note1.removeParagraph("user", p1.getId());

    // then
    assertEquals(ApplicationState.Status.UNLOADED, app.getStatus());

    // clean
    notebook.removeNote(note1.getId(), anonymous);
  }


  @Test
  public void testUnloadOnInterpreterUnbind() throws IOException {
    // given
    HeliumPackage pkg1 = new HeliumPackage(HeliumType.APPLICATION,
        "name1",
        "desc1",
        "",
        HeliumTestApplication.class.getName(),
        new String[][]{},
        "", "");

    Note note1 = notebook.createNote(anonymous);
    notebook.bindInterpretersToNote("user", note1.getId(), interpreterSettingManager.getDefaultInterpreterSettingList());

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // make sure interpreter process running
    p1.setText("%mock1 job");
    p1.setAuthenticationInfo(anonymous);
    note1.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();

    assertEquals(0, p1.getAllApplicationStates().size());
    String appId = heliumAppFactory.loadAndRun(pkg1, p1);
    ApplicationState app = p1.getApplicationState(appId);
    while (app.getStatus() != ApplicationState.Status.LOADED) {
      Thread.yield();
    }

    // when unbind interpreter
    notebook.bindInterpretersToNote("user", note1.getId(), new LinkedList<String>());

    // then
    assertEquals(ApplicationState.Status.UNLOADED, app.getStatus());

    // clean
    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testInterpreterUnbindOfNullReplParagraph() throws IOException {
    // create note
    Note note1 = notebook.createNote(anonymous);

    // add paragraph with invalid magic
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%fake ");

    // make sure that p1's repl is null
    Interpreter intp = p1.getCurrentRepl();
    assertEquals(intp, null);

    // Unbind all interpreter from note
    // NullPointerException shouldn't occur here
    notebook.bindInterpretersToNote("user", note1.getId(), new LinkedList<String>());

    // remove note
    notebook.removeNote(note1.getId(), anonymous);
  }


  @Test
  public void testUnloadOnInterpreterRestart() throws IOException {
    // given
    HeliumPackage pkg1 = new HeliumPackage(HeliumType.APPLICATION,
        "name1",
        "desc1",
        "",
        HeliumTestApplication.class.getName(),
        new String[][]{},
        "", "");

    Note note1 = notebook.createNote(anonymous);
    notebook.bindInterpretersToNote("user", note1.getId(), interpreterSettingManager.getDefaultInterpreterSettingList());
    String mock1IntpSettingId = null;
    for (InterpreterSetting setting : notebook.getBindedInterpreterSettings(note1.getId())) {
      if (setting.getName().equals("mock1")) {
        mock1IntpSettingId = setting.getId();
        break;
      }
    }

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // make sure interpreter process running
    p1.setText("%mock1 job");
    p1.setAuthenticationInfo(anonymous);
    note1.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
    assertEquals(0, p1.getAllApplicationStates().size());
    String appId = heliumAppFactory.loadAndRun(pkg1, p1);
    ApplicationState app = p1.getApplicationState(appId);
    while (app.getStatus() != ApplicationState.Status.LOADED) {
      Thread.yield();
    }
    // wait until application is executed
    while (!"Hello world 1".equals(app.getOutput())) {
      Thread.yield();
    }
    // when restart interpreter
    interpreterSettingManager.restart(mock1IntpSettingId);
    while (app.getStatus() == ApplicationState.Status.LOADED) {
      Thread.yield();
    }
    // then
    assertEquals(ApplicationState.Status.UNLOADED, app.getStatus());

    // clean
    notebook.removeNote(note1.getId(), anonymous);
  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return new ParagraphJobListener() {
      @Override
      public void onOutputAppend(Paragraph paragraph, int idx, String output) {

      }

      @Override
      public void onOutputUpdate(Paragraph paragraph, int idx, InterpreterResultMessage msg) {

      }

      @Override
      public void onOutputUpdateAll(Paragraph paragraph, List<InterpreterResultMessage> msgs) {

      }

      @Override
      public void onProgressUpdate(Job job, int progress) {

      }

      @Override
      public void beforeStatusChange(Job job, Job.Status before, Job.Status after) {

      }

      @Override
      public void afterStatusChange(Job job, Job.Status before, Job.Status after) {

      }
    };
  }
}
