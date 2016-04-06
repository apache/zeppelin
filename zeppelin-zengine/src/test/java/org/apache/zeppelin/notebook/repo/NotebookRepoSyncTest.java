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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.search.LuceneSearch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NotebookRepoSyncTest implements JobListenerFactory {

  private File mainZepDir;
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private File mainNotebookDir;
  private File secNotebookDir;
  private Notebook notebookSync;
  private NotebookRepoSync notebookRepoSync;
  private InterpreterFactory factory;
  private DependencyResolver depResolver;
  private SearchService search;
  private NotebookAuthorization notebookAuthorization;
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSyncTest.class);
  
  @Before
  public void setUp() throws Exception {
    String zpath = System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis();
    mainZepDir = new File(zpath);
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    String mainNotePath = zpath+"/notebook";
    String secNotePath = mainNotePath + "_secondary";
    mainNotebookDir = new File(mainNotePath);
    secNotebookDir = new File(secNotePath);
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), mainZepDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.VFSNotebookRepo,org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock");
    LOG.info("main Note dir : " + mainNotePath);
    LOG.info("secondary note dir : " + secNotePath);
    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    depResolver = new DependencyResolver(mainZepDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, depResolver);
    
    search = mock(SearchService.class);
    notebookRepoSync = new NotebookRepoSync(conf);
    notebookAuthorization = new NotebookAuthorization(conf);
    notebookSync = new Notebook(conf, notebookRepoSync, schedulerFactory, factory, this, search, notebookAuthorization);
  }

  @After
  public void tearDown() throws Exception {
    delete(mainZepDir);
  }
  
  @Test
  public void testRepoCount() throws IOException {
    assertTrue(notebookRepoSync.getMaxRepoNum() >= notebookRepoSync.getRepoCount());
  }
  
  @Test
  public void testSyncOnCreate() throws IOException {
    /* check that both storage systems are empty */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0).size());
    assertEquals(0, notebookRepoSync.list(1).size());
    
    /* create note */
    Note note = notebookSync.createNote();

    // check that automatically saved on both storages
    assertEquals(1, notebookRepoSync.list(0).size());
    assertEquals(1, notebookRepoSync.list(1).size());
    assertEquals(notebookRepoSync.list(0).get(0).getId(),notebookRepoSync.list(1).get(0).getId());
    
  }

  @Test
  public void testSyncOnDelete() throws IOException {
    /* create note */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0).size());
    assertEquals(0, notebookRepoSync.list(1).size());
    
    Note note = notebookSync.createNote();

    /* check that created in both storage systems */
    assertEquals(1, notebookRepoSync.list(0).size());
    assertEquals(1, notebookRepoSync.list(1).size());
    assertEquals(notebookRepoSync.list(0).get(0).getId(),notebookRepoSync.list(1).get(0).getId());
    
    /* remove Note */
    notebookSync.removeNote(notebookRepoSync.list(0).get(0).getId());
    
    /* check that deleted in both storages */
    assertEquals(0, notebookRepoSync.list(0).size());
    assertEquals(0, notebookRepoSync.list(1).size());
    
  }
  
  @Test
  public void testSyncUpdateMain() throws IOException {
    
    /* create note */
    Note note = notebookSync.createNote();
    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    
    /* new paragraph exists in note instance */
    assertEquals(1, note.getParagraphs().size());
    
    /* new paragraph not yet saved into storages */
    assertEquals(0, notebookRepoSync.get(0,
        notebookRepoSync.list(0).get(0).getId()).getParagraphs().size());
    assertEquals(0, notebookRepoSync.get(1,
        notebookRepoSync.list(1).get(0).getId()).getParagraphs().size());
    
    /* save to storage under index 0 (first storage) */ 
    notebookRepoSync.save(0, note);
    
    /* check paragraph saved to first storage */
    assertEquals(1, notebookRepoSync.get(0,
        notebookRepoSync.list(0).get(0).getId()).getParagraphs().size());
    /* check paragraph isn't saved to second storage */
    assertEquals(0, notebookRepoSync.get(1,
        notebookRepoSync.list(1).get(0).getId()).getParagraphs().size());
    /* apply sync */
    notebookRepoSync.sync();
    /* check whether added to second storage */
    assertEquals(1, notebookRepoSync.get(1,
    notebookRepoSync.list(1).get(0).getId()).getParagraphs().size());
    /* check whether same paragraph id */
    assertEquals(p1.getId(), notebookRepoSync.get(0,
        notebookRepoSync.list(0).get(0).getId()).getLastParagraph().getId());
    assertEquals(p1.getId(), notebookRepoSync.get(1,
        notebookRepoSync.list(1).get(0).getId()).getLastParagraph().getId());
  }

  @Test
  public void testSyncOnReloadedList() throws IOException {
    /* check that both storage repos are empty */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0).size());
    assertEquals(0, notebookRepoSync.list(1).size());

    File srcDir = new File("src/test/resources/2A94M5J1Z");
    File destDir = new File(secNotebookDir + "/2A94M5J1Z");

    /* copy manually new notebook into secondary storage repo and check repos */
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch (IOException e) {
      LOG.error(e.toString(), e);
    }
    assertEquals(0, notebookRepoSync.list(0).size());
    assertEquals(1, notebookRepoSync.list(1).size());

    // After reloading notebooks repos should be synchronized
    notebookSync.reloadAllNotes();
    assertEquals(1, notebookRepoSync.list(0).size());
    assertEquals(1, notebookRepoSync.list(1).size());
  }

  @Test
  public void testCheckpointOneStorage() throws IOException, SchedulerException {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.GitNotebookRepo");
    ZeppelinConfiguration vConf = ZeppelinConfiguration.create();

    NotebookRepoSync vRepoSync = new NotebookRepoSync(vConf);
    Notebook vNotebookSync = new Notebook(vConf, vRepoSync, schedulerFactory, factory, this, search, null);

    // one git versioned storage initialized
    assertThat(vRepoSync.getRepoCount()).isEqualTo(1);
    assertThat(vRepoSync.getRepo(0)).isInstanceOf(GitNotebookRepo.class);
    
    GitNotebookRepo gitRepo = (GitNotebookRepo) vRepoSync.getRepo(0);
    
    // no notes
    assertThat(vRepoSync.list().size()).isEqualTo(0);
    // create note
    Note note = vNotebookSync.createNote();
    assertThat(vRepoSync.list().size()).isEqualTo(1);
    
    String noteId = vRepoSync.list().get(0).getId();
    // first checkpoint
    vRepoSync.checkpoint(noteId, "checkpoint message");
    int vCount = gitRepo.history(noteId).size();
    assertThat(vCount).isEqualTo(1);
    
    Paragraph p = note.addParagraph();
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md checkpoint test");
    
    // save and checkpoint again
    vRepoSync.save(note);
    vRepoSync.checkpoint(noteId, "checkpoint message 2");
    assertThat(gitRepo.history(noteId).size()).isEqualTo(vCount + 1);
  }
  
  static void delete(File file){
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

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return new ParagraphJobListener(){

      @Override
      public void onOutputAppend(Paragraph paragraph, InterpreterOutput out, String output) {
      }

      @Override
      public void onOutputUpdate(Paragraph paragraph, InterpreterOutput out, String output) {
      }

      @Override
      public void onProgressUpdate(Job job, int progress) {
      }

      @Override
      public void beforeStatusChange(Job job, Status before, Status after) {
      }

      @Override
      public void afterStatusChange(Job job, Status before, Status after) {
      }
    };
  }
	
}
