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

package org.apache.zeppelin.notebook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;

public class NotebookTest implements JobListenerFactory{
  private static final Logger logger = LoggerFactory.getLogger(NotebookTest.class);

  private File tmpDir;
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private File notebookDir;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private InterpreterFactory factory;
  private DependencyResolver depResolver;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    new File(tmpDir, "conf").mkdirs();
    notebookDir = new File(tmpDir + "/notebook");
    notebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), tmpDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "org.apache.zeppelin.interpreter.mock.MockInterpreter1,org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    depResolver = new DependencyResolver(tmpDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, depResolver);

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo(conf);
    NotebookAuthorization notebookAuthorization = new NotebookAuthorization(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, this, search, notebookAuthorization);
  }

  @After
  public void tearDown() throws Exception {
    delete(tmpDir);
  }

  @Test
  public void testSelectingReplImplementation() throws IOException {
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    // run with defatul repl
    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    note.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
    assertEquals("repl1: hello world", p1.getResult().message());

    // run with specific repl
    Paragraph p2 = note.addParagraph();
    p2.setConfig(config);
    p2.setText("%mock2 hello world");
    note.run(p2.getId());
    while(p2.isTerminated()==false || p2.getResult()==null) Thread.yield();
    assertEquals("repl2: hello world", p2.getResult().message());
  }

  @Test
  public void testReloadAllNotes() throws IOException {
    File srcDir = new File("src/test/resources/2A94M5J1Z");
    File destDir = new File(notebookDir.getAbsolutePath() + "/2A94M5J1Z");

    // copy the notebook
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch (IOException e) {
      logger.error(e.toString(), e);
    }

    // doesn't have copied notebook in memory before reloading
    List<Note> notes = notebook.getAllNotes();
    assertEquals(notes.size(), 0);

    // load copied notebook on memory when reloadAllNotes() is called
    Note copiedNote = notebookRepo.get("2A94M5J1Z");
    notebook.reloadAllNotes();
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 1);
    assertEquals(notes.get(0).id(), copiedNote.id());
    assertEquals(notes.get(0).getName(), copiedNote.getName());
    assertEquals(notes.get(0).getParagraphs(), copiedNote.getParagraphs());

    // delete the notebook
    FileUtils.deleteDirectory(destDir);

    // keep notebook in memory before reloading
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 1);

    // delete notebook from notebook list when reloadAllNotes() is called
    notebook.reloadAllNotes();
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 0);
  }

  @Test
  public void testPersist() throws IOException, SchedulerException, RepositoryException {
    Note note = notebook.createNote();

    // run with default repl
    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    note.persist();

    Notebook notebook2 = new Notebook(
        conf, notebookRepo, schedulerFactory,
        new InterpreterFactory(conf, null, null, null, depResolver), this, null, null);
    assertEquals(1, notebook2.getAllNotes().size());
  }

  @Test
  public void testClearParagraphOutput() throws IOException, SchedulerException{
    Note note = notebook.createNote();
    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    note.run(p1.getId());

    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
    assertEquals("repl1: hello world", p1.getResult().message());

    // clear paragraph output/result
    note.clearParagraphOutput(p1.getId());
    assertNull(p1.getResult());
  }

  @Test
  public void testRunAll() throws IOException {
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    // p1
    Paragraph p1 = note.addParagraph();
    Map config1 = p1.getConfig();
    config1.put("enabled", true);
    p1.setConfig(config1);
    p1.setText("p1");

    // p2
    Paragraph p2 = note.addParagraph();
    Map config2 = p2.getConfig();
    config2.put("enabled", false);
    p2.setConfig(config2);
    p2.setText("p2");

    // p3
    Paragraph p3 = note.addParagraph();
    p3.setText("p3");

    // when
    note.runAll();

    // wait for finish
    while(p3.isTerminated()==false) {
      Thread.yield();
    }

    assertEquals("repl1: p1", p1.getResult().message());
    assertNull(p2.getResult());
    assertEquals("repl1: p3", p3.getResult().message());

    notebook.removeNote(note.getId());
  }

  @Test
  public void testSchedule() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    Paragraph p = note.addParagraph();
    Map config = new HashMap<String, Object>();
    p.setConfig(config);
    p.setText("p1");
    Date dateFinished = p.getDateFinished();
    assertNull(dateFinished);

    // set cron scheduler, once a second
    config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);
    notebook.refreshCron(note.id());
    Thread.sleep(1*1000);

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    notebook.refreshCron(note.id());
    Thread.sleep(1000);
    dateFinished = p.getDateFinished();
    assertNotNull(dateFinished);
    Thread.sleep(1*1000);
    assertEquals(dateFinished, p.getDateFinished());
  }

  @Test
  public void testAutoRestartInterpreterAfterSchedule() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());
    
    Paragraph p = note.addParagraph();
    Map config = new HashMap<String, Object>();
    p.setConfig(config);
    p.setText("p1");

    // set cron scheduler, once a second
    config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    config.put("releaseresource", "true");
    note.setConfig(config);
    notebook.refreshCron(note.id());
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    Date dateFinished = p.getDateFinished();
    assertNotNull(dateFinished);

    // restart interpreter
    for (InterpreterSetting setting : note.getNoteReplLoader().getInterpreterSettings()) {
      notebook.getInterpreterFactory().restart(setting.id());
    }

    Thread.sleep(1000);
    while (p.getStatus() != Status.FINISHED) {
      Thread.sleep(100);
    }
    assertNotEquals(dateFinished, p.getDateFinished());
    
    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    notebook.refreshCron(note.id());
  }

  @Test
  public void testCloneNote() throws IOException, CloneNotSupportedException,
      InterruptedException {
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    final Paragraph p = note.addParagraph();
    p.setText("hello world");
    note.runAll();
    while(p.isTerminated()==false || p.getResult()==null) Thread.yield();

    p.setStatus(Status.RUNNING);
    Note cloneNote = notebook.cloneNote(note.getId(), "clone note");
    Paragraph cp = cloneNote.paragraphs.get(0);
    assertEquals(cp.getStatus(), Status.READY);

    // Keep same ParagraphID
    assertEquals(cp.getId(), p.getId());
    assertEquals(cp.text, p.text);
    assertEquals(cp.getResult().message(), p.getResult().message());
  }

  @Test
  public void testResourceRemovealOnParagraphNoteRemove() throws IOException {
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());
    for (InterpreterGroup intpGroup : InterpreterGroup.getAll()) {
      intpGroup.setResourcePool(new LocalResourcePool(intpGroup.getId()));
    }
    Paragraph p1 = note.addParagraph();
    p1.setText("hello");
    Paragraph p2 = note.addParagraph();
    p2.setText("%mock2 world");

    note.runAll();
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
    while(p2.isTerminated()==false || p2.getResult()==null) Thread.yield();

    assertEquals(2, ResourcePoolUtils.getAllResources().size());

    // remove a paragraph
    note.removeParagraph(p1.getId());
    assertEquals(1, ResourcePoolUtils.getAllResources().size());

    // remove note
    notebook.removeNote(note.id());
    assertEquals(0, ResourcePoolUtils.getAllResources().size());
  }

  @Test
  public void testAngularObjectRemovalOnNotebookRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    AngularObjectRegistry registry = note.getNoteReplLoader()
        .getInterpreterSettings().get(0).getInterpreterGroup("sharedProcess")
        .getAngularObjectRegistry();

    Paragraph p1 = note.addParagraph();

    // add paragraph scope object
    registry.add("o1", "object1", note.id(), p1.getId());

    // add notebook scope object
    registry.add("o2", "object2", note.id(), null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    notebook.removeNote(note.id());

    // notebook scope or paragraph scope object should be removed
    assertNull(registry.get("o1", note.id(), null));
    assertNull(registry.get("o2", note.id(), p1.getId()));

    // global object sould be remained
    assertNotNull(registry.get("o3", null, null));
  }

  @Test
  public void testAngularObjectRemovalOnParagraphRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    AngularObjectRegistry registry = note.getNoteReplLoader()
        .getInterpreterSettings().get(0).getInterpreterGroup("sharedProcess")
        .getAngularObjectRegistry();

    Paragraph p1 = note.addParagraph();

    // add paragraph scope object
    registry.add("o1", "object1", note.id(), p1.getId());

    // add notebook scope object
    registry.add("o2", "object2", note.id(), null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    note.removeParagraph(p1.getId());

    // paragraph scope should be removed
    assertNull(registry.get("o1", note.id(), null));

    // notebook scope and global object sould be remained
    assertNotNull(registry.get("o2", note.id(), null));
    assertNotNull(registry.get("o3", null, null));
  }

  @Test
  public void testAngularObjectRemovalOnInterpreterRestart() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    AngularObjectRegistry registry = note.getNoteReplLoader()
        .getInterpreterSettings().get(0).getInterpreterGroup("sharedProcess")
        .getAngularObjectRegistry();

    // add local scope object
    registry.add("o1", "object1", note.id(), null);
    // add global scope object
    registry.add("o2", "object2", null, null);

    // restart interpreter
    factory.restart(note.getNoteReplLoader().getInterpreterSettings().get(0).id());
    registry = note.getNoteReplLoader()
    .getInterpreterSettings().get(0).getInterpreterGroup("sharedProcess")
    .getAngularObjectRegistry();

    // local and global scope object should be removed
    assertNull(registry.get("o1", note.id(), null));
    assertNull(registry.get("o2", null, null));
    notebook.removeNote(note.id());
  }

  @Test
  public void testPermissions() throws IOException {
    // create a note and a paragraph
    Note note = notebook.createNote();
    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    // empty owners, readers and writers means note is public
    assertEquals(notebookAuthorization.isOwner(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isReader(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isWriter(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), true);

    notebookAuthorization.setOwners(note.id(),
            new HashSet<String>(Arrays.asList("user1")));
    notebookAuthorization.setReaders(note.id(),
            new HashSet<String>(Arrays.asList("user1", "user2")));
    notebookAuthorization.setWriters(note.id(),
            new HashSet<String>(Arrays.asList("user1")));

    assertEquals(notebookAuthorization.isOwner(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), false);
    assertEquals(notebookAuthorization.isOwner(note.id(),
            new HashSet<String>(Arrays.asList("user1"))), true);

    assertEquals(notebookAuthorization.isReader(note.id(),
            new HashSet<String>(Arrays.asList("user3"))), false);
    assertEquals(notebookAuthorization.isReader(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), true);

    assertEquals(notebookAuthorization.isWriter(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), false);
    assertEquals(notebookAuthorization.isWriter(note.id(),
            new HashSet<String>(Arrays.asList("user1"))), true);

    // Test clearing of permssions
    notebookAuthorization.setReaders(note.id(), Sets.<String>newHashSet());
    assertEquals(notebookAuthorization.isReader(note.id(),
            new HashSet<String>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isReader(note.id(),
            new HashSet<String>(Arrays.asList("user3"))), true);

    notebook.removeNote(note.id());
  }

  @Test
  public void testAbortParagraphStatusOnInterpreterRestart() throws InterruptedException,
      IOException {
    Note note = notebook.createNote();
    note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

    ArrayList<Paragraph> paragraphs = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      Paragraph tmp = note.addParagraph();
      tmp.setText("p" + tmp.getId());
      paragraphs.add(tmp);
    }

    for (Paragraph p : paragraphs) {
      assertEquals(Job.Status.READY, p.getStatus());
    }

    note.runAll();

    while (paragraphs.get(0).getStatus() != Status.FINISHED) Thread.yield();

    factory.restart(note.getNoteReplLoader().getInterpreterSettings().get(0).id());

    boolean isAborted = false;
    for (Paragraph p : paragraphs) {
      logger.debug(p.getStatus().name());
      if (isAborted) {
        assertEquals(Job.Status.ABORT, p.getStatus());
      }
      if (p.getStatus() == Status.ABORT) {
        isAborted = true;
      }
    }

    assertTrue(isAborted);
  }

  @Test
  public void testPerSessionInterpreterCloseOnNoteRemoval() throws IOException {
    // create a notes
    Note note1  = notebook.createNote();
    Paragraph p1 = note1.addParagraph();
    p1.setText("getId");

    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : note1.getNoteReplLoader().getInterpreterSettings()) {
      setting.getOption().setPerNoteSession(true);
      notebook.getInterpreterFactory().restart(setting.id());
    }

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    InterpreterResult result = p1.getResult();

    // remove note and recreate
    notebook.removeNote(note1.getId());
    note1 = notebook.createNote();
    p1 = note1.addParagraph();
    p1.setText("getId");

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    assertNotEquals(p1.getResult().message(), result.message());

    notebook.removeNote(note1.getId());
  }

  @Test
  public void testPerSessionInterpreter() throws IOException {
    // create two notes
    Note note1  = notebook.createNote();
    Paragraph p1 = note1.addParagraph();

    Note note2  = notebook.createNote();
    Paragraph p2 = note2.addParagraph();

    p1.setText("getId");
    p2.setText("getId");

    // run per note session disabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertEquals(p1.getResult().message(), p2.getResult().message());


    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : note1.getNoteReplLoader().getInterpreterSettings()) {
      setting.getOption().setPerNoteSession(true);
      notebook.getInterpreterFactory().restart(setting.id());
    }

    // run per note session enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getResult().message(), p2.getResult().message());

    notebook.removeNote(note1.getId());
    notebook.removeNote(note2.getId());
  }

  @Test
  public void testPerSessionInterpreterCloseOnUnbindInterpreterSetting() throws IOException {
    // create a notes
    Note note1  = notebook.createNote();
    Paragraph p1 = note1.addParagraph();
    p1.setText("getId");

    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : note1.getNoteReplLoader().getInterpreterSettings()) {
      setting.getOption().setPerNoteSession(true);
      notebook.getInterpreterFactory().restart(setting.id());
    }

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    InterpreterResult result = p1.getResult();


    // unbind, and rebind setting. that result interpreter instance close
    List<String> bindedSettings = notebook.getBindedInterpreterSettingsIds(note1.getId());
    notebook.bindInterpretersToNote(note1.getId(), new LinkedList<String>());
    notebook.bindInterpretersToNote(note1.getId(), bindedSettings);

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(result.message(), p1.getResult().message());

    notebook.removeNote(note1.getId());
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
