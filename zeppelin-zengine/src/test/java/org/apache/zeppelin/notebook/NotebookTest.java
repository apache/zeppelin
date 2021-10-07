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
import java.nio.charset.StandardCharsets;
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

    notebookRepo = new VFSNotebookRepo();
    notebookRepo.init(conf);
    noteManager = new NoteManager(notebookRepo, conf);
    authorizationService = new AuthorizationService(noteManager, conf);

    credentials = new Credentials(conf);
    notebook = new Notebook(conf, authorizationService, notebookRepo, noteManager, interpreterFactory, interpreterSettingManager, credentials, null);
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
    notebook = new Notebook(conf, mock(AuthorizationService.class), notebookRepo, new NoteManager(notebookRepo, conf), interpreterFactory,
        interpreterSettingManager, credentials, null);
    assertFalse("Revision is not supported in DummyNotebookRepo", notebook.isRevisionSupported());

    notebookRepo = new DummyNotebookRepoWithVersionControl();
    notebook = new Notebook(conf, mock(AuthorizationService.class), notebookRepo, new NoteManager(notebookRepo, conf), interpreterFactory,
        interpreterSettingManager, credentials, null);
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
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
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
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testReloadAndSetInterpreter() throws IOException {
    String noteId = notebook.createNote("note1", AuthenticationInfo.ANONYMOUS);
    notebook.processNote(noteId,
      note -> {
        Paragraph p1 = note.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
        p1.setText("%md hello world");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

    // when load
    notebook.reloadAllNotes(anonymous);
    assertEquals(1, notebook.getNotesInfo().size());

    // then interpreter factory should be injected into all the paragraphs
    notebook.processNote(noteId,
      newNote -> {
        try {
          newNote.getParagraphs().get(0).getBindedInterpreter();
          fail("Should throw InterpreterNotFoundException");
        } catch (InterpreterNotFoundException e) {

        }
        return null;
      });

  }

  @Test
  public void testReloadAllNotes() throws IOException {
    String note1Id = notebook.createNote("note1", AuthenticationInfo.ANONYMOUS);
    notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
        p1.setText("%md hello world");
        return null;
      });

    String note2Id = notebook.cloneNote(note1Id, "copied note", AuthenticationInfo.ANONYMOUS);

    // load copied notebook on memory when reloadAllNotes() is called
    Note copiedNote = notebookRepo.get(note2Id, "/copied note", anonymous);
    notebook.reloadAllNotes(anonymous);
    List<NoteInfo> notesInfo = notebook.getNotesInfo();
    assertEquals(2 , notesInfo.size());
    NoteInfo found = null;
    for (NoteInfo noteInfo : notesInfo) {
      if (noteInfo.getId().equals(copiedNote.getId())) {
        found = noteInfo;
        break;
      }
    }

    assertNotNull(found);
    assertEquals(found.getId(), copiedNote.getId());
    assertEquals(notebook.processNote(found.getId(), Note::getName), copiedNote.getName());
    // format has make some changes due to
    // Notebook.convertFromSingleResultToMultipleResultsFormat
    notebook.processNote(found.getId(),
      note -> {
        assertEquals(note.getParagraphs().size(), copiedNote.getParagraphs().size());
        assertEquals(note.getParagraphs().get(0).getText(),
            copiedNote.getParagraphs().get(0).getText());
        assertEquals(note.getParagraphs().get(0).settings,
            copiedNote.getParagraphs().get(0).settings);
        assertEquals(note.getParagraphs().get(0).getTitle(),
            copiedNote.getParagraphs().get(0).getTitle());
        return null;
      });

    // delete notebook from notebook list when reloadAllNotes() is called
    notebook.reloadAllNotes(anonymous);
    notesInfo = notebook.getNotesInfo();
    assertEquals(2, notesInfo.size());
  }

  @Test
  public void testLoadAllNotes() {
    try {
      assertEquals(0, notebook.getNotesInfo().size());
      String noteId = notebook.createNote("note1", anonymous);
      notebook.processNote(noteId,
        note -> {
          Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
          Map<String, Object> config = p1.getConfig();
          config.put("enabled", true);
          p1.setConfig(config);
          p1.setText("hello world");
          notebook.saveNote(note, anonymous);
          return null;
        });
    } catch (IOException fe) {
      logger.warn("Failed to create note and paragraph. Possible problem with persisting note, safe to ignore", fe);
    }

    assertEquals(1, notebook.getNotesInfo().size());
  }

  @Test
  public void testPersist() throws IOException, SchedulerException {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
        // run with default repl
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map<String, Object> config = p1.getConfig();
        config.put("enabled", true);
        p1.setConfig(config);
        p1.setText("hello world");
        notebook.saveNote(note, anonymous);
        assertEquals(1, notebook.getNotesInfo().size());
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testCreateNoteWithSubject() throws IOException, SchedulerException, RepositoryException {
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    String noteId = notebook.createNote("note1", subject);
    assertNotNull(authorizationService.getOwners(noteId));
    assertEquals(1, authorizationService.getOwners(noteId).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(noteId));
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testClearParagraphOutput() throws IOException, SchedulerException {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
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
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testRunBlankParagraph() throws IOException, SchedulerException, InterruptedException {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("");
        p1.setAuthenticationInfo(anonymous);
        note.run(p1.getId());
        try {
          Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {
          fail("Interrupted");
        }
        assertEquals(Status.FINISHED, p1.getStatus());
        assertNull(p1.getDateStarted());
        return null;
      });

    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testRemoveNote() throws IOException, InterruptedException {
    try {
      LOGGER.info("--------------- Test testRemoveNote ---------------");
      // create a note and a paragraph
      String noteId = notebook.createNote("note1", anonymous);
      int mock1ProcessNum = interpreterSettingManager.getByName("mock1").getAllInterpreterGroups().size();
      Paragraph p = notebook.processNote(noteId,
        note -> {
          return note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        });
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
      notebook.removeNote(noteId, anonymous);
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
      String corruptedNoteId = notebook.createNote("note1", anonymous);
      String corruptedNotePath = notebook.processNote(corruptedNoteId,
        corruptedNote -> {
          return notebookDir.getAbsolutePath() + corruptedNote.getPath() + "_" + corruptedNote.getId() + ".zpln";
        });

      // corrupt note
      FileWriter myWriter = new FileWriter(corruptedNotePath);
      myWriter.write("{{{I'm corrupted;;;");
      myWriter.close();
      LOGGER.info("--------------- Finish Test testRemoveCorruptedNote ---------------");
      int numberOfNotes = notebook.getNotesInfo().size();
      notebook.removeNote(corruptedNoteId, anonymous);
      assertEquals(numberOfNotes - 1, notebook.getNotesInfo().size());
      LOGGER.info("--------------- Finish Test testRemoveCorruptedNote ---------------");
  }

  @Test
  public void testInvalidInterpreter() throws IOException, InterruptedException {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("%invalid abc");
        p1.setAuthenticationInfo(anonymous);
        note.run(p1.getId());

        try {
          Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {
          fail("Interrupted");
        }
        assertEquals(Status.ERROR, p1.getStatus());
        InterpreterResult result = p1.getReturn();
        assertEquals(InterpreterResult.Code.ERROR, result.code());
        assertEquals("Interpreter invalid not found", result.message().get(0).getData());
        assertNull(p1.getDateStarted());
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testRunAll() throws Exception {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
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
        try {
          note.runAll(anonymous, true, false, new HashMap<>());
        } catch (Exception e) {
          fail("Exception in runAll");
        }

        assertEquals("repl1: p1", p1.getReturn().message().get(0).getData());
        assertNull(p2.getReturn());
        assertEquals("repl1: p3", p3.getReturn().message().get(0).getData());
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testSchedule() throws InterruptedException, IOException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);
    // use write lock, because note configuration is overwritten
    Paragraph paragraph = notebook.processNote(noteId,
      note -> {
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
        return p;
      });
    schedulerService.refreshCron(noteId);
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    // use write lock, because note configuration is overwritten
    notebook.processNote(noteId,
      note -> {
        Map<String, Object> config = note.getConfig();
        config.put("cron", null);
        note.setConfig(config);
        return null;
      });
    schedulerService.refreshCron(noteId);
    // wait a little until running processes are finished
    Thread.sleep(3 * 1000);
    Date dateFinished = paragraph.getDateFinished();
    assertNotNull(dateFinished);
    // wait a little bit to check that no other tasks are being executed
    Thread.sleep(2 * 1000);
    assertEquals(dateFinished, paragraph.getDateFinished());
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testScheduleAgainstRunningAndPendingParagraph() throws InterruptedException, IOException {
    // create a note
    String noteId = notebook.createNote("note1", anonymous);
    // append running and pending paragraphs to the note
    // use write lock, because note configuration is overwritten
    notebook.processNote(noteId,
      note -> {
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
        return null;
      });



    schedulerService.refreshCron(noteId);
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    notebook.processNote(noteId,
      note -> {
        Map<String, Object> config = note.getConfig();
        config.put("cron", null);
        note.setConfig(config);
        return null;
      });

    schedulerService.refreshCron(noteId);
    Thread.sleep(2 * 1000);

    // check if the executions of the running and pending paragraphs were skipped
    notebook.processNote(noteId,
      note -> {
        for (Paragraph p : note.getParagraphs()) {
          assertNull(p.getDateFinished());
        }
        return null;
      });

    // remove the note
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testSchedulePoolUsage() throws InterruptedException, IOException {
    final int timeout = 30;
    final String everySecondCron = "* * * * * ?";
    // each run starts a new JVM and the job takes about ~5 seconds
    final CountDownLatch jobsToExecuteCount = new CountDownLatch(5);
    final String noteId = notebook.createNote("note1", anonymous);

    executeNewParagraphByCron(noteId, everySecondCron);
    afterStatusChangedListener = new StatusChangedListener() {
      @Override
      public void onStatusChanged(Job<?> job, Status before, Status after) {
        if (after == Status.FINISHED) {
          jobsToExecuteCount.countDown();
        }
      }
    };

    assertTrue(jobsToExecuteCount.await(timeout, TimeUnit.SECONDS));

    terminateScheduledNote(noteId);
    afterStatusChangedListener = null;
  }

  private void executeNewParagraphByCron(String noteId, String cron) throws IOException {
    notebook.processNote(noteId,
      note -> {
        Paragraph paragraph = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        paragraph.setText("p");
        Map<String, Object> config = note.getConfig();
        config.put("enabled", true);
        config.put("cron", cron);
        note.setConfig(config);
        return null;
      });
    schedulerService.refreshCron(noteId);
  }

  @Test
  public void testScheduleDisabled() throws InterruptedException, IOException {

    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_ENABLE.getVarName(), "false");
    try {
      final int timeout = 10;
      final String everySecondCron = "* * * * * ?";
      final CountDownLatch jobsToExecuteCount = new CountDownLatch(5);
      final String noteId = notebook.createNote("note1", anonymous);

      executeNewParagraphByCron(noteId, everySecondCron);
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

      terminateScheduledNote(noteId);
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
      final String noteId = notebook.createNote("note1", anonymous);

      executeNewParagraphByCron(noteId, everySecondCron);
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

      terminateScheduledNote(noteId);
      afterStatusChangedListener = null;

      final String noteNameSystemId = notebook.createNote("/System/test1", anonymous);
      final CountDownLatch jobsToExecuteCountNameSystem = new CountDownLatch(5);

      executeNewParagraphByCron(noteNameSystemId, everySecondCron);
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

      terminateScheduledNote(noteNameSystemId);
      afterStatusChangedListener = null;
    } finally {
      System.clearProperty(ConfVars.ZEPPELIN_NOTEBOOK_CRON_FOLDERS.getVarName());
    }
  }

  private void terminateScheduledNote(String noteId) throws IOException {
    notebook.processNote(noteId,
      note -> {
        note.getConfig().remove("cron");
        return null;
      });

    schedulerService.refreshCron(noteId);
    notebook.removeNote(noteId, anonymous);
  }


  // @Test
  public void testAutoRestartInterpreterAfterSchedule() throws InterruptedException, IOException, InterpreterNotFoundException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);
    // use write lock, because we are overwrite the note configuration
    notebook.processNote(noteId,
      note -> {
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
        return null;
      });

    schedulerService.refreshCron(noteId);

    ExecutionContext executionContext = new ExecutionContext(anonymous.getUser(), noteId, "test");
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
    // use write lock because config is overwritten
    notebook.processNote(noteId,
      note -> {
        Map<String, Object> config = note.getConfig();
        config.put("cron", null);
        note.setConfig(config);
        return null;
      });
    schedulerService.refreshCron(noteId);

    // make sure all paragraph has been executed
    notebook.processNote(noteId,
      note -> {
        for (Paragraph p : note.getParagraphs()) {
          assertNotNull(p);
        }
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

//  @Test
  public void testCronWithReleaseResourceClosesOnlySpecificInterpreters()
      throws IOException, InterruptedException, InterpreterNotFoundException {
    // create a cron scheduled note.
    String cronNoteId = notebook.createNote("note1", anonymous);
    // use write lock, because we overwrite the note configuration
    notebook.processNote(cronNoteId,
      cronNote -> {
        Map<String, Object> config = new HashMap<>();
        config.put("cron", "1/5 * * * * ?");
        config.put("cronExecutingUser", anonymous.getUser());
        config.put("releaseresource", true);
        cronNote.setConfig(config);
        return null;
      });
    RemoteInterpreter cronNoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("mock1", new ExecutionContext(anonymous.getUser(), cronNoteId, "test"));

    // create a paragraph of the cron scheduled note.
    notebook.processNote(cronNoteId,
      cronNote -> {
        Paragraph cronNoteParagraph = cronNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        cronNoteParagraph.setConfig(config);
        cronNoteParagraph.setText("%mock1 sleep 1000");
        return null;
      });


    // create another note
    String anotherNoteId = notebook.createNote("note1", anonymous);

    RemoteInterpreter anotherNoteInterpreter =
        (RemoteInterpreter) interpreterFactory.getInterpreter("mock2", new ExecutionContext(anonymous.getUser(), anotherNoteId, "test"));

    // create a paragraph of another note
    notebook.processNote(anotherNoteId,
      anotherNote -> {
        Paragraph anotherNoteParagraph = anotherNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        anotherNoteParagraph.setConfig(config);
        anotherNoteParagraph.setText("%mock2 echo 1");
        // run the paragraph of another note
        anotherNote.run(anotherNoteParagraph.getId());
        return null;
      });


    // wait until anotherNoteInterpreter is opened
    while (!anotherNoteInterpreter.isOpened()) {
      Thread.yield();
    }

    // refresh the cron schedule
    schedulerService.refreshCron(cronNoteId);

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
    // use write lock because config is overwritten
    notebook.processNote(cronNoteId,
      cronNote -> {
        Map<String, Object> config = new HashMap<>();
        config.put("cron", null);
        config.put("cronExecutingUser", null);
        config.put("releaseresource", null);
        cronNote.setConfig(config);
        return null;
      });

    schedulerService.refreshCron(cronNoteId);

    // remove notebooks
    notebook.removeNote(cronNoteId, anonymous);
    notebook.removeNote(anotherNoteId, anonymous);
  }

  @Test
  public void testCronNoteInTrash() throws InterruptedException, IOException, SchedulerException {
    String noteId = notebook.createNote("~Trash/NotCron", anonymous);
    // use write lock because we overwrite the note config
    notebook.processNote(noteId,
      note -> {
        Map<String, Object> config = note.getConfig();
        config.put("enabled", true);
        config.put("cron", "* * * * * ?");
        note.setConfig(config);
        return null;
      });
    final int jobsBeforeRefresh = schedulerService.getJobs().size();
    schedulerService.refreshCron(noteId);
    final int jobsAfterRefresh = schedulerService.getJobs().size();

    assertEquals(jobsBeforeRefresh, jobsAfterRefresh);

    // remove cron scheduler.
    notebook.processNote(noteId,
      note -> {
        note.getConfig().remove("cron");
        return null;
      });
    schedulerService.refreshCron(noteId);
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testExportAndImportNote() throws Exception {
    String noteId = notebook.createNote("note1", anonymous);

    notebook.processNote(noteId,
      note -> {
        final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        String simpleText = "hello world";
        p.setText(simpleText);

        try {
          note.runAll(anonymous, true, false, new HashMap<>());
        } catch (Exception e) {
          fail();
        }
        return null;
      });

    String exportedNoteJson = notebook.exportNote(noteId);

    String importedNoteId = notebook.importNote(exportedNoteJson, "Title", anonymous);
    notebook.processNote(noteId,
      note -> {
        Paragraph p = note.getParagraph(0);
        notebook.processNote(importedNoteId,
          importedNote -> {
            Paragraph p2 = importedNote.getParagraphs().get(0);

            // Test
            assertEquals(p.getId(), p2.getId());
            assertEquals(p.getText(), p2.getText());
            assertEquals(p.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());
            return null;
          });
        return null;
      });


    // Verify import note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    String importedNote2Id = notebook.importNote(exportedNoteJson, "Title2", subject);
    assertNotNull(authorizationService.getOwners(importedNote2Id));
    assertEquals(1, authorizationService.getOwners(importedNote2Id).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(importedNote2Id));
    notebook.removeNote(noteId, anonymous);
    notebook.removeNote(importedNoteId, anonymous);
    notebook.removeNote(importedNote2Id, anonymous);
  }

  @Test
  public void testCloneNote() throws Exception {
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
        final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("hello world");
        try {
          note.runAll(anonymous, true, false, new HashMap<>());
        } catch (Exception e) {
          fail();
        }

        p.setStatus(Status.RUNNING);
        return null;
      });

    String cloneNoteId = notebook.cloneNote(noteId, "clone note", anonymous);
    notebook.processNote(noteId,
      note -> {
        Paragraph p = note.getParagraph(0);
        notebook.processNote(cloneNoteId,
          cloneNote -> {
            Paragraph cp = cloneNote.getParagraph(0);
            assertEquals(Status.READY, cp.getStatus());
            // Keep same ParagraphId
            assertEquals(cp.getId(), p.getId());
            assertEquals(cp.getText(), p.getText());
            assertEquals(cp.getReturn().message().get(0).getData(), p.getReturn().message().get(0).getData());
            return null;
          });
        return null;
      });


    // Verify clone note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    String cloneNote2Id = notebook.cloneNote(noteId, "clone note2", subject);
    assertNotNull(authorizationService.getOwners(cloneNote2Id));
    assertEquals(1, authorizationService.getOwners(cloneNote2Id).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, authorizationService.getOwners(cloneNote2Id));
    notebook.removeNote(noteId, anonymous);
    notebook.removeNote(cloneNoteId, anonymous);
    notebook.removeNote(cloneNote2Id, anonymous);
  }

  @Test
  public void testResourceRemovealOnParagraphNoteRemove() throws Exception {
    String noteId = notebook.createNote("note1", anonymous);

    notebook.processNote(noteId,
      note -> {
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("%mock1 hello");
        Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p2.setText("%mock2 world");
        for (InterpreterGroup intpGroup : interpreterSettingManager.getAllInterpreterGroup()) {
          intpGroup.setResourcePool(new LocalResourcePool(intpGroup.getId()));
        }
        try {
          note.runAll(anonymous, true, false, new HashMap<>());
        } catch (Exception e) {
          fail();
        }

        assertEquals(2, interpreterSettingManager.getAllResources().size());

        // remove a paragraph
        note.removeParagraph(anonymous.getUser(), p1.getId());
        assertEquals(1, interpreterSettingManager.getAllResources().size());
        return null;
      });



    // remove note
    notebook.removeNote(noteId, anonymous);
    assertEquals(0, interpreterSettingManager.getAllResources().size());
  }

  @Test
  public void testAngularObjectRemovalOnNotebookRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);
    AngularObjectRegistry registry = notebook.processNote(noteId,
      note -> {
        return note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
          .getAngularObjectRegistry();
      });
    String paragraphId = notebook.processNote(noteId,
      note -> {
        return note.addNewParagraph(AuthenticationInfo.ANONYMOUS).getId();
      });
    // add paragraph scope object
    registry.add("o1", "object1", noteId, paragraphId);

    // add notebook scope object
    registry.add("o2", "object2", noteId, null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    notebook.removeNote(noteId, anonymous);

    // notebook scope or paragraph scope object should be removed
    assertNull(registry.get("o1", noteId, null));
    assertNull(registry.get("o2", noteId, paragraphId));

    // global object should be remained
    assertNotNull(registry.get("o3", null, null));
  }

  @Test
  public void testAngularObjectRemovalOnParagraphRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);

    AngularObjectRegistry registry = notebook.processNote(noteId,
      note -> {
        return note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
          .getAngularObjectRegistry();
      });

    String paragraphId = notebook.processNote(noteId,
      note -> {
        return note.addNewParagraph(AuthenticationInfo.ANONYMOUS).getId();
      });

    // add paragraph scope object
    registry.add("o1", "object1", noteId, paragraphId);

    // add notebook scope object
    registry.add("o2", "object2", noteId, null);

    // add global scope object
    registry.add("o3", "object3", null, null);
    // remove notebook
    notebook.processNote(noteId,
      note -> {
        note.removeParagraph(anonymous.getUser(), paragraphId);
        return null;
      });


    // paragraph scope should be removed
    assertNull(registry.get("o1", noteId, null));

    // notebook scope and global object sould be remained
    assertNotNull(registry.get("o2", noteId, null));
    assertNotNull(registry.get("o3", null, null));

    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testAngularObjectRemovalOnInterpreterRestart() throws InterruptedException,
      IOException, InterpreterException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);
    notebook.processNote(noteId,
      note -> {
        AngularObjectRegistry registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
            .getAngularObjectRegistry();

        // add local scope object
        registry.add("o1", "object1", note.getId(), null);
        // add global scope object
        registry.add("o2", "object2", null, null);

        // restart interpreter
        try {
          interpreterSettingManager.restart(note.getBindedInterpreterSettings(new ArrayList<>()).get(0).getId());
        } catch (InterpreterException e) {
          fail();
        }
        registry = note.getBindedInterpreterSettings(new ArrayList<>()).get(0)
            .getOrCreateInterpreterGroup(anonymous.getUser(), note.getId())
            .getAngularObjectRegistry();

        // New InterpreterGroup will be created and its AngularObjectRegistry will be created
        assertNull(registry.get("o1", note.getId(), null));
        assertNull(registry.get("o2", null, null));
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testPermissions() throws IOException {
    // create a note and a paragraph
    String noteId = notebook.createNote("note1", anonymous);
    // empty owners, readers or writers means note is public
    assertTrue(authorizationService.isOwner(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isReader(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isRunner(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isWriter(noteId,
        new HashSet<>(Arrays.asList("user2"))));

    authorizationService.setOwners(noteId,
        new HashSet<>(Arrays.asList("user1")));
    authorizationService.setReaders(noteId,
        new HashSet<>(Arrays.asList("user1", "user2")));
    authorizationService.setRunners(noteId,
        new HashSet<>(Arrays.asList("user3")));
    authorizationService.setWriters(noteId,
        new HashSet<>(Arrays.asList("user1")));

    assertFalse(authorizationService.isOwner(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isOwner(noteId,
        new HashSet<>(Arrays.asList("user1"))));

    assertFalse(authorizationService.isReader(noteId,
        new HashSet<>(Arrays.asList("user4"))));
    assertTrue(authorizationService.isReader(noteId,
        new HashSet<>(Arrays.asList("user2"))));

    assertTrue(authorizationService.isRunner(noteId,
        new HashSet<>(Arrays.asList("user3"))));
    assertFalse(authorizationService.isRunner(noteId,
        new HashSet<>(Arrays.asList("user2"))));

    assertFalse(authorizationService.isWriter(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isWriter(noteId,
        new HashSet<>(Arrays.asList("user1"))));

    // Test clearing of permissions
    authorizationService.setReaders(noteId, new HashSet<>());
    assertTrue(authorizationService.isReader(noteId,
        new HashSet<>(Arrays.asList("user2"))));
    assertTrue(authorizationService.isReader(noteId,
        new HashSet<>(Arrays.asList("user4"))));

    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testAuthorizationRoles() throws IOException {
    String user1 = "user1";
    String user2 = "user2";
    Set<String> roles = new HashSet<>(Arrays.asList("admin"));
    // set admin roles for both user1 and user2
    authorizationService.setRoles(user1, roles);
    authorizationService.setRoles(user2, roles);

    String noteId = notebook.createNote("note1", new AuthenticationInfo(user1));

    // check that user1 is owner, reader, runner and writer
    assertTrue(authorizationService.isOwner(noteId,
      new HashSet<>(Arrays.asList(user1))));
    assertTrue(authorizationService.isReader(noteId,
      new HashSet<>(Arrays.asList(user1))));
    assertTrue(authorizationService.isRunner(noteId,
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isWriter(noteId,
      new HashSet<>(Arrays.asList(user1))));

    // since user1 and user2 both have admin role, user2 will be reader and writer as well
    assertFalse(authorizationService.isOwner(noteId,
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isReader(noteId,
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isRunner(noteId,
      new HashSet<>(Arrays.asList(user2))));
    assertTrue(authorizationService.isWriter(noteId,
      new HashSet<>(Arrays.asList(user2))));

    // check that user1 has note listed in his workbench
    Set<String> user1AndRoles = authorizationService.getRoles(user1);
    user1AndRoles.add(user1);
    List<NoteInfo> user1Notes = notebook.getNotesInfo(noteIdTmp -> authorizationService.isReader(noteIdTmp, user1AndRoles));
    assertEquals(1, user1Notes.size());
    assertEquals(user1Notes.get(0).getId(), noteId);

    // check that user2 has note listed in his workbench because of admin role
    Set<String> user2AndRoles = authorizationService.getRoles(user2);
    user2AndRoles.add(user2);
    List<NoteInfo> user2Notes = notebook.getNotesInfo(noteIdTmp -> authorizationService.isReader(noteIdTmp, user2AndRoles));
    assertEquals(1, user2Notes.size());
    assertEquals(user2Notes.get(0).getId(), noteId);
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
    String noteId = notebook.createNote("note1", anonymous);

    notebook.processNote(noteId,
      note -> {
        // create three paragraphs
        Paragraph p1 = note.addNewParagraph(anonymous);
        p1.setText("%mock1 sleep 1000");
        Paragraph p2 = note.addNewParagraph(anonymous);
        p2.setText("%mock1 sleep 1000");
        Paragraph p3 = note.addNewParagraph(anonymous);
        p3.setText("%mock1 sleep 1000");


        try {
          note.runAll(AuthenticationInfo.ANONYMOUS, false, false, new HashMap<>());
        } catch (Exception e1) {
          fail();
        }

        // wait until first paragraph finishes and second paragraph starts
        while (p1.getStatus() != Status.FINISHED || p2.getStatus() != Status.RUNNING) Thread.yield();

        assertEquals(Status.FINISHED, p1.getStatus());
        assertEquals(Status.RUNNING, p2.getStatus());
        assertEquals(Status.READY, p3.getStatus());

        // restart interpreter
        try {
          interpreterSettingManager.restart(interpreterSettingManager.getInterpreterSettingByName("mock1").getId());
        } catch (InterpreterException e) {
          fail();
        }

        // make sure three different status aborted well.
        assertEquals(Status.FINISHED, p1.getStatus());
        assertEquals(Status.ABORT, p2.getStatus());
        assertEquals(Status.READY, p3.getStatus());
        return null;
      });
    notebook.removeNote(noteId, anonymous);
  }

  @Test
  public void testPerSessionInterpreterCloseOnNoteRemoval() throws IOException, InterpreterException {
    // create a notes
    String note1Id = notebook.createNote("note1", anonymous);
    InterpreterResult result = notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("%mock1 getId");
        p1.setAuthenticationInfo(anonymous);

        // restart interpreter with per user session enabled
        for (InterpreterSetting setting : note1.getBindedInterpreterSettings(new ArrayList<>())) {
          setting.getOption().setPerNote(InterpreterOption.SCOPED);
          try {
            notebook.getInterpreterSettingManager().restart(setting.getId());
          } catch (InterpreterException e) {
            fail();
          }
        }

        note1.run(p1.getId());
        while (p1.getStatus() != Status.FINISHED) Thread.yield();
        return p1.getReturn();
      });


    // remove note and recreate
    notebook.removeNote(note1Id, anonymous);
    note1Id = notebook.createNote("note1", anonymous);
    notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("%mock1 getId");
        p1.setAuthenticationInfo(anonymous);

        note1.run(p1.getId());
        while (p1.getStatus() != Status.FINISHED) Thread.yield();
        assertNotEquals(p1.getReturn().message(), result.message());
        return null;
      });

    notebook.removeNote(note1Id, anonymous);
  }

  @Test
  public void testPerSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    String note1Id = notebook.createNote("note1", anonymous);
    notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        String note2Id = notebook.createNote("note2", anonymous);
        notebook.processNote(note2Id,
          note2 -> {
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
              try {
                notebook.getInterpreterSettingManager().restart(setting.getId());
              } catch (InterpreterException e) {
                fail();
              }
            }

            // run per note session enabled
            note1.run(p1.getId());
            note2.run(p2.getId());

            while (p1.getStatus() != Status.FINISHED) Thread.yield();
            while (p2.getStatus() != Status.FINISHED) Thread.yield();

            assertNotEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());
            return null;
          });

        notebook.removeNote(note2Id, anonymous);
        return null;
      });

    notebook.removeNote(note1Id, anonymous);

  }


  @Test
  public void testPerNoteSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    String note1Id = notebook.createNote("note1", anonymous);
    notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

        String note2Id = notebook.createNote("note2", anonymous);
        notebook.processNote(note2Id,
          note2 -> {
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
              try {
                notebook.getInterpreterSettingManager().restart(setting.getId());
              } catch (InterpreterException e) {
                fail();
              }
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
              try {
                setting.getInterpreterSettingManager().restart(setting.getId());
              } catch (InterpreterException e) {
                fail();
              }
            }

            // run per note process enabled
            note1.run(p1.getId());
            note2.run(p2.getId());

            while (p1.getStatus() != Status.FINISHED) Thread.yield();
            while (p2.getStatus() != Status.FINISHED) Thread.yield();

            assertNotEquals(p1.getReturn().message().get(0).getData(), p2.getReturn().message().get(0).getData());
            return null;
          });
        notebook.removeNote(note2Id, anonymous);
        return null;
      });

    notebook.removeNote(note1Id, anonymous);

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

    String note1Id = notebook.createNote("note1", anonymous);
    assertEquals(1, onNoteCreate.get());

    notebook.processNote(note1Id,
      note1 -> {
        Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        assertEquals(1, onParagraphCreate.get());

        note1.addCloneParagraph(p1, AuthenticationInfo.ANONYMOUS);
        assertEquals(2, onParagraphCreate.get());

        note1.removeParagraph(anonymous.getUser(), p1.getId());
        assertEquals(1, onParagraphRemove.get());
        return null;
      });

    notebook.removeNote(note1Id, anonymous);
    assertEquals(1, onNoteRemove.get());
    assertEquals(1, onParagraphRemove.get());
  }

  @Test
  public void testGetAllNotes() throws Exception {
    String note1Id = notebook.createNote("note1", anonymous);
    String note2Id = notebook.createNote("note2", anonymous);
    assertEquals(2, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("anonymous")))).size());

    authorizationService.setOwners(note1Id, new HashSet<>(Arrays.asList("user1")));
    authorizationService.setWriters(note1Id, new HashSet<>(Arrays.asList("user1")));
    authorizationService.setRunners(note1Id, new HashSet<>(Arrays.asList("user1")));
    authorizationService.setReaders(note1Id, new HashSet<>(Arrays.asList("user1")));
    assertEquals(1, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("anonymous")))).size());
    assertEquals(2, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1")))).size());

    authorizationService.setOwners(note2Id, new HashSet<>(Arrays.asList("user2")));
    authorizationService.setWriters(note2Id, new HashSet<>(Arrays.asList("user2")));
    authorizationService.setReaders(note2Id, new HashSet<>(Arrays.asList("user2")));
    authorizationService.setRunners(note2Id, new HashSet<>(Arrays.asList("user2")));
    assertEquals(0, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("anonymous")))).size());
    assertEquals(1, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1")))).size());
    assertEquals(1, notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2")))).size());
    notebook.removeNote(note1Id, AuthenticationInfo.ANONYMOUS);
    notebook.removeNote(note2Id, AuthenticationInfo.ANONYMOUS);
  }

  @Test
  public void testCreateDuplicateNote() throws Exception {
    String note1Id = notebook.createNote("note1", anonymous);
    try {
      notebook.createNote("note1", anonymous);
      fail("Should not be able to create same note 'note1'");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Note '/note1' existed"));
    } finally {
      notebook.removeNote(note1Id, anonymous);
    }
  }

  @Test
  public void testGetAllNotesWithDifferentPermissions() throws IOException {
    List<NoteInfo> notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    List<NoteInfo> notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(0, notes1.size());
    assertEquals(0, notes2.size());

    //creates note and sets user1 owner
    String note1Id = notebook.createNote("note1", new AuthenticationInfo("user1"));

    // note is public since readers and writers empty
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setReaders(note1Id, new HashSet<>(Arrays.asList("user1")));
    //note is public since writers empty
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setRunners(note1Id, new HashSet<>(Arrays.asList("user1")));
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    authorizationService.setWriters(note1Id, new HashSet<>(Arrays.asList("user1")));
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(0, notes2.size());
  }

  @Test
  public void testPublicPrivateNewNote() throws IOException {
    // case of public note
    assertTrue(conf.isNotebookPublic());
    assertTrue(authorizationService.isPublic());

    List<NoteInfo> notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    List<NoteInfo> notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(0, notes1.size());
    assertEquals(0, notes2.size());

    // user1 creates note
    String notePublicId = notebook.createNote("note1", new AuthenticationInfo("user1"));

    // both users have note
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());
    assertEquals(notes1.get(0).getId(), notePublicId);
    assertEquals(notes2.get(0).getId(), notePublicId);

    // user1 is only owner
    assertEquals(1, authorizationService.getOwners(notePublicId).size());
    assertEquals(0, authorizationService.getReaders(notePublicId).size());
    assertEquals(0, authorizationService.getRunners(notePublicId).size());
    assertEquals(0, authorizationService.getWriters(notePublicId).size());

    // case of private note
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "false");
    ZeppelinConfiguration conf2 = ZeppelinConfiguration.create();
    assertFalse(conf2.isNotebookPublic());
    // notebook authorization reads from conf, so no need to re-initilize
    assertFalse(authorizationService.isPublic());

    // check that still 1 note per user
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(1, notes1.size());
    assertEquals(1, notes2.size());

    // create private note
    String notePrivateId = notebook.createNote("note2", new AuthenticationInfo("user1"));

    // only user1 have notePrivate right after creation
    notes1 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user1"))));
    notes2 = notebook.getNotesInfo(noteId -> authorizationService.isReader(noteId, new HashSet<>(Arrays.asList("user2"))));
    assertEquals(2, notes1.size());
    assertEquals(1, notes2.size());
    boolean found = false;
    for (NoteInfo info : notes1) {
      if (info.getId() == notePrivateId) {
        found = true;
        break;
      }
    }
    assertEquals(true, found);

    // user1 have all rights
    assertEquals(1, authorizationService.getOwners(notePrivateId).size());
    assertEquals(1, authorizationService.getReaders(notePrivateId).size());
    assertEquals(1, authorizationService.getRunners(notePrivateId).size());
    assertEquals(1, authorizationService.getWriters(notePrivateId).size());

    //set back public to true
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "true");
    ZeppelinConfiguration.create();
  }

  @Test
  public void testCloneImportCheck() throws IOException {
    String sourceNoteId = notebook.createNote("note1", new AuthenticationInfo("user"));
    notebook.processNote(sourceNoteId,
      sourceNote -> {
        sourceNote.setName("TestNote");

        assertEquals("TestNote",sourceNote.getName());

        Paragraph sourceParagraph = sourceNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        assertEquals("anonymous", sourceParagraph.getUser());
        String destNoteId = notebook.createNote("note2", new AuthenticationInfo("user"));
        notebook.processNote(destNoteId,
          destNote -> {
            destNote.setName("ClonedNote");
            assertEquals("ClonedNote",destNote.getName());

            List<Paragraph> paragraphs = sourceNote.getParagraphs();
            for (Paragraph p : paragraphs) {
              destNote.addCloneParagraph(p, AuthenticationInfo.ANONYMOUS);
              assertEquals("anonymous", p.getUser());
            }
            return null;
          });
        return null;
      });
  }

  @Test
  public void testMoveNote() throws InterruptedException, IOException {
    String noteId = null;
    try {
      noteId = notebook.createNote("note1", anonymous);
      notebook.processNote(noteId,
        note -> {
          assertEquals("note1", note.getName());
          assertEquals("/note1", note.getPath());
          return null;
        });

      notebook.moveNote(noteId, "/tmp/note2", anonymous);

      // read note json file to check the name field is updated
      File noteFile = notebook.processNote(noteId,
        note -> {
          return new File(conf.getNotebookDir() + "/" + notebookRepo.buildNoteFileName(note));
        });
      String noteJson = IOUtils.toString(new FileInputStream(noteFile), StandardCharsets.UTF_8);
      assertTrue(noteJson, noteJson.contains("note2"));
    } finally {
      if (noteId != null) {
        notebook.removeNote(noteId, anonymous);
      }
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


}
