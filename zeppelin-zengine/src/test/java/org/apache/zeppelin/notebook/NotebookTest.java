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

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSettingsInfo;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.RepositoryException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;


public class NotebookTest extends AbstractInterpreterTest implements ParagraphJobListener {
  private static final Logger logger = LoggerFactory.getLogger(NotebookTest.class);

  private Notebook notebook;
  private NoteManager noteManager;
  private NotebookRepo notebookRepo;
  private AuthorizationService authorizationService;
  private Credentials credentials;
  private AuthenticationInfo anonymous = AuthenticationInfo.ANONYMOUS;
  private StatusChangedListener afterStatusChangedListener;
  private QuartzSchedulerService schedulerService;

  @Override
  @Before
  public void setUp() throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "true");
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
    super.setUp();

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo();
    notebookRepo.init(conf);
    noteManager = new NoteManager(notebookRepo);
    authorizationService = new AuthorizationService(noteManager, conf);

    credentials = new Credentials(conf);
    notebook = new Notebook(conf, authorizationService, notebookRepo, noteManager, interpreterFactory, interpreterSettingManager, search,
            credentials, null);
    notebook.setParagraphJobListener(this);
    schedulerService = new QuartzSchedulerService(conf, notebook);
    schedulerService.waitForFinishInit();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName());
    System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName());
  }

  @Test
  public void testRevisionSupported() throws IOException {
    NotebookRepo notebookRepo;
    Notebook notebook;

    notebookRepo = new DummyNotebookRepo();
    notebook = new Notebook(conf, mock(AuthorizationService.class), notebookRepo, new NoteManager(notebookRepo), interpreterFactory,
        interpreterSettingManager, null,
        credentials, null);
    assertFalse("Revision is not supported in DummyNotebookRepo", notebook.isRevisionSupported());

    notebookRepo = new DummyNotebookRepoWithVersionControl();
    notebook = new Notebook(conf, mock(AuthorizationService.class), notebookRepo, new NoteManager(notebookRepo), interpreterFactory,
        interpreterSettingManager, null,
        credentials, null);
    assertTrue("Revision is supported in DummyNotebookRepoWithVersionControl",
        notebook.isRevisionSupported());
  }

  public static class DummyNotebookRepo implements NotebookRepo {

    @Override
    public void init(ZeppelinConfiguration zConf) throws IOException {

    }

    @Override
    public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
      return new HashMap<>();
    }

    @Override
    public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
      return null;
    }

    @Override
    public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) {

    }

    @Override
    public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {

    }

    @Override
    public void save(Note note, AuthenticationInfo subject) throws IOException {

    }

    @Override
    public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {

    }

    @Override
    public void remove(String folderPath, AuthenticationInfo subject) {

    }

    @Override
    public void close() {

    }

    @Override
    public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
      return Collections.emptyList();
    }

    @Override
    public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {

    }
  }

  public static class DummyNotebookRepoWithVersionControl implements
      NotebookRepoWithVersionControl {

    @Override
    public Revision checkpoint(String noteId, String notePath, String checkpointMsg, AuthenticationInfo subject)
        throws IOException {
      return null;
    }

    @Override
    public Note get(String noteId, String notePath, String revId, AuthenticationInfo subject) throws IOException {
      return null;
    }

    @Override
    public List<Revision> revisionHistory(String noteId, String notePath, AuthenticationInfo subject) {
      return null;
    }

    @Override
    public Note setNoteRevision(String noteId, String notePath, String revId, AuthenticationInfo subject) throws
        IOException {
      return null;
    }

    @Override
    public void init(ZeppelinConfiguration zConf) throws IOException {

    }

    @Override
    public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
      return new HashMap<>();
    }

    @Override
    public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
      return null;
    }

    @Override
    public void save(Note note, AuthenticationInfo subject) throws IOException {

    }

    @Override
    public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) {

    }

    @Override
    public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {

    }

    @Override
    public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {

    }

    @Override
    public void remove(String folderPath, AuthenticationInfo subject) {

    }

    @Override
    public void close() {

    }

    @Override
    public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
      return Collections.emptyList();
    }

    @Override
    public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {

    }
  }

  @Test
  public void testSelectingReplImplementation() throws IOException {
    Note note = notebook.createNote("note1", anonymous);
    // run with default repl
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());
    while (p1.isTerminated() == false || p1.getReturn() == null) Thread.yield();
    assertEquals("repl1: hello world", p1.getReturn().message().get(0).getData());

    // run with specific repl
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setConfig(config);
    p2.setText("%mock2 hello world");
    p2.setAuthenticationInfo(anonymous);
    note.run(p2.getId());
    while (p2.isTerminated() == false || p2.getReturn() == null) Thread.yield();
    assertEquals("repl2: hello world", p2.getReturn().message().get(0).getData());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testReloadAndSetInterpreter() throws IOException {
    Note note = notebook.createNote("note1", AuthenticationInfo.ANONYMOUS);
    Paragraph p1 = note.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);

    // when load
    notebook.reloadAllNotes(anonymous);
    assertEquals(1, notebook.getAllNotes().size());

    // then interpreter factory should be injected into all the paragraphs
    note = notebook.getNote(note.getId());
    try {
      note.getParagraphs().get(0).getBindedInterpreter();
      fail("Should throw InterpreterNotFoundException");
    } catch (InterpreterNotFoundException e) {

    }
  }

  @Test
  public void testReloadAllNotes() throws IOException {
    Note note1 = notebook.createNote("note1", AuthenticationInfo.ANONYMOUS);
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");

    Note note2 = notebook.cloneNote(note1.getId(), "copied note", AuthenticationInfo.ANONYMOUS);

    // load copied notebook on memory when reloadAllNotes() is called
    Note copiedNote = notebookRepo.get(note2.getId(), note2.getPath(), anonymous);
    notebook.reloadAllNotes(anonymous);
    List<Note> notes = notebook.getAllNotes();
    assertEquals(2 , notes.size());
    assertEquals(notes.get(0).getId(), copiedNote.getId());
    assertEquals(notes.get(0).getName(), copiedNote.getName());
    // format has make some changes due to
    // Notebook.convertFromSingleResultToMultipleResultsFormat
    assertEquals(notes.get(0).getParagraphs().size(), copiedNote.getParagraphs().size());
    assertEquals(notes.get(0).getParagraphs().get(0).getText(),
        copiedNote.getParagraphs().get(0).getText());
    assertEquals(notes.get(0).getParagraphs().get(0).settings,
        copiedNote.getParagraphs().get(0).settings);
    assertEquals(notes.get(0).getParagraphs().get(0).getTitle(),
        copiedNote.getParagraphs().get(0).getTitle());


    // delete notebook from notebook list when reloadAllNotes() is called
    notebook.reloadAllNotes(anonymous);
    notes = notebook.getAllNotes();
    assertEquals(2, notes.size());
  }

  @Test
  public void testReloadAllNotesWhenRunningNote() throws IOException, InterruptedException {
    Note note1 = notebook.createNote("note1", AuthenticationInfo.ANONYMOUS);
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 sleep 10000");
    note1.run(p1.getId(), false);
    waitForRunning(p1);
    notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);

    notebook.reloadAllNotes(anonymous);
    note1 = notebook.getNote(note1.getId());

    // ensure reload won't change note status
    assertEquals(Status.RUNNING, note1.getParagraph(p1.getId()).getStatus());
    waitForFinish(p1);
    assertEquals(Status.FINISHED, note1.getParagraph(p1.getId()).getStatus());
  }

  @Test
  public void testLoadAllNotes() {
    Note note;
    try {
      assertEquals(0, notebook.getAllNotes().size());
      note = notebook.createNote("note1", anonymous);
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map<String, Object> config = p1.getConfig();
      config.put("enabled", true);
      p1.setConfig(config);
      p1.setText("hello world");
      notebook.saveNote(note, anonymous);
    } catch (IOException fe) {
      logger.warn("Failed to create note and paragraph. Possible problem with persisting note, safe to ignore", fe);
    }

    assertEquals(1, notebook.getAllNotes().size());
  }

  @Test
  public void testPersist() throws IOException, SchedulerException {
    Note note = notebook.createNote("note1", anonymous);

    // run with default repl
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    notebook.saveNote(note, anonymous);

//    Notebook notebook2 = new Notebook(
//        conf, notebookRepo,
//        new InterpreterFactory(interpreterSettingManager),
//        interpreterSettingManager, null, null, null);

    //assertEquals(1, notebook2.getAllNotes().size());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testCreateNoteWithSubject() throws IOException, SchedulerException, RepositoryException {
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note note = notebook.createNote("note1", subject);

    assertNotNull(authorizationService.getOwners(note.getId()));
    assertEquals(1, authorizationService.getOwners(note.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(note.getId()));
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testClearParagraphOutput() throws IOException, SchedulerException {
    Note note = notebook.createNote("note1", anonymous);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());

    while (p1.isTerminated() == false || p1.getReturn() == null) Thread.yield();
    assertEquals("repl1: hello world", p1.getReturn().message().get(0).getData());

    // clear paragraph output/result
    note.clearParagraphOutput(p1.getId());
    assertNull(p1.getReturn());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testRunBlankParagraph() throws IOException, SchedulerException, InterruptedException {
    Note note = notebook.createNote("note1", anonymous);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());

    Thread.sleep(2 * 1000);
    assertEquals(Status.FINISHED, p1.getStatus());
    assertNull(p1.getDateStarted());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testRemoveNote() throws IOException, InterruptedException {
    try {
      LOGGER.info("--------------- Test testRemoveNote ---------------");
      // create a note and a paragraph
      Note note = notebook.createNote("note1", anonymous);
      int mock1ProcessNum = interpreterSettingManager.getByName("mock1").getAllInterpreterGroups().size();
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map<String, Object> config = new HashMap<>();
      p.setConfig(config);
      p.setText("%mock1 sleep 100000");
      p.execute(false);
      // wait until it is running
      while (!p.isRunning()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      assertEquals(mock1ProcessNum + 1, interpreterSettingManager.getByName("mock1").getAllInterpreterGroups().size());
      LOGGER.info("--------------- Finish Test testRemoveNote ---------------");
      notebook.removeNote(note, anonymous);
      // stop interpreter process is async, so we wait for 5 seconds here.
      Thread.sleep(5 * 1000);
      assertEquals(mock1ProcessNum, interpreterSettingManager.getByName("mock1").getAllInterpreterGroups().size());

      LOGGER.info("--------------- Finish Test testRemoveNote ---------------");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testRemoveCorruptedNote() throws IOException{
      LOGGER.info("--------------- Test testRemoveCorruptedNote ---------------");
      // create a note and a paragraph
      Note corruptedNote = notebook.createNote("note1", anonymous);
      String corruptedNotePath = notebookDir.getAbsolutePath() + corruptedNote.getPath() + "_" + corruptedNote.getId() + ".zpln";
      // corrupt note
      FileWriter myWriter = new FileWriter(corruptedNotePath);
      myWriter.write("{{{I'm corrupted;;;");
      myWriter.close();
      LOGGER.info("--------------- Finish Test testRemoveCorruptedNote ---------------");
      int numberOfNotes = notebook.getAllNotes().size();
      notebook.removeNote(corruptedNote, anonymous);
      assertEquals(numberOfNotes - 1, notebook.getAllNotes().size());
      LOGGER.info("--------------- Finish Test testRemoveCorruptedNote ---------------");
  }

  @Test
  public void testInvalidInterpreter() throws IOException, InterruptedException {
    Note note = notebook.createNote("note1", anonymous);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%invalid abc");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());

    Thread.sleep(2 * 1000);
    assertEquals(Status.ERROR, p1.getStatus());
    InterpreterResult result = p1.getReturn();
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertEquals("Interpreter invalid not found", result.message().get(0).getData());
    assertNull(p1.getDateStarted());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testRunAll() throws Exception {
    Note note = notebook.createNote("note1", anonymous);

    // p1
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config1 = p1.getConfig();
    config1.put("enabled", true);
    p1.setConfig(config1);
    p1.setText("%mock1 p1");

    // p2
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config2 = p2.getConfig();
    config2.put("enabled", false);
    p2.setConfig(config2);
    p2.setText("%mock1 p2");

    // p3
    Paragraph p3 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p3.setText("%mock1 p3");

    // when
    note.runAll(anonymous, true, false, new HashMap<>());

    assertEquals("repl1: p1", p1.getReturn().message().get(0).getData());
    assertNull(p2.getReturn());
    assertEquals("repl1: p3", p3.getReturn().message().get(0).getData());

    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testSchedule() throws InterruptedException, IOException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = new HashMap<>();
    p.setConfig(config);
    p.setText("p1");
    Date dateFinished = p.getDateFinished();
    assertNull(dateFinished);

    // set cron scheduler, once a second
    config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());
    Thread.sleep(2 * 1000);
    dateFinished = p.getDateFinished();
    assertNotNull(dateFinished);
    Thread.sleep(2 * 1000);
    assertEquals(dateFinished, p.getDateFinished());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testScheduleAgainstRunningAndPendingParagraph() throws InterruptedException, IOException {
    // create a note
    Note note = notebook.createNote("note1", anonymous);
    // append running and pending paragraphs to the note
    for (Status status : new Status[]{Status.RUNNING, Status.PENDING}) {
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map<String, Object> config = new HashMap<>();
      p.setConfig(config);
      p.setText("p");
      p.setStatus(status);
      assertNull(p.getDateFinished());
    }

    // set cron scheduler, once a second
    Map<String, Object> config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // check if the executions of the running and pending paragraphs were skipped
    for (Paragraph p : note.getParagraphs()) {
      assertNull(p.getDateFinished());
    }

    // remove the note
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testSchedulePoolUsage() throws InterruptedException, IOException {
    final int timeout = 30;
    final String everySecondCron = "* * * * * ?";
    // each run starts a new JVM and the job takes about ~5 seconds
    final CountDownLatch jobsToExecuteCount = new CountDownLatch(5);
    final Note note = notebook.createNote("note1", anonymous);

    executeNewParagraphByCron(note, everySecondCron);
    afterStatusChangedListener = new StatusChangedListener() {
      @Override
      public void onStatusChanged(Job<?> job, Status before, Status after) {
        if (after == Status.FINISHED) {
          jobsToExecuteCount.countDown();
        }
      }
    };

    assertTrue(jobsToExecuteCount.await(timeout, TimeUnit.SECONDS));

    terminateScheduledNote(note);
    afterStatusChangedListener = null;
  }

  private void executeNewParagraphByCron(Note note, String cron) {
    Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    paragraph.setText("p");
    Map<String, Object> config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", cron);
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());
  }

  @Test
  public void testScheduleDisabled() throws InterruptedException, IOException {

    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "false");
    try {
      final int timeout = 10;
      final String everySecondCron = "* * * * * ?";
      final CountDownLatch jobsToExecuteCount = new CountDownLatch(5);
      final Note note = notebook.createNote("note1", anonymous);

      executeNewParagraphByCron(note, everySecondCron);
      afterStatusChangedListener = new StatusChangedListener() {
        @Override
        public void onStatusChanged(Job<?> job, Status before, Status after) {
          if (after == Status.FINISHED) {
            jobsToExecuteCount.countDown();
          }
        }
      };

      //This job should not run because "ZEPPELIN_NOTEBOOK_CRON_ENABLE" is set to false
      assertFalse(jobsToExecuteCount.await(timeout, TimeUnit.SECONDS));

      terminateScheduledNote(note);
      afterStatusChangedListener = null;
    } finally {
      System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "true");
    }
  }

  @Test
  public void testScheduleDisabledWithName() throws InterruptedException, IOException {

    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName(), "/System");
    try {
      final int timeout = 30;
      final String everySecondCron = "* * * * * ?";
      // each run starts a new JVM and the job takes about ~5 seconds
      final CountDownLatch jobsToExecuteCount = new CountDownLatch(5);
      final Note note = notebook.createNote("note1", anonymous);

      executeNewParagraphByCron(note, everySecondCron);
      afterStatusChangedListener = new StatusChangedListener() {
        @Override
        public void onStatusChanged(Job<?> job, Status before, Status after) {
          if (after == Status.FINISHED) {
            jobsToExecuteCount.countDown();
          }
        }
      };

      //This job should not run because it's path does not matches "ZEPPELIN_NOTEBOOK_CRON_FOLDERS"
      assertFalse(jobsToExecuteCount.await(timeout, TimeUnit.SECONDS));

      terminateScheduledNote(note);
      afterStatusChangedListener = null;

      final Note noteNameSystem = notebook.createNote("/System/test1", anonymous);
      final CountDownLatch jobsToExecuteCountNameSystem = new CountDownLatch(5);

      executeNewParagraphByCron(noteNameSystem, everySecondCron);
      afterStatusChangedListener = new StatusChangedListener() {
        @Override
        public void onStatusChanged(Job<?> job, Status before, Status after) {
          if (after == Status.FINISHED) {
            jobsToExecuteCountNameSystem.countDown();
          }
        }
      };

      //This job should run because it's path contains "System/"
      assertTrue(jobsToExecuteCountNameSystem.await(timeout, TimeUnit.SECONDS));

      terminateScheduledNote(noteNameSystem);
      afterStatusChangedListener = null;
    } finally {
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName());
    }
  }

  private void terminateScheduledNote(Note note) throws IOException {
    note.getConfig().remove("cron");
    schedulerService.refreshCron(note.getId());
    notebook.removeNote(note, anonymous);
  }


  // @Test
  public void testAutoRestartInterpreterAfterSchedule() throws InterruptedException, IOException, InterpreterNotFoundException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = new HashMap<>();
    p.setConfig(config);
    p.setText("%mock1 sleep 1000");

    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setConfig(config);
    p2.setText("%mock2 sleep 500");

    // set cron scheduler, once a second
    config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "1/3 * * * * ?");
    config.put("releaseresource", true);
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());

    ExecutionContext executionContext = new ExecutionContext(anonymous.getUser(), note.getId(), "test");
    RemoteInterpreter mock1 = (RemoteInterpreter) interpreterFactory.getInterpreter("mock1", executionContext);

    RemoteInterpreter mock2 = (RemoteInterpreter) interpreterFactory.getInterpreter("mock2", executionContext);

    // wait until interpreters are started
    while (!mock1.isOpened() || !mock2.isOpened()) {
      Thread.yield();
    }

    // wait until interpreters are closed
    while (mock1.isOpened() || mock2.isOpened()) {
      Thread.yield();
    }

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    schedulerService.refreshCron(note.getId());

    // make sure all paragraph has been executed
    assertNotNull(p.getDateFinished());
    assertNotNull(p2.getDateFinished());
    notebook.removeNote(note, anonymous);
  }

//  @Test
  public void testCronWithReleaseResourceClosesOnlySpecificInterpreters()
      throws IOException, InterruptedException, InterpreterNotFoundException {
    // create a cron scheduled note.
    Note cronNote = notebook.createNote("note1", anonymous);
    Map<String, Object> config = new HashMap<>();
    config.put("cron", "1/5 * * * * ?");
    config.put("cronExecutingUser", anonymous.getUser());
    config.put("releaseresource", true);
    cronNote.setConfig(config);

    RemoteInterpreter cronNoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("mock1", new ExecutionContext(anonymous.getUser(), cronNote.getId(), "test"));

    // create a paragraph of the cron scheduled note.
    Paragraph cronNoteParagraph = cronNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    config = new HashMap<>();
    config.put("enabled", true);
    cronNoteParagraph.setConfig(config);
    cronNoteParagraph.setText("%mock1 sleep 1000");

    // create another note
    Note anotherNote = notebook.createNote("note1", anonymous);

    RemoteInterpreter anotherNoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("mock2", new ExecutionContext(anonymous.getUser(), anotherNote.getId(), "test"));

    // create a paragraph of another note
    Paragraph anotherNoteParagraph = anotherNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    config = new HashMap<>();
    config.put("enabled", true);
    anotherNoteParagraph.setConfig(config);
    anotherNoteParagraph.setText("%mock2 echo 1");

    // run the paragraph of another note
    anotherNote.run(anotherNoteParagraph.getId());

    // wait until anotherNoteInterpreter is opened
    while (!anotherNoteInterpreter.isOpened()) {
      Thread.yield();
    }

    // refresh the cron schedule
    schedulerService.refreshCron(cronNote.getId());

    // wait until cronNoteInterpreter is opened
    while (!cronNoteInterpreter.isOpened()) {
      Thread.yield();
    }

    // wait until cronNoteInterpreter is closed
    while (cronNoteInterpreter.isOpened()) {
      Thread.yield();
    }

    // wait for a few seconds
    Thread.sleep(5 * 1000);
    // test that anotherNoteInterpreter is still opened
    assertTrue(anotherNoteInterpreter.isOpened());

    // remove cron scheduler
    config = new HashMap<>();
    config.put("cron", null);
    config.put("cronExecutingUser", null);
    config.put("releaseresource", null);
    cronNote.setConfig(config);
    schedulerService.refreshCron(cronNote.getId());

    // remove notebooks
    notebook.removeNote(cronNote, anonymous);
    notebook.removeNote(anotherNote, anonymous);
  }

  @Test
  public void testCronNoteInTrash() throws InterruptedException, IOException, SchedulerException {
    Note note = notebook.createNote("~Trash/NotCron", anonymous);

    Map<String, Object> config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);

    final int jobsBeforeRefresh = schedulerService.getJobs().size();
    schedulerService.refreshCron(note.getId());
    final int jobsAfterRefresh = schedulerService.getJobs().size();

    assertEquals(jobsBeforeRefresh, jobsAfterRefresh);

    // remove cron scheduler.
    config.remove("cron");
    schedulerService.refreshCron(note.getId());
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testExportAndImportNote() throws Exception {
    Note note = notebook.createNote("note1", anonymous);

    final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    String simpleText = "hello world";
    p.setText(simpleText);

    note.runAll(anonymous, true, false, new HashMap<>());

    String exportedNoteJson = notebook.exportNote(note.getId());

    Note importedNote = notebook.importNote(exportedNoteJson, "Title", anonymous);

    Paragraph p2 = importedNote.getParagraphs().get(0);

    // Test
    assertEquals(p.getId(), p2.getId());
    assertEquals(p.getText(), p2.getText());
    assertEquals(p.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());

    // Verify import note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note importedNote2 = notebook.importNote(exportedNoteJson, "Title2", subject);
    assertNotNull(authorizationService.getOwners(importedNote2.getId()));
    assertEquals(1, authorizationService.getOwners(importedNote2.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(importedNote2.getId()));
    notebook.removeNote(note, anonymous);
    notebook.removeNote(importedNote, anonymous);
    notebook.removeNote(importedNote2, anonymous);
  }

  @Test
  public void testCloneNote() throws Exception {
    Note note = notebook.createNote("note1", anonymous);

    final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText("hello world");
    note.runAll(anonymous, true, false, new HashMap<>());

    p.setStatus(Status.RUNNING);
    Note cloneNote = notebook.cloneNote(note.getId(), "clone note", anonymous);
    Paragraph cp = cloneNote.getParagraph(0);
    assertEquals(Status.READY, cp.getStatus());

    // Keep same ParagraphId
    assertEquals(cp.getId(), p.getId());
    assertEquals(cp.getText(), p.getText());
    assertEquals(cp.getReturn().message().get(0).getData(), p.getReturn().message().get(0).getData());

    // Verify clone note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note cloneNote2 = notebook.cloneNote(note.getId(), "clone note2", subject);
    assertNotNull(authorizationService.getOwners(cloneNote2.getId()));
    assertEquals(1, authorizationService.getOwners(cloneNote2.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(cloneNote2.getId()));
    notebook.removeNote(note, anonymous);
    notebook.removeNote(cloneNote, anonymous);
    notebook.removeNote(cloneNote2, anonymous);
  }

  @Test
  public void testResourceRemovealOnParagraphNoteRemove() throws Exception {
    Note note = notebook.createNote("note1", anonymous);

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 hello");
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setText("%mock2 world");
    for (InterpreterGroup intpGroup : interpreterSettingManager.getAllInterpreterGroup()) {
      intpGroup.setResourcePool(new LocalResourcePool(intpGroup.getId()));
    }
    note.runAll(anonymous, true, false, new HashMap<>());

    assertEquals(2, interpreterSettingManager.getAllResources().size());

    // remove a paragraph
    note.removeParagraph(anonymous.getUser(), p1.getId());
    assertEquals(1, interpreterSettingManager.getAllResources().size());

    // remove note
    notebook.removeNote(note, anonymous);
    assertEquals(0, interpreterSettingManager.getAllResources().size());
  }

  @Test
  public void testAngularObjectRemovalOnNotebookRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);

    AngularObjectRegistry registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
        .getAngularObjectRegistry();

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // add paragraph scope object
    registry.add("o1", "object1", note.getId(), p1.getId());

    // add notebook scope object
    registry.add("o2", "object2", note.getId(), null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    notebook.removeNote(note, anonymous);

    // notebook scope or paragraph scope object should be removed
    assertNull(registry.get("o1", note.getId(), null));
    assertNull(registry.get("o2", note.getId(), p1.getId()));

    // global object should be remained
    assertNotNull(registry.get("o3", null, null));
  }

  @Test
  public void testAngularObjectRemovalOnParagraphRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);

    AngularObjectRegistry registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
        .getAngularObjectRegistry();

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // add paragraph scope object
    registry.add("o1", "object1", note.getId(), p1.getId());

    // add notebook scope object
    registry.add("o2", "object2", note.getId(), null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    note.removeParagraph(anonymous.getUser(), p1.getId());

    // paragraph scope should be removed
    assertNull(registry.get("o1", note.getId(), null));

    // notebook scope and global object sould be remained
    assertNotNull(registry.get("o2", note.getId(), null));
    assertNotNull(registry.get("o3", null, null));
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testAngularObjectRemovalOnInterpreterRestart() throws InterruptedException,
      IOException, InterpreterException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);

    AngularObjectRegistry registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
        .getAngularObjectRegistry();

    // add local scope object
    registry.add("o1", "object1", note.getId(), null);
    // add global scope object
    registry.add("o2", "object2", null, null);

    // restart interpreter
    interpreterSettingManager.restart(note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getId());
    registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0)
        .getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
        .getAngularObjectRegistry();

    // New InterpreterGroup will be created and its AngularObjectRegistry will be created
    assertNull(registry.get("o1", note.getId(), null));
    assertNull(registry.get("o2", null, null));
    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testPermissions() throws IOException {
    // create a note and a paragraph
    Note note = notebook.createNote("note1", anonymous);
    // empty owners, readers or writers means note is public
    assertTrue(authorizationService.isOwner(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isRunner(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isWriter(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));

    authorizationService.setOwners(note.getId(),
        new HashSet<>(Arrays.asList("user1")));
    authorizationService.setReaders(note.getId(),
        new HashSet<>(Arrays.asList("user1", "user2")));
    authorizationService.setRunners(note.getId(),
        new HashSet<>(Arrays.asList("user3")));
    authorizationService.setWriters(note.getId(),
        new HashSet<>(Arrays.asList("user1")));

    assertFalse(authorizationService.isOwner(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isOwner(note.getId(),
        new HashSet<>(Arrays.asList("user1"))));

    assertFalse(authorizationService.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user4"))));
    assertTrue(authorizationService.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));

    assertTrue(authorizationService.isRunner(note.getId(),
        new HashSet<>(Arrays.asList("user3"))));
    assertFalse(authorizationService.isRunner(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));

    assertFalse(authorizationService.isWriter(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isWriter(note.getId(),
        new HashSet<>(Arrays.asList("user1"))));

    // Test clearing of permissions
    authorizationService.setReaders(note.getId(), new HashSet<>());
    assertTrue(authorizationService.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user4"))));

    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testAuthorizationRoles() throws IOException {
    String user1 = "user1";
    String user2 = "user2";
    Set<String> roles = new HashSet<>(Arrays.asList("admin"));
    // set admin roles for both user1 and user2
    authorizationService.setRoles(user1, roles);
    authorizationService.setRoles(user2, roles);

    Note note = notebook.createNote("note1", new AuthenticationInfo(user1));

    // check that user1 is owner, reader, runner and writer
    assertTrue(authorizationService.isOwner(note.getId(),
      new HashSet<>(Arrays.asList(user1))));
    assertTrue(authorizationService.isReader(note.getId(),
      new HashSet<>(Arrays.asList(user1))));
    assertTrue(authorizationService.isRunner(note.getId(),
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isWriter(note.getId(),
      new HashSet<>(Arrays.asList(user1))));

    // since user1 and user2 both have admin role, user2 will be reader and writer as well
    assertFalse(authorizationService.isOwner(note.getId(),
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isReader(note.getId(),
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isRunner(note.getId(),
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isWriter(note.getId(),
      new HashSet<>(Arrays.asList(user2))));

    // check that user1 has note listed in his workbench
    Set<String> user1AndRoles = authorizationService.getRoles(user1);
    user1AndRoles.add(user1);
    List<NoteInfo> user1Notes = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, user1AndRoles));
    assertEquals(1, user1Notes.size());
    assertEquals(user1Notes.get(0).getId(), note.getId());

    // check that user2 has note listed in his workbench because of admin role
    Set<String> user2AndRoles = authorizationService.getRoles(user2);
    user2AndRoles.add(user2);
    List<NoteInfo> user2Notes = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, user2AndRoles));
    assertEquals(1, user2Notes.size());
    assertEquals(user2Notes.get(0).getId(), note.getId());
  }

  @Test
  public void testInterpreterSettingConfig() {
    LOGGER.info("testInterpreterSettingConfig >>> ");
    Note note = new Note("testInterpreterSettingConfig", "config_test",
        interpreterFactory, interpreterSettingManager, this, credentials, new ArrayList<>());

    // create paragraphs
    Paragraph p1 = note.addNewParagraph(anonymous);
    Map<String, Object> config = p1.getConfig();
    assertTrue(config.containsKey(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE));
    assertTrue(config.containsKey(InterpreterSetting.PARAGRAPH_CONFIG_TITLE));
    assertTrue(config.containsKey(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE));
    assertEquals(true, config.get(InterpreterSetting.PARAGRAPH_CONFIG_TITLE));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY));

    // The config_test interpreter sets the default parameters
    // in interpreter/config_test/interpreter-setting.json
    //    "config": {
    //      "runOnSelectionChange": false,
    //      "title": true,
    //      "checkEmpty": true
    //    },
    p1.setText("%config_test sleep 1000");
    p1.execute(true);

    // Check if the config_test interpreter default parameter takes effect
    LOGGER.info("p1.getConfig() =  " + p1.getConfig());
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE));
    assertEquals(true, config.get(InterpreterSetting.PARAGRAPH_CONFIG_TITLE));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY));

    // The mock1 interpreter does not set default parameters
    p1.setText("%mock1 sleep 1000");
    p1.execute(true);

    // mock1 has no config setting in interpreter-setting.json, so keep the previous config
    LOGGER.info("changed intp p1.getConfig() =  " + p1.getConfig());
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE));
    assertEquals(true, config.get(InterpreterSetting.PARAGRAPH_CONFIG_TITLE));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY));

    // user manually change config
    p1.getConfig().put(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE, true);
    p1.getConfig().put(InterpreterSetting.PARAGRAPH_CONFIG_TITLE, false);
    p1.setText("%mock1 sleep 1000");
    p1.execute(true);

    // manually config change take effect after execution
    LOGGER.info("changed intp p1.getConfig() =  " + p1.getConfig());
    assertEquals(true, config.get(InterpreterSetting.PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_TITLE));
    assertEquals(false, config.get(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY));
  }

  @Test
  public void testAbortParagraphStatusOnInterpreterRestart() throws Exception {
    Note note = notebook.createNote("note1", anonymous);

    // create three paragraphs
    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%mock1 sleep 1000");
    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%mock1 sleep 1000");
    Paragraph p3 = note.addNewParagraph(anonymous);
    p3.setText("%mock1 sleep 1000");


    note.runAll(AuthenticationInfo.ANONYMOUS, false, false, new HashMap<>());

    // wait until first paragraph finishes and second paragraph starts
    while (p1.getStatus() != Status.FINISHED || p2.getStatus() != Status.RUNNING) Thread.yield();

    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.RUNNING, p2.getStatus());
    assertEquals(Status.READY, p3.getStatus());

    // restart interpreter
    interpreterSettingManager.restart(interpreterSettingManager.getInterpreterSettingByName("mock1").getId());

    // make sure three different status aborted well.
    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.ABORT, p2.getStatus());
    assertEquals(Status.READY, p3.getStatus());

    notebook.removeNote(note, anonymous);
  }

  @Test
  public void testPerSessionInterpreterCloseOnNoteRemoval() throws IOException, InterpreterException {
    // create a notes
    Note note1 = notebook.createNote("note1", anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);

    // restart interpreter with per user session enabled
    for (InterpreterSetting setting : note1.getBindedInterpreterSettings(new ArrayList<>())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    InterpreterResult result = p1.getReturn();

    // remove note and recreate
    notebook.removeNote(note1, anonymous);
    note1 = notebook.createNote("note1", anonymous);
    p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    assertNotEquals(p1.getReturn().message(), result.message());

    notebook.removeNote(note1, anonymous);
  }

  @Test
  public void testPerSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    Note note1 = notebook.createNote("note1", anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Note note2 = notebook.createNote("note2", anonymous);
    Paragraph p2 = note2.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);
    p2.setText("%mock1 getId");
    p2.setAuthenticationInfo(anonymous);

    // run per note session disabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());


    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : note1.getBindedInterpreterSettings(new ArrayList<>())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note session enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());

    notebook.removeNote(note1, anonymous);
    notebook.removeNote(note2, anonymous);
  }


  @Test
  public void testPerNoteSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    Note note1 = notebook.createNote("note1", anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Note note2 = notebook.createNote("note2", anonymous);
    Paragraph p2 = note2.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);
    p2.setText("%mock1 getId");
    p2.setAuthenticationInfo(anonymous);

    // shared mode.
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());

    // restart interpreter with scoped mode enabled
    for (InterpreterSetting setting : note1.getBindedInterpreterSettings(new ArrayList<>())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note session enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());

    // restart interpreter with isolated mode enabled
    for (InterpreterSetting setting : note1.getBindedInterpreterSettings(new ArrayList<>())) {
      setting.getOption().setPerNote(InterpreterOption.ISOLATED);
      setting.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note process enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());

    notebook.removeNote(note1, anonymous);
    notebook.removeNote(note2, anonymous);
  }

  public void testNotebookEventListener() throws IOException {
    final AtomicInteger onNoteRemove = new AtomicInteger(0);
    final AtomicInteger onNoteCreate = new AtomicInteger(0);
    final AtomicInteger onParagraphRemove = new AtomicInteger(0);
    final AtomicInteger onParagraphCreate = new AtomicInteger(0);

    notebook.addNotebookEventListener(new NoteEventListener() {
      @Override
      public void onNoteRemove(Note note, AuthenticationInfo subject) {
        onNoteRemove.incrementAndGet();
      }

      @Override
      public void onNoteCreate(Note note, AuthenticationInfo subject) {
        onNoteCreate.incrementAndGet();
      }

      @Override
      public void onNoteUpdate(Note note, AuthenticationInfo subject) {

      }

      @Override
      public void onParagraphRemove(Paragraph p) {
        onParagraphRemove.incrementAndGet();
      }

      @Override
      public void onParagraphCreate(Paragraph p) {
        onParagraphCreate.incrementAndGet();
      }

      @Override
      public void onParagraphUpdate(Paragraph p) throws IOException {

      }

      @Override
      public void onParagraphStatusChange(Paragraph p, Status status) throws IOException {

      }

    });

    Note note1 = notebook.createNote("note1", anonymous);
    assertEquals(1, onNoteCreate.get());

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, onParagraphCreate.get());

    note1.addCloneParagraph(p1, AuthenticationInfo.ANONYMOUS);
    assertEquals(2, onParagraphCreate.get());

    note1.removeParagraph(anonymous.getUser(), p1.getId());
    assertEquals(1, onParagraphRemove.get());

    notebook.removeNote(note1, anonymous);
    assertEquals(1, onNoteRemove.get());
    assertEquals(1, onParagraphRemove.get());
  }

  @Test
  public void testGetAllNotes() throws Exception {
    Note note1 = notebook.createNote("note1", anonymous);
    Note note2 = notebook.createNote("note2", anonymous);
    assertEquals(2, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("anonymous")))).size());

    authorizationService.setOwners(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    authorizationService.setWriters(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    authorizationService.setRunners(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    authorizationService.setReaders(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    assertEquals(1, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("anonymous")))).size());
    assertEquals(2, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1")))).size());

    authorizationService.setOwners(note2.getId(), new HashSet<>(Arrays.asList("user2")));
    authorizationService.setWriters(note2.getId(), new HashSet<>(Arrays.asList("user2")));
    authorizationService.setReaders(note2.getId(), new HashSet<>(Arrays.asList("user2")));
    authorizationService.setRunners(note2.getId(), new HashSet<>(Arrays.asList("user2")));
    assertEquals(0, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("anonymous")))).size());
    assertEquals(1, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1")))).size());
    assertEquals(1, notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2")))).size());
    notebook.removeNote(note1, AuthenticationInfo.ANONYMOUS);
    notebook.removeNote(note2, AuthenticationInfo.ANONYMOUS);
  }

  @Test
  public void testCreateDuplicateNote() throws Exception {
    Note note1 = notebook.createNote("note1", anonymous);
    try {
      notebook.createNote("note1", anonymous);
      fail("Should not be able to create same note 'note1'");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Note '/note1' existed"));
    } finally {
      notebook.removeNote(note1, anonymous);
    }
  }

  @Test
  public void testGetAllNotesWithDifferentPermissions() throws IOException {
    List<Note> notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    List<Note> notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(0, notes1.size());
    assertEquals(0, notes2.size());

    //creates note and sets user1 owner
    Note note1 = notebook.createNote("note1", new AuthenticationInfo("user1"));

    // note is public since readers and writers empty
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setReaders(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    //note is public since writers empty
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setRunners(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setWriters(note1.getId(), new HashSet<>(Arrays.asList("user1")));
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(0, notes2.size());
  }

  @Test
  public void testPublicPrivateNewNote() throws IOException {
    // case of public note
    assertTrue(conf.isNotebookPublic());
    assertTrue(authorizationService.isPublic());

    List<Note> notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    List<Note> notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(0, notes1.size());
    assertEquals(0, notes2.size());

    // user1 creates note
    Note notePublic = notebook.createNote("note1", new AuthenticationInfo("user1"));

    // both users have note
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());
    assertEquals(notes1.get(0).getId(), notePublic.getId());
    assertEquals(notes2.get(0).getId(), notePublic.getId());

    // user1 is only owner
    assertEquals(1, authorizationService.getOwners(notePublic.getId()).size());
    assertEquals(0, authorizationService.getReaders(notePublic.getId()).size());
    assertEquals(0, authorizationService.getRunners(notePublic.getId()).size());
    assertEquals(0, authorizationService.getWriters(notePublic.getId()).size());

    // case of private note
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "false");
    ZeppelinConfiguration conf2 = ZeppelinConfiguration.create();
    assertFalse(conf2.isNotebookPublic());
    // notebook authorization reads from conf, so no need to re-initilize
    assertFalse(authorizationService.isPublic());

    // check that still 1 note per user
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    // create private note
    Note notePrivate = notebook.createNote("note2", new AuthenticationInfo("user1"));

    // only user1 have notePrivate right after creation
    notes1 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getAllNotes(note -> authorizationService.isReader(note.getId(), new HashSet<>(Arrays.asList("user2"))));
    assertEquals(2, notes1.size());
    assertEquals(1, notes2.size());
    assertEquals(true, notes1.contains(notePrivate));

    // user1 have all rights
    assertEquals(1, authorizationService.getOwners(notePrivate.getId()).size());
    assertEquals(1, authorizationService.getReaders(notePrivate.getId()).size());
    assertEquals(1, authorizationService.getRunners(notePrivate.getId()).size());
    assertEquals(1, authorizationService.getWriters(notePrivate.getId()).size());

    //set back public to true
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "true");
    ZeppelinConfiguration.create();
  }

  @Test
  public void testCloneImportCheck() throws IOException {
    Note sourceNote = notebook.createNote("note1", new AuthenticationInfo("user"));
    sourceNote.setName("TestNote");

    assertEquals("TestNote",sourceNote.getName());

    Paragraph sourceParagraph = sourceNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    assertEquals("anonymous", sourceParagraph.getUser());

    Note destNote = notebook.createNote("note2", new AuthenticationInfo("user"));
    destNote.setName("ClonedNote");
    assertEquals("ClonedNote",destNote.getName());

    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
    	  destNote.addCloneParagraph(p, AuthenticationInfo.ANONYMOUS);
      assertEquals("anonymous", p.getUser());
    }
  }

  @Test
  public void testMoveNote() throws InterruptedException, IOException {
    Note note = null;
    try {
      note = notebook.createNote("note1", anonymous);
      assertEquals("note1", note.getName());
      assertEquals("/note1", note.getPath());
      notebook.moveNote(note.getId(), "/tmp/note2", anonymous);

      // read note json file to check the name field is updated
      File noteFile = new File(conf.getNotebookDir() + "/" + notebookRepo.buildNoteFileName(note));
      String noteJson = IOUtils.toString(new FileInputStream(noteFile));
      assertTrue(noteJson, noteJson.contains("note2"));
    } finally {
      if (note != null) {
        notebook.removeNote(note, anonymous);
      }
    }
  }

  private void delete(File file) {
    if (file.isFile()) {
      file.delete();
    } else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
  }

  @Override
  public void noteRunningStatusChange(String noteId, boolean newStatus) {

  }

  @Override
  public void onProgressUpdate(Paragraph paragraph, int progress) {
  }

  @Override
  public void onStatusChange(Paragraph paragraph, Status before, Status after) {
    if (afterStatusChangedListener != null) {
      afterStatusChangedListener.onStatusChanged(paragraph, before, after);
    }
  }


  private interface StatusChangedListener {
    void onStatusChanged(Job<?> job, Status before, Status after);
  }


  private void waitForFinish(Paragraph p) {
    while (p.getStatus() != Status.FINISHED
            && p.getStatus() != Status.ERROR
            && p.getStatus() != Status.ABORT) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        LOGGER.error("Exception in WebDriverManager while getWebDriver ", e);
      }
    }
  }

  private void waitForRunning(Paragraph p) {
    while (p.getStatus() != Status.RUNNING) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        LOGGER.error("Exception in WebDriverManager while getWebDriver ", e);
      }
    }
  }
}
