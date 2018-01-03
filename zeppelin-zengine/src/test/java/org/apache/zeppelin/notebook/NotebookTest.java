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

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.AbstractInterpreterTest;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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

public class NotebookTest extends AbstractInterpreterTest implements JobListenerFactory {
  private static final Logger logger = LoggerFactory.getLogger(NotebookTest.class);

  private SchedulerFactory schedulerFactory;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private NotebookAuthorization notebookAuthorization;
  private Credentials credentials;
  private AuthenticationInfo anonymous = AuthenticationInfo.ANONYMOUS;
  private StatusChangedListener afterStatusChangedListener;

  @Before
  public void setUp() throws Exception {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "true");
    System.setProperty(ConfVars.ZEPPELIN_INTERPRETER_GROUP_ORDER.getVarName(), "mock1,mock2");
    super.setUp();

    schedulerFactory = SchedulerFactory.singleton();

    SearchService search = mock(SearchService.class);
    notebookRepo = new VFSNotebookRepo(conf);
    notebookAuthorization = NotebookAuthorization.init(conf);
    credentials = new Credentials(conf.credentialsPersist(), conf.getCredentialsPath(), null);

    notebook = new Notebook(conf, notebookRepo, schedulerFactory, interpreterFactory, interpreterSettingManager, this, search,
        notebookAuthorization, credentials);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSelectingReplImplementation() throws IOException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    // run with default repl
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());
    while(p1.isTerminated()==false || p1.getResult()==null) Thread.yield();
    assertEquals("repl1: hello world", p1.getResult().message().get(0).getData());

    // run with specific repl
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setConfig(config);
    p2.setText("%mock2 hello world");
    p2.setAuthenticationInfo(anonymous);
    note.run(p2.getId());
    while(p2.isTerminated()==false || p2.getResult()==null) Thread.yield();
    assertEquals("repl2: hello world", p2.getResult().message().get(0).getData());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testReloadAndSetInterpreter() throws IOException {
    // given a notebook
    File srcDir = new File("src/test/resources/2A94M5J1Z");
    File destDir = new File(notebookDir.getAbsolutePath() + "/2A94M5J1Z");
    FileUtils.copyDirectory(srcDir, destDir);

    // when load
    notebook.reloadAllNotes(anonymous);
    assertEquals(1, notebook.getAllNotes().size());

    // then interpreter factory should be injected into all the paragraphs
    Note note = notebook.getAllNotes().get(0);
    assertNull(note.getParagraphs().get(0).getBindedInterpreter());
  }

  @Test
  public void testReloadAllNotes() throws IOException {
    /**
     * 2A94M5J1Z old date format without timezone
     * 2BQA35CJZ new date format with timezone
     */
    String[] noteNames = new String[]{"2A94M5J1Z", "2BQA35CJZ"};

    // copy the notebook
    try {
      for (String note : noteNames) {
        File srcDir = new File("src/test/resources/" + note);
        File destDir = new File(notebookDir.getAbsolutePath() + "/" + note);
        FileUtils.copyDirectory(srcDir, destDir);
      }
    } catch (IOException e) {
      logger.error(e.toString(), e);
    }

    // doesn't have copied notebook in memory before reloading
    List<Note> notes = notebook.getAllNotes();
    assertEquals(notes.size(), 0);

    // load copied notebook on memory when reloadAllNotes() is called
    Note copiedNote = notebookRepo.get("2A94M5J1Z", anonymous);
    notebook.reloadAllNotes(anonymous);
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 2);
    assertEquals(notes.get(1).getId(), copiedNote.getId());
    assertEquals(notes.get(1).getName(), copiedNote.getName());
    // format has make some changes due to
    // Notebook.convertFromSingleResultToMultipleResultsFormat
    assertEquals(notes.get(1).getParagraphs().size(), copiedNote.getParagraphs().size());
    assertEquals(notes.get(1).getParagraphs().get(0).getText(),
        copiedNote.getParagraphs().get(0).getText());
    assertEquals(notes.get(1).getParagraphs().get(0).settings,
        copiedNote.getParagraphs().get(0).settings);
    assertEquals(notes.get(1).getParagraphs().get(0).getTitle(),
        copiedNote.getParagraphs().get(0).getTitle());

    // delete the notebook
    for (String note : noteNames) {
      File destDir = new File(notebookDir.getAbsolutePath() + "/" + note);
      FileUtils.deleteDirectory(destDir);
    }

    // keep notebook in memory before reloading
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 2);

    // delete notebook from notebook list when reloadAllNotes() is called
    notebook.reloadAllNotes(anonymous);
    notes = notebook.getAllNotes();
    assertEquals(notes.size(), 0);
  }

  @Test
  public void testLoadAllNotes() {
    Note note;
    try {
      assertEquals(0, notebook.getAllNotes().size());
      note = notebook.createNote(anonymous);
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = p1.getConfig();
      config.put("enabled", true);
      p1.setConfig(config);
      p1.setText("hello world");
      note.persist(anonymous);
    } catch (IOException fe) {
      logger.warn("Failed to create note and paragraph. Possible problem with persisting note, safe to ignore", fe);
    }

    try {
      notebook.loadAllNotes(anonymous);
      assertEquals(1, notebook.getAllNotes().size());
    } catch (IOException e) {
      fail("Subject is non-emtpy anonymous, shouldn't fail");
    }
  }
  
  @Test
  public void testPersist() throws IOException, SchedulerException, RepositoryException {
    Note note = notebook.createNote(anonymous);

    // run with default repl
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");
    note.persist(anonymous);

    Notebook notebook2 = new Notebook(
        conf, notebookRepo, schedulerFactory,
        new InterpreterFactory(interpreterSettingManager),
        interpreterSettingManager, null, null, null, null);

    assertEquals(1, notebook2.getAllNotes().size());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testCreateNoteWithSubject() throws IOException, SchedulerException, RepositoryException {
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note note = notebook.createNote(subject);

    assertNotNull(notebook.getNotebookAuthorization().getOwners(note.getId()));
    assertEquals(1, notebook.getNotebookAuthorization().getOwners(note.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, notebook.getNotebookAuthorization().getOwners(note.getId()));
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testClearParagraphOutput() throws IOException, SchedulerException{
    Note note = notebook.createNote(anonymous);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());

    while(p1.isTerminated() == false || p1.getResult() == null) Thread.yield();
    assertEquals("repl1: hello world", p1.getResult().message().get(0).getData());

    // clear paragraph output/result
    note.clearParagraphOutput(p1.getId());
    assertNull(p1.getResult());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testRunBlankParagraph() throws IOException, SchedulerException, InterruptedException {
    Note note = notebook.createNote(anonymous);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("");
    p1.setAuthenticationInfo(anonymous);
    note.run(p1.getId());

    Thread.sleep(2 * 1000);
    assertEquals(p1.getStatus(), Status.FINISHED);
    assertNull(p1.getDateStarted());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testRunAll() throws IOException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding("user", note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    // p1
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config1 = p1.getConfig();
    config1.put("enabled", true);
    p1.setConfig(config1);
    p1.setText("%mock1 p1");

    // p2
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config2 = p2.getConfig();
    config2.put("enabled", false);
    p2.setConfig(config2);
    p2.setText("%mock1 p2");

    // p3
    Paragraph p3 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p3.setText("%mock1 p3");

    // when
    note.runAll();

    assertEquals("repl1: p1", p1.getResult().message().get(0).getData());
    assertNull(p2.getResult());
    assertEquals("repl1: p3", p3.getResult().message().get(0).getData());

    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testSchedule() throws InterruptedException, IOException {
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding("user", note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = new HashMap<>();
    p.setConfig(config);
    p.setText("p1");
    Date dateFinished = p.getDateFinished();
    assertNull(dateFinished);

    // set cron scheduler, once a second
    config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);
    notebook.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    notebook.refreshCron(note.getId());
    Thread.sleep(2 * 1000);
    dateFinished = p.getDateFinished();
    assertNotNull(dateFinished);
    Thread.sleep(2 * 1000);
    assertEquals(dateFinished, p.getDateFinished());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testScheduleAgainstRunningAndPendingParagraph() throws InterruptedException, IOException {
    // create a note
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding("user", note.getId(),
            interpreterSettingManager.getInterpreterSettingIds());

    // append running and pending paragraphs to the note
    for (Status status: new Status[]{Status.RUNNING, Status.PENDING}) {
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      Map config = new HashMap<>();
      p.setConfig(config);
      p.setText("p");
      p.setStatus(status);
      assertNull(p.getDateFinished());
    }

    // set cron scheduler, once a second
    Map config = note.getConfig();
    config.put("enabled", true);
    config.put("cron", "* * * * * ?");
    note.setConfig(config);
    notebook.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // remove cron scheduler.
    config.put("cron", null);
    note.setConfig(config);
    notebook.refreshCron(note.getId());
    Thread.sleep(2 * 1000);

    // check if the executions of the running and pending paragraphs were skipped
    for (Paragraph p : note.paragraphs) {
      assertNull(p.getDateFinished());
    }

    // remove the note
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testSchedulePoolUsage() throws InterruptedException, IOException {
    final int timeout = 30;
    final String everySecondCron = "* * * * * ?";
    final CountDownLatch jobsToExecuteCount = new CountDownLatch(13);
    final Note note = notebook.createNote(anonymous);

    executeNewParagraphByCron(note, everySecondCron);
    afterStatusChangedListener = new StatusChangedListener() {
      @Override
      public void onStatusChanged(Job job, Status before, Status after) {
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
    notebook.refreshCron(note.getId());
  }

  private void terminateScheduledNote(Note note) {
    note.getConfig().remove("cron");
    notebook.refreshCron(note.getId());
    notebook.removeNote(note.getId(), anonymous);
  }

  
  @Test
  public void testAutoRestartInterpreterAfterSchedule() throws InterruptedException, IOException{
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map config = new HashMap<>();
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
    notebook.refreshCron(note.getId());


    RemoteInterpreter mock1 = (RemoteInterpreter) interpreterFactory.getInterpreter(anonymous.getUser(), note.getId(), "mock1");

    RemoteInterpreter mock2 = (RemoteInterpreter) interpreterFactory.getInterpreter(anonymous.getUser(), note.getId(), "mock2");

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
    notebook.refreshCron(note.getId());

    // make sure all paragraph has been executed
    assertNotNull(p.getDateFinished());
    assertNotNull(p2.getDateFinished());
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testCronWithReleaseResourceClosesOnlySpecificInterpreters()
          throws IOException, InterruptedException {
    // create a cron scheduled note.
    Note cronNote = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), cronNote.getId(),
            Arrays.asList(interpreterSettingManager.getInterpreterSettingByName("mock1").getId()));
    cronNote.setConfig(new HashMap() {
      {
        put("cron", "1/5 * * * * ?");
        put("cronExecutingUser", anonymous.getUser());
        put("releaseresource", true);
      }
    });
    RemoteInterpreter cronNoteInterpreter =
            (RemoteInterpreter) interpreterFactory.getInterpreter(anonymous.getUser(),
                    cronNote.getId(), "mock1");

    // create a paragraph of the cron scheduled note.
    Paragraph cronNoteParagraph = cronNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    cronNoteParagraph.setConfig(new HashMap() {
      { put("enabled", true); }
    });
    cronNoteParagraph.setText("%mock1 sleep 1000");

    // create another note
    Note anotherNote = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), anotherNote.getId(),
            Arrays.asList(interpreterSettingManager.getInterpreterSettingByName("mock2").getId()));
    RemoteInterpreter anotherNoteInterpreter =
            (RemoteInterpreter) interpreterFactory.getInterpreter(anonymous.getUser(),
                    anotherNote.getId(), "mock2");

    // create a paragraph of another note
    Paragraph anotherNoteParagraph = anotherNote.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    anotherNoteParagraph.setConfig(new HashMap() {
      { put("enabled", true); }
    });
    anotherNoteParagraph.setText("%mock2 echo 1");

    // run the paragraph of another note
    anotherNote.run(anotherNoteParagraph.getId());

    // wait until anotherNoteInterpreter is opened
    while (!anotherNoteInterpreter.isOpened()) {
      Thread.yield();
    }

    // refresh the cron schedule
    notebook.refreshCron(cronNote.getId());

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
    cronNote.setConfig(new HashMap() {
      {
        put("cron", null);
        put("cronExecutingUser", null);
        put("releaseresource", null);
      }
    });
    notebook.refreshCron(cronNote.getId());

    // remove notebooks
    notebook.removeNote(cronNote.getId(), anonymous);
    notebook.removeNote(anotherNote.getId(), anonymous);
  }

  @Test
  public void testExportAndImportNote() throws IOException, CloneNotSupportedException,
          InterruptedException, InterpreterException, SchedulerException, RepositoryException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding("user", note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    String simpleText = "hello world";
    p.setText(simpleText);

    note.runAll();

    String exportedNoteJson = notebook.exportNote(note.getId());

    Note importedNote = notebook.importNote(exportedNoteJson, "Title", anonymous);

    Paragraph p2 = importedNote.getParagraphs().get(0);

    // Test
    assertEquals(p.getId(), p2.getId());
    assertEquals(p.getText(), p2.getText());
    assertEquals(p.getResult().message().get(0).getData(), p2.getResult().message().get(0).getData());

    // Verify import note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note importedNote2 = notebook.importNote(exportedNoteJson, "Title2", subject);
    assertNotNull(notebook.getNotebookAuthorization().getOwners(importedNote2.getId()));
    assertEquals(1, notebook.getNotebookAuthorization().getOwners(importedNote2.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, notebook.getNotebookAuthorization().getOwners(importedNote2.getId()));
    notebook.removeNote(note.getId(), anonymous);
    notebook.removeNote(importedNote.getId(), anonymous);
    notebook.removeNote(importedNote2.getId(), anonymous);
  }

  @Test
  public void testCloneNote() throws IOException, CloneNotSupportedException,
      InterruptedException, InterpreterException, SchedulerException, RepositoryException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding("user", note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText("hello world");
    note.runAll();

    p.setStatus(Status.RUNNING);
    Note cloneNote = notebook.cloneNote(note.getId(), "clone note", anonymous);
    Paragraph cp = cloneNote.paragraphs.get(0);
    assertEquals(cp.getStatus(), Status.READY);

    // Keep same ParagraphId
    assertEquals(cp.getId(), p.getId());
    assertEquals(cp.getText(), p.getText());
    assertEquals(cp.getResult().message().get(0).getData(), p.getResult().message().get(0).getData());

    // Verify clone note with subject
    AuthenticationInfo subject = new AuthenticationInfo("user1");
    Note cloneNote2 = notebook.cloneNote(note.getId(), "clone note2", subject);
    assertNotNull(notebook.getNotebookAuthorization().getOwners(cloneNote2.getId()));
    assertEquals(1, notebook.getNotebookAuthorization().getOwners(cloneNote2.getId()).size());
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    assertEquals(owners, notebook.getNotebookAuthorization().getOwners(cloneNote2.getId()));
    notebook.removeNote(note.getId(), anonymous);
    notebook.removeNote(cloneNote.getId(), anonymous);
    notebook.removeNote(cloneNote2.getId(), anonymous);
  }

  @Test
  public void testCloneNoteWithNoName() throws IOException, CloneNotSupportedException,
      InterruptedException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    Note cloneNote = notebook.cloneNote(note.getId(), null, anonymous);
    assertEquals(cloneNote.getName(), "Note " + cloneNote.getId());
    notebook.removeNote(note.getId(), anonymous);
    notebook.removeNote(cloneNote.getId(), anonymous);
  }

  @Test
  public void testCloneNoteWithExceptionResult() throws IOException, CloneNotSupportedException,
      InterruptedException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    final Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText("hello world");
    note.runAll();

    // Force paragraph to have String type object
    p.setResult("Exception");

    Note cloneNote = notebook.cloneNote(note.getId(), "clone note with Exception result", anonymous);
    Paragraph cp = cloneNote.paragraphs.get(0);

    // Keep same ParagraphId
    assertEquals(cp.getId(), p.getId());
    assertEquals(cp.getText(), p.getText());
    assertNull(cp.getResult());
    notebook.removeNote(note.getId(), anonymous);
    notebook.removeNote(cloneNote.getId(), anonymous);
  }

  @Test
  public void testResourceRemovealOnParagraphNoteRemove() throws IOException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 hello");
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setText("%mock2 world");
    for (InterpreterGroup intpGroup : interpreterSettingManager.getAllInterpreterGroup()) {
      intpGroup.setResourcePool(new LocalResourcePool(intpGroup.getId()));
    }
    note.runAll();

    assertEquals(2, interpreterSettingManager.getAllResources().size());

    // remove a paragraph
    note.removeParagraph(anonymous.getUser(), p1.getId());
    assertEquals(1, interpreterSettingManager.getAllResources().size());

    // remove note
    notebook.removeNote(note.getId(), anonymous);
    assertEquals(0, interpreterSettingManager.getAllResources().size());
  }

  @Test
  public void testAngularObjectRemovalOnNotebookRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    AngularObjectRegistry registry = interpreterSettingManager
        .getInterpreterSettings(note.getId()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), "sharedProcess")
        .getAngularObjectRegistry();

    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    // add paragraph scope object
    registry.add("o1", "object1", note.getId(), p1.getId());

    // add notebook scope object
    registry.add("o2", "object2", note.getId(), null);

    // add global scope object
    registry.add("o3", "object3", null, null);

    // remove notebook
    notebook.removeNote(note.getId(), anonymous);

    // notebook scope or paragraph scope object should be removed
    assertNull(registry.get("o1", note.getId(), null));
    assertNull(registry.get("o2", note.getId(), p1.getId()));

    // global object sould be remained
    assertNotNull(registry.get("o3", null, null));
  }

  @Test
  public void testAngularObjectRemovalOnParagraphRemove() throws InterruptedException,
      IOException {
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    AngularObjectRegistry registry = interpreterSettingManager
        .getInterpreterSettings(note.getId()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), "sharedProcess")
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
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testAngularObjectRemovalOnInterpreterRestart() throws InterruptedException,
      IOException, InterpreterException {
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    AngularObjectRegistry registry = interpreterSettingManager
        .getInterpreterSettings(note.getId()).get(0).getOrCreateInterpreterGroup(anonymous.getUser(), "sharedProcess")
        .getAngularObjectRegistry();

    // add local scope object
    registry.add("o1", "object1", note.getId(), null);
    // add global scope object
    registry.add("o2", "object2", null, null);

    // restart interpreter
    interpreterSettingManager.restart(interpreterSettingManager.getInterpreterSettings(note.getId()).get(0).getId());
    registry = interpreterSettingManager.getInterpreterSettings(note.getId()).get(0)
        .getOrCreateInterpreterGroup(anonymous.getUser(), "sharedProcess")
        .getAngularObjectRegistry();

    // New InterpreterGroup will be created and its AngularObjectRegistry will be created
    assertNull(registry.get("o1", note.getId(), null));
    assertNull(registry.get("o2", null, null));
    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testPermissions() throws IOException {
    // create a note and a paragraph
    Note note = notebook.createNote(anonymous);
    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    // empty owners, readers or writers means note is public
    assertEquals(notebookAuthorization.isOwner(note.getId(),
            new HashSet<>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isReader(note.getId(),
            new HashSet<>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isRunner(note.getId(),
            new HashSet<>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isWriter(note.getId(),
            new HashSet<>(Arrays.asList("user2"))), true);

    notebookAuthorization.setOwners(note.getId(),
            new HashSet<>(Arrays.asList("user1")));
    notebookAuthorization.setReaders(note.getId(),
            new HashSet<>(Arrays.asList("user1", "user2")));
      notebookAuthorization.setRunners(note.getId(),
              new HashSet<>(Arrays.asList("user3")));
    notebookAuthorization.setWriters(note.getId(),
            new HashSet<>(Arrays.asList("user1")));

    assertEquals(notebookAuthorization.isOwner(note.getId(),
        new HashSet<>(Arrays.asList("user2"))), false);
    assertEquals(notebookAuthorization.isOwner(note.getId(),
            new HashSet<>(Arrays.asList("user1"))), true);

    assertEquals(notebookAuthorization.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user4"))), false);
    assertEquals(notebookAuthorization.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user2"))), true);

    assertEquals(notebookAuthorization.isRunner(note.getId(),
            new HashSet<>(Arrays.asList("user3"))), true);
    assertEquals(notebookAuthorization.isRunner(note.getId(),
            new HashSet<>(Arrays.asList("user2"))), false);

    assertEquals(notebookAuthorization.isWriter(note.getId(),
        new HashSet<>(Arrays.asList("user2"))), false);
    assertEquals(notebookAuthorization.isWriter(note.getId(),
        new HashSet<>(Arrays.asList("user1"))), true);

    // Test clearing of permissions
    notebookAuthorization.setReaders(note.getId(), Sets.<String>newHashSet());
    assertEquals(notebookAuthorization.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user2"))), true);
    assertEquals(notebookAuthorization.isReader(note.getId(),
        new HashSet<>(Arrays.asList("user4"))), true);

    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testAuthorizationRoles() throws IOException {
    String user1 = "user1";
    String user2 = "user2";
    Set<String> roles = Sets.newHashSet("admin");
    // set admin roles for both user1 and user2
    notebookAuthorization.setRoles(user1, roles);
    notebookAuthorization.setRoles(user2, roles);
    
    Note note = notebook.createNote(new AuthenticationInfo(user1));
    
    // check that user1 is owner, reader, runner and writer
    assertEquals(notebookAuthorization.isOwner(note.getId(),
        Sets.newHashSet(user1)), true);
    assertEquals(notebookAuthorization.isReader(note.getId(),
        Sets.newHashSet(user1)), true);
    assertEquals(notebookAuthorization.isRunner(note.getId(),
        Sets.newHashSet(user2)), true);
    assertEquals(notebookAuthorization.isWriter(note.getId(),
        Sets.newHashSet(user1)), true);
    
    // since user1 and user2 both have admin role, user2 will be reader and writer as well
    assertEquals(notebookAuthorization.isOwner(note.getId(),
        Sets.newHashSet(user2)), false);
    assertEquals(notebookAuthorization.isReader(note.getId(),
        Sets.newHashSet(user2)), true);
    assertEquals(notebookAuthorization.isRunner(note.getId(),
        Sets.newHashSet(user2)), true);
    assertEquals(notebookAuthorization.isWriter(note.getId(),
        Sets.newHashSet(user2)), true);
    
    // check that user1 has note listed in his workbench
    Set<String> user1AndRoles = notebookAuthorization.getRoles(user1);
    user1AndRoles.add(user1);
    List<Note> user1Notes = notebook.getAllNotes(user1AndRoles);
    assertEquals(user1Notes.size(), 1);
    assertEquals(user1Notes.get(0).getId(), note.getId());
    
    // check that user2 has note listed in his workbench because of admin role
    Set<String> user2AndRoles = notebookAuthorization.getRoles(user2);
    user2AndRoles.add(user2);
    List<Note> user2Notes = notebook.getAllNotes(user2AndRoles);
    assertEquals(user2Notes.size(), 1);
    assertEquals(user2Notes.get(0).getId(), note.getId());
  }
  
  @Test
  public void testAbortParagraphStatusOnInterpreterRestart() throws InterruptedException,
      IOException, InterpreterException {
    Note note = notebook.createNote(anonymous);
    interpreterSettingManager.setInterpreterBinding(anonymous.getUser(), note.getId(), interpreterSettingManager.getInterpreterSettingIds());

    // create three paragraphs
    Paragraph p1 = note.addNewParagraph(anonymous);
    p1.setText("%mock1 sleep 1000");
    Paragraph p2 = note.addNewParagraph(anonymous);
    p2.setText("%mock1 sleep 1000");
    Paragraph p3 = note.addNewParagraph(anonymous);
    p3.setText("%mock1 sleep 1000");


    note.runAll(AuthenticationInfo.ANONYMOUS, false);

    // wait until first paragraph finishes and second paragraph starts
    while (p1.getStatus() != Status.FINISHED || p2.getStatus() != Status.RUNNING) Thread.yield();

    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.RUNNING, p2.getStatus());
    assertEquals(Status.PENDING, p3.getStatus());

    // restart interpreter
    interpreterSettingManager.restart(interpreterSettingManager.getInterpreterSettingByName("mock1").getId());

    // make sure three different status aborted well.
    assertEquals(Status.FINISHED, p1.getStatus());
    assertEquals(Status.ABORT, p2.getStatus());
    assertEquals(Status.ABORT, p3.getStatus());

    notebook.removeNote(note.getId(), anonymous);
  }

  @Test
  public void testPerSessionInterpreterCloseOnNoteRemoval() throws IOException, InterpreterException {
    // create a notes
    Note note1  = notebook.createNote(anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);

    // restart interpreter with per user session enabled
    for (InterpreterSetting setting : interpreterSettingManager.getInterpreterSettings(note1.getId())) {
      setting.getOption().setPerNote(setting.getOption().SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    InterpreterResult result = p1.getResult();

    // remove note and recreate
    notebook.removeNote(note1.getId(), anonymous);
    note1 = notebook.createNote(anonymous);
    p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%mock1 getId");
    p1.setAuthenticationInfo(anonymous);

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    assertNotEquals(p1.getResult().message(), result.message());

    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testPerSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    Note note1  = notebook.createNote(anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Note note2  = notebook.createNote(anonymous);
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

    assertEquals(p1.getResult().message().get(0).getData(), p2.getResult().message().get(0).getData());


    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : notebook.getInterpreterSettingManager().getInterpreterSettings(note1.getId())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note session enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getResult().message(), p2.getResult().message().get(0).getData());

    notebook.removeNote(note1.getId(), anonymous);
    notebook.removeNote(note2.getId(), anonymous);
  }


  @Test
  public void testPerNoteSessionInterpreter() throws IOException, InterpreterException {
    // create two notes
    Note note1  = notebook.createNote(anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    Note note2  = notebook.createNote(anonymous);
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

    assertEquals(p1.getResult().message().get(0).getData(), p2.getResult().message().get(0).getData());

    // restart interpreter with scoped mode enabled
    for (InterpreterSetting setting : notebook.getInterpreterSettingManager().getInterpreterSettings(note1.getId())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note session enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getResult().message().get(0).getData(), p2.getResult().message().get(0).getData());

    // restart interpreter with isolated mode enabled
    for (InterpreterSetting setting : notebook.getInterpreterSettingManager().getInterpreterSettings(note1.getId())) {
      setting.getOption().setPerNote(InterpreterOption.ISOLATED);
      setting.getInterpreterSettingManager().restart(setting.getId());
    }

    // run per note process enabled
    note1.run(p1.getId());
    note2.run(p2.getId());

    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    while (p2.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(p1.getResult().message().get(0).getData(), p2.getResult().message().get(0).getData());

    notebook.removeNote(note1.getId(), anonymous);
    notebook.removeNote(note2.getId(), anonymous);
  }

  @Test
  public void testPerSessionInterpreterCloseOnUnbindInterpreterSetting() throws IOException, InterpreterException {
    // create a notes
    Note note1  = notebook.createNote(anonymous);
    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setAuthenticationInfo(anonymous);
    p1.setText("%mock1 getId");

    // restart interpreter with per note session enabled
    for (InterpreterSetting setting : interpreterSettingManager.getInterpreterSettings(note1.getId())) {
      setting.getOption().setPerNote(InterpreterOption.SCOPED);
      notebook.getInterpreterSettingManager().restart(setting.getId());
    }

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();
    InterpreterResult result = p1.getResult();


    // unbind, and rebind setting. that result interpreter instance close
    List<String> bindedSettings = notebook.getBindedInterpreterSettingsIds(note1.getId());
    notebook.bindInterpretersToNote(anonymous.getUser(), note1.getId(), new LinkedList<String>());
    notebook.bindInterpretersToNote(anonymous.getUser(), note1.getId(), bindedSettings);

    note1.run(p1.getId());
    while (p1.getStatus() != Status.FINISHED) Thread.yield();

    assertNotEquals(result.message().get(0).getData(), p1.getResult().message().get(0).getData());

    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testNotebookEventListener() throws IOException {
    final AtomicInteger onNoteRemove = new AtomicInteger(0);
    final AtomicInteger onNoteCreate = new AtomicInteger(0);
    final AtomicInteger onParagraphRemove = new AtomicInteger(0);
    final AtomicInteger onParagraphCreate = new AtomicInteger(0);
    final AtomicInteger unbindInterpreter = new AtomicInteger(0);

    notebook.addNotebookEventListener(new NotebookEventListener() {
      @Override
      public void onNoteRemove(Note note) {
        onNoteRemove.incrementAndGet();
      }

      @Override
      public void onNoteCreate(Note note) {
        onNoteCreate.incrementAndGet();
      }

      @Override
      public void onUnbindInterpreter(Note note, InterpreterSetting setting) {
        unbindInterpreter.incrementAndGet();
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
      public void onParagraphStatusChange(Paragraph p, Status status) {
      }
    });

    Note note1 = notebook.createNote(anonymous);
    assertEquals(1, onNoteCreate.get());

    Paragraph p1 = note1.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, onParagraphCreate.get());

    note1.addCloneParagraph(p1);
    assertEquals(2, onParagraphCreate.get());

    note1.removeParagraph(anonymous.getUser(), p1.getId());
    assertEquals(1, onParagraphRemove.get());

    List<String> settings = notebook.getBindedInterpreterSettingsIds(note1.getId());
    notebook.bindInterpretersToNote(anonymous.getUser(), note1.getId(), new LinkedList<String>());
    assertEquals(settings.size(), unbindInterpreter.get());

    notebook.removeNote(note1.getId(), anonymous);
    assertEquals(1, onNoteRemove.get());
    assertEquals(1, onParagraphRemove.get());
  }

  @Test
  public void testNormalizeNoteName() throws IOException {
    // create a notes
    Note note1  = notebook.createNote(anonymous);

    note1.setName("MyNote");
    assertEquals(note1.getName(), "MyNote");

    note1.setName("/MyNote");
    assertEquals(note1.getName(), "/MyNote");

    note1.setName("MyNote/sub");
    assertEquals(note1.getName(), "MyNote/sub");

    note1.setName("/MyNote/sub");
    assertEquals(note1.getName(), "/MyNote/sub");

    note1.setName("///////MyNote//////sub");
    assertEquals(note1.getName(), "/MyNote/sub");

    note1.setName("\\\\\\MyNote///sub");
    assertEquals(note1.getName(), "/MyNote/sub");

    notebook.removeNote(note1.getId(), anonymous);
  }

  @Test
  public void testGetAllNotes() throws Exception {
    Note note1 = notebook.createNote(anonymous);
    Note note2 = notebook.createNote(anonymous);
    assertEquals(2, notebook.getAllNotes(Sets.newHashSet("anonymous")).size());

    notebook.getNotebookAuthorization().setOwners(note1.getId(), Sets.newHashSet("user1"));
    notebook.getNotebookAuthorization().setWriters(note1.getId(), Sets.newHashSet("user1"));
    notebook.getNotebookAuthorization().setRunners(note1.getId(), Sets.newHashSet("user1"));
    notebook.getNotebookAuthorization().setReaders(note1.getId(), Sets.newHashSet("user1"));
    assertEquals(1, notebook.getAllNotes(Sets.newHashSet("anonymous")).size());
    assertEquals(2, notebook.getAllNotes(Sets.newHashSet("user1")).size());

    notebook.getNotebookAuthorization().setOwners(note2.getId(), Sets.newHashSet("user2"));
    notebook.getNotebookAuthorization().setWriters(note2.getId(), Sets.newHashSet("user2"));
    notebook.getNotebookAuthorization().setReaders(note2.getId(), Sets.newHashSet("user2"));
      notebook.getNotebookAuthorization().setRunners(note2.getId(), Sets.newHashSet("user2"));
    assertEquals(0, notebook.getAllNotes(Sets.newHashSet("anonymous")).size());
    assertEquals(1, notebook.getAllNotes(Sets.newHashSet("user1")).size());
    assertEquals(1, notebook.getAllNotes(Sets.newHashSet("user2")).size());
    notebook.removeNote(note1.getId(), anonymous);
    notebook.removeNote(note2.getId(), anonymous);
  }


  @Test
  public void testGetAllNotesWithDifferentPermissions() throws IOException {
    HashSet<String> user1 = Sets.newHashSet("user1");
    HashSet<String> user2 = Sets.newHashSet("user1");
    List<Note> notes1 = notebook.getAllNotes(user1);
    List<Note> notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 0);
    assertEquals(notes2.size(), 0);

    //creates note and sets user1 owner
    Note note = notebook.createNote(new AuthenticationInfo("user1"));

    // note is public since readers and writers empty
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);
    
    notebook.getNotebookAuthorization().setReaders(note.getId(), Sets.newHashSet("user1"));
    //note is public since writers empty
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);

    notebook.getNotebookAuthorization().setRunners(note.getId(), Sets.newHashSet("user1"));
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);
    
    notebook.getNotebookAuthorization().setWriters(note.getId(), Sets.newHashSet("user1"));
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);
  }

  @Test
  public void testPublicPrivateNewNote() throws IOException, SchedulerException {
    HashSet<String> user1 = Sets.newHashSet("user1");
    HashSet<String> user2 = Sets.newHashSet("user2");
    
    // case of public note
    assertTrue(conf.isNotebookPublic());
    assertTrue(notebookAuthorization.isPublic());
    
    List<Note> notes1 = notebook.getAllNotes(user1);
    List<Note> notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 0);
    assertEquals(notes2.size(), 0);
    
    // user1 creates note
    Note notePublic = notebook.createNote(new AuthenticationInfo("user1"));
    
    // both users have note
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);
    assertEquals(notes1.get(0).getId(), notePublic.getId());
    assertEquals(notes2.get(0).getId(), notePublic.getId());
    
    // user1 is only owner
    assertEquals(notebookAuthorization.getOwners(notePublic.getId()).size(), 1);
    assertEquals(notebookAuthorization.getReaders(notePublic.getId()).size(), 0);
    assertEquals(notebookAuthorization.getRunners(notePublic.getId()).size(), 0);
    assertEquals(notebookAuthorization.getWriters(notePublic.getId()).size(), 0);

    // case of private note
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "false");
    ZeppelinConfiguration conf2 = ZeppelinConfiguration.create();
    assertFalse(conf2.isNotebookPublic());
    // notebook authorization reads from conf, so no need to re-initilize
    assertFalse(notebookAuthorization.isPublic());
    
    // check that still 1 note per user
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 1);
    assertEquals(notes2.size(), 1);
    
    // create private note
    Note notePrivate = notebook.createNote(new AuthenticationInfo("user1"));

    // only user1 have notePrivate right after creation
    notes1 = notebook.getAllNotes(user1);
    notes2 = notebook.getAllNotes(user2);
    assertEquals(notes1.size(), 2);
    assertEquals(notes2.size(), 1);
    assertEquals(true, notes1.contains(notePrivate));
    
    // user1 have all rights
    assertEquals(notebookAuthorization.getOwners(notePrivate.getId()).size(), 1);
    assertEquals(notebookAuthorization.getReaders(notePrivate.getId()).size(), 1);
    assertEquals(notebookAuthorization.getRunners(notePrivate.getId()).size(), 1);
    assertEquals(notebookAuthorization.getWriters(notePrivate.getId()).size(), 1);
    
    //set back public to true
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_PUBLIC.getVarName(), "true");
    ZeppelinConfiguration.create();
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
      public void beforeStatusChange(Job job, Status before, Status after) {
      }

      @Override
      public void afterStatusChange(Job job, Status before, Status after) {
        if (afterStatusChangedListener != null) {
          afterStatusChangedListener.onStatusChanged(job, before, after);
        }
      }
    };
  }

  private interface StatusChangedListener {
    void onStatusChanged(Job job, Status before, Status after);
  }
}
