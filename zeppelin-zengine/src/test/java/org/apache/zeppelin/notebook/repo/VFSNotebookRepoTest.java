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

package org.apache.zeppelin.notebook.repo;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterInfo;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterProperty;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class VFSNotebookRepoTest implements JobListenerFactory {
  private static final Logger LOG = LoggerFactory.getLogger(VFSNotebookRepoTest.class);
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private InterpreterSettingManager interpreterSettingManager;
  private InterpreterFactory factory;
  private DependencyResolver depResolver;
  private NotebookAuthorization notebookAuthorization;

  private File mainZepDir;
  private File mainNotebookDir;

  @Before
  public void setUp() throws Exception {
    String zpath = System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis();
    mainZepDir = new File(zpath);
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    String mainNotePath = zpath + "/notebook";
    mainNotebookDir = new File(mainNotePath);
    mainNotebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.VFSNotebookRepo");
    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    this.schedulerFactory = new SchedulerFactory();
    depResolver = new DependencyResolver(mainZepDir.getAbsolutePath() + "/local-repo");
    interpreterSettingManager = new InterpreterSettingManager(conf, depResolver, new InterpreterOption(true));
    factory = new InterpreterFactory(conf, null, null, null, depResolver, false, interpreterSettingManager);

    ArrayList<InterpreterInfo> interpreterInfos = new ArrayList<>();
    interpreterInfos.add(new InterpreterInfo(MockInterpreter1.class.getName(), "mock1", true, new HashMap<String, Object>()));
    interpreterSettingManager.add("mock1", interpreterInfos, new ArrayList<Dependency>(), new InterpreterOption(),
        Maps.<String, InterpreterProperty>newHashMap(), "mock1", null);
    interpreterSettingManager.createNewSetting("mock1", "mock1", new ArrayList<Dependency>(), new InterpreterOption(), new Properties());

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo(conf);
    notebookAuthorization = NotebookAuthorization.init(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, interpreterSettingManager, this, search,
        notebookAuthorization, null);
  }

  @After
  public void tearDown() throws Exception {
    if (!FileUtils.deleteQuietly(mainZepDir)) {
      LOG.error("Failed to delete {} ", mainZepDir.getName());
    }
  }

  @Test
  public void testInvalidJsonFile() throws IOException {
    // given
    int numNotes = notebookRepo.list(null).size();

    // when create invalid json file
    File testNoteDir = new File(mainNotebookDir, "test");
    testNoteDir.mkdir();
    FileUtils.writeStringToFile(new File(testNoteDir, "note.json"), "");

    // then
    assertEquals(numNotes, notebookRepo.list(null).size());
  }

  @Test
  public void testSaveNotebook() throws IOException, InterruptedException {
    AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreters("user", note.getId(), interpreterSettingManager.getDefaultInterpreterSettingList());

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");
    p1.setAuthenticationInfo(anonymous);

    note.run(p1.getId());
    int timeout = 1;
    while (!p1.isTerminated()) {
      Thread.sleep(1000);
      if (timeout++ > 10) {
        break;
      }
    }
    int i = 0;
    int TEST_COUNT = 10;
    while (i++ < TEST_COUNT) {
      p1.setText("%mock1 hello zeppelin");
      new Thread(new NotebookWriter(note)).start();
      p1.setText("%mock1 hello world");
      new Thread(new NotebookWriter(note)).start();
    }

    note.setName("SaveTest");
    notebookRepo.save(note, null);
    assertEquals(note.getName(), "SaveTest");
    notebookRepo.remove(note.getId(), null);
  }
  
  @Test
  public void testUpdateSettings() throws IOException {
    AuthenticationInfo subject = new AuthenticationInfo("anonymous");
    File tmpDir = File.createTempFile("temp", Long.toString(System.nanoTime()));
    Map<String, String> settings = ImmutableMap.of("Notebook Path", tmpDir.getAbsolutePath());
    
    List<NotebookRepoSettingsInfo> repoSettings = notebookRepo.getSettings(subject);
    String originalDir = repoSettings.get(0).selected;
    
    notebookRepo.updateSettings(settings, subject);
    repoSettings = notebookRepo.getSettings(subject);
    assertEquals(repoSettings.get(0).selected, tmpDir.getAbsolutePath());
    
    // restaure
    notebookRepo.updateSettings(ImmutableMap.of("Notebook Path", originalDir), subject);
    FileUtils.deleteQuietly(tmpDir);
  }

  class NotebookWriter implements Runnable {
    Note note;
    public NotebookWriter(Note note) {
      this.note = note;
    }

    @Override
    public void run() {
      try {
        notebookRepo.save(note, null);
      } catch (IOException e) {
        LOG.error(e.toString(), e);
      }
    }
  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return null;
  }
}
