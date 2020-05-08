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


package org.apache.zeppelin.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.io.Files;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class NotebookServiceTest {

  private static NotebookService notebookService;

  private File notebookDir;
  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  private Gson gson = new Gson();


  @Before
  public void setUp() throws Exception {
    notebookDir = Files.createTempDir().getAbsoluteFile();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
            notebookDir.getAbsolutePath());
    ZeppelinConfiguration zeppelinConfiguration = ZeppelinConfiguration.create();
    NotebookRepo notebookRepo = new VFSNotebookRepo();
    notebookRepo.init(zeppelinConfiguration);

    InterpreterSettingManager mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterFactory mockInterpreterFactory = mock(InterpreterFactory.class);
    Interpreter mockInterpreter = mock(Interpreter.class);
    when(mockInterpreterFactory.getInterpreter(any(), any()))
        .thenReturn(mockInterpreter);
    when(mockInterpreter.interpret(eq("invalid_code"), any()))
        .thenReturn(new InterpreterResult(Code.ERROR, "failed"));
    when(mockInterpreter.interpret(eq("1+1"), any()))
        .thenReturn(new InterpreterResult(Code.SUCCESS, "succeed"));
    doCallRealMethod().when(mockInterpreter).getScheduler();
    when(mockInterpreter.getFormType()).thenReturn(FormType.NATIVE);
    ManagedInterpreterGroup mockInterpreterGroup = mock(ManagedInterpreterGroup.class);
    when(mockInterpreter.getInterpreterGroup()).thenReturn(mockInterpreterGroup);
    InterpreterSetting mockInterpreterSetting = mock(InterpreterSetting.class);
    when(mockInterpreterSetting.isUserAuthorized(any())).thenReturn(true);
    when(mockInterpreterGroup.getInterpreterSetting()).thenReturn(mockInterpreterSetting);
    when(mockInterpreterSetting.getStatus()).thenReturn(InterpreterSetting.Status.READY);
    SearchService searchService = new LuceneSearch(zeppelinConfiguration);
    Credentials credentials = new Credentials();
    NoteManager noteManager = new NoteManager(notebookRepo);
    AuthorizationService authorizationService = new AuthorizationService(noteManager, zeppelinConfiguration);
    Notebook notebook =
        new Notebook(
            zeppelinConfiguration,
            authorizationService,
            notebookRepo,
            noteManager,
            mockInterpreterFactory,
            mockInterpreterSettingManager,
            searchService,
            credentials,
            null);

    QuartzSchedulerService schedulerService = new QuartzSchedulerService(zeppelinConfiguration, notebook);
    schedulerService.waitForFinishInit();
    notebookService =
        new NotebookService(
            notebook, authorizationService, zeppelinConfiguration, schedulerService);

    String interpreterName = "test";
    when(mockInterpreterSetting.getName()).thenReturn(interpreterName);
    when(mockInterpreterSettingManager.getDefaultInterpreterSetting())
        .thenReturn(mockInterpreterSetting);
  }

  @After
  public void tearDown() {
    notebookDir.delete();
  }

  @Test
  public void testNoteOperations() throws IOException {
    // get home note
    Note homeNote = notebookService.getHomeNote(context, callback);
    assertNull(homeNote);
    verify(callback).onSuccess(homeNote, context);

    // create note
    Note note1 = notebookService.createNote("/folder_1/note1", "test", context, callback);
    assertEquals("note1", note1.getName());
    assertEquals(1, note1.getParagraphCount());
    verify(callback).onSuccess(note1, context);

    // create duplicated note
    reset(callback);
    Note note2 = notebookService.createNote("/folder_1/note1", "test", context, callback);
    assertNull(note2);
    ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
    verify(callback).onFailure(exception.capture(), any(ServiceContext.class));
    assertTrue(exception.getValue().getMessage().equals("Note '/folder_1/note1' existed"));

    // list note
    reset(callback);
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(1, notesInfo.size());
    assertEquals(note1.getId(), notesInfo.get(0).getId());
    assertEquals(note1.getName(), notesInfo.get(0).getNoteName());
    verify(callback).onSuccess(notesInfo, context);

    // get note
    reset(callback);
    Note note1_copy = notebookService.getNote(note1.getId(), context, callback);
    assertEquals(note1, note1_copy);
    verify(callback).onSuccess(note1_copy, context);

    // rename note
    reset(callback);
    notebookService.renameNote(note1.getId(), "/folder_2/new_name", false, context, callback);
    verify(callback).onSuccess(note1, context);
    assertEquals("new_name", note1.getName());

    // move folder
    reset(callback);
    notesInfo = notebookService.renameFolder("/folder_2", "/folder_3", context, callback);
    verify(callback).onSuccess(notesInfo, context);
    assertEquals(1, notesInfo.size());
    assertEquals("/folder_3/new_name", notesInfo.get(0).getPath());

    // move folder in case of folder path without prefix '/'
    reset(callback);
    notesInfo = notebookService.renameFolder("folder_3", "folder_4", context, callback);
    verify(callback).onSuccess(notesInfo, context);
    assertEquals(1, notesInfo.size());
    assertEquals("/folder_4/new_name", notesInfo.get(0).getPath());

    // create another note
    note2 = notebookService.createNote("/note2", "test", context, callback);
    assertEquals("note2", note2.getName());
    verify(callback).onSuccess(note2, context);

    // rename note
    reset(callback);
    notebookService.renameNote(note2.getId(), "new_note2", true, context, callback);
    verify(callback).onSuccess(note2, context);
    assertEquals("new_note2", note2.getName());

    // list note
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // delete note
    reset(callback);
    notebookService.removeNote(note2.getId(), context, callback);
    verify(callback).onSuccess("Delete note successfully", context);

    // list note again
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(1, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // delete folder
    notesInfo = notebookService.removeFolder("/folder_4", context, callback);
    verify(callback).onSuccess(notesInfo, context);

    // list note again
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(0, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // import note
    reset(callback);
    Note importedNote = notebookService.importNote("/Imported Note", "{}", context, callback);
    assertNotNull(importedNote);
    verify(callback).onSuccess(importedNote, context);

    // clone note
    reset(callback);
    Note clonedNote = notebookService.cloneNote(importedNote.getId(), "/Backup/Cloned Note",
        context, callback);
    assertEquals(importedNote.getParagraphCount(), clonedNote.getParagraphCount());
    verify(callback).onSuccess(clonedNote, context);

    // list note
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // move note to Trash
    notebookService.moveNoteToTrash(importedNote.getId(), context, callback);

    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    boolean moveToTrash = false;
    for (NoteInfo noteInfo : notesInfo) {
      if (noteInfo.getId().equals(importedNote.getId())) {
        assertEquals("/~Trash/Imported Note", noteInfo.getPath());
        moveToTrash = true;
      }
    }
    assertTrue("No note is moved to trash", moveToTrash);

    // restore it
    notebookService.restoreNote(importedNote.getId(), context, callback);
    Note restoredNote = notebookService.getNote(importedNote.getId(), context, callback);
    assertNotNull(restoredNote);
    assertEquals("/Imported Note", restoredNote.getPath());

    // move it to Trash again
    notebookService.moveNoteToTrash(restoredNote.getId(), context, callback);

    // remove note from Trash
    reset(callback);

    notebookService.removeNote(importedNote.getId(), context, callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(1, notesInfo.size());

    // move folder to Trash
    notebookService.moveFolderToTrash("Backup", context, callback);

    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(1, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);
    moveToTrash = false;
    for (NoteInfo noteInfo : notesInfo) {
      if (noteInfo.getId().equals(clonedNote.getId())) {
        assertEquals("/~Trash/Backup/Cloned Note", noteInfo.getPath());
        moveToTrash = true;
      }
    }
    assertTrue("No folder is moved to trash", moveToTrash);

    // restore folder
    reset(callback);
    notebookService.restoreFolder("/~Trash/Backup", context, callback);
    restoredNote = notebookService.getNote(clonedNote.getId(), context, callback);
    assertNotNull(restoredNote);
    assertEquals("/Backup/Cloned Note", restoredNote.getPath());

    // move the folder to trash again
    notebookService.moveFolderToTrash("Backup", context, callback);

    // remove folder from Trash
    reset(callback);
    notebookService.removeFolder("/~Trash/Backup", context, callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(0, notesInfo.size());

    // empty trash
    notebookService.emptyTrash(context, callback);

    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(0, notesInfo.size());
  }

  @Test
  public void testParagraphOperations() throws IOException {
    // create note
    Note note1 = notebookService.createNote("note1", "python", context, callback);
    assertEquals("note1", note1.getName());
    assertEquals(1, note1.getParagraphCount());
    verify(callback).onSuccess(note1, context);

    // add paragraph
    reset(callback);
    Paragraph p = notebookService.insertParagraph(note1.getId(), 1, new HashMap<>(), context,
        callback);
    assertNotNull(p);
    verify(callback).onSuccess(p, context);
    assertEquals(2, note1.getParagraphCount());

    // update paragraph
    reset(callback);
    notebookService.updateParagraph(note1.getId(), p.getId(), "my_title", "my_text",
        new HashMap<>(), new HashMap<>(), context, callback);
    assertEquals("my_title", p.getTitle());
    assertEquals("my_text", p.getText());

    // move paragraph
    reset(callback);
    notebookService.moveParagraph(note1.getId(), p.getId(), 0, context, callback);
    assertEquals(p, note1.getParagraph(0));
    verify(callback).onSuccess(p, context);

    // run paragraph asynchronously
    reset(callback);
    p.getConfig().put("colWidth", "6.0");
    p.getConfig().put("title", true);
    boolean runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "1+1",
        new HashMap<>(), new HashMap<>(), false, false, context, callback);
    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);
    assertEquals(2, p.getConfig().size());

    // run paragraph synchronously via correct code
    reset(callback);
    runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "1+1",
        new HashMap<>(), new HashMap<>(), false, true, context, callback);
    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);
    assertEquals(2, p.getConfig().size());

    // run all paragraphs, with null paragraph list provided
    reset(callback);
    assertTrue(notebookService.runAllParagraphs(
            note1.getId(),
            null,
            context, callback));

    reset(callback);
    runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "invalid_code",
        new HashMap<>(), new HashMap<>(), false, true, context, callback);
    assertTrue(runStatus);
    // TODO(zjffdu) Enable it after ZEPPELIN-3699
    // assertNotNull(p.getResult());
    verify(callback).onSuccess(p, context);

    // clean output
    reset(callback);
    notebookService.clearParagraphOutput(note1.getId(), p.getId(), context, callback);
    assertNull(p.getReturn());
    verify(callback).onSuccess(p, context);
  }

  @Test
  public void testNormalizeNotePath() throws IOException {
    assertEquals("/Untitled Note", notebookService.normalizeNotePath(" "));
    assertEquals("/Untitled Note", notebookService.normalizeNotePath(null));
    assertEquals("/my_note", notebookService.normalizeNotePath("my_note"));
    assertEquals("/my  note", notebookService.normalizeNotePath("my\r\nnote"));

    try {
      String longNoteName = StringUtils.join(
          IntStream.range(0, 256).boxed().collect(Collectors.toList()), "");
      notebookService.normalizeNotePath(longNoteName);
      fail("Should fail");
    } catch (IOException e) {
      assertEquals("Note name must be less than 255", e.getMessage());
    }
    try {
      notebookService.normalizeNotePath("my..note");
      fail("Should fail");
    } catch (IOException e) {
      assertEquals("Note name can not contain '..'", e.getMessage());
    }
  }
}
