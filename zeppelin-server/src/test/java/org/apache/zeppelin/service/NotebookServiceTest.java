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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import org.apache.zeppelin.notebook.exception.NotePathAlreadyExistsException;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.Gson;

public class NotebookServiceTest {

  private static NotebookService notebookService;

  private File notebookDir;
  private SearchService searchService;
  private Notebook notebook;
  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  private Gson gson = new Gson();


  @Before
  public void setUp() throws Exception {
    notebookDir = Files.createTempDirectory("notebookDir").toAbsolutePath().toFile();
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
    Credentials credentials = new Credentials();
    NoteManager noteManager = new NoteManager(notebookRepo, zeppelinConfiguration);
    AuthorizationService authorizationService = new AuthorizationService(noteManager, zeppelinConfiguration);
    notebook =
        new Notebook(
            zeppelinConfiguration,
            authorizationService,
            notebookRepo,
            noteManager,
            mockInterpreterFactory,
            mockInterpreterSettingManager,
            credentials,
            null);
    searchService = new LuceneSearch(zeppelinConfiguration, notebook);
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
    searchService.close();
  }

  @Test
  public void testNoteOperations() throws IOException {
    // get home note
    String homeNoteId = notebookService.getHomeNote(context, callback);
    assertNull(homeNoteId);
    notebook.processNote(homeNoteId,
      homeNote -> {
        verify(callback).onSuccess(homeNote, context);
        return null;
      });

    // create note
    String note1Id = notebookService.createNote("/folder_1/note1", "test", true, context, callback);
    Note note1 = notebook.processNote(note1Id,
      note1Read -> {
        assertEquals("note1", note1Read.getName());
        assertEquals(1, note1Read.getParagraphCount());
        verify(callback).onSuccess(note1Read, context);
        return note1Read;
      });


    // create duplicated note
    reset(callback);
    String note2Id = notebookService.createNote("/folder_1/note1", "test", true, context, callback);
    assertNull(note2Id);
    ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
    verify(callback).onFailure(exception.capture(), any(ServiceContext.class));
    assertEquals("Note '/folder_1/note1' existed", exception.getValue().getMessage());

    // list note
    reset(callback);
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(1, notesInfo.size());
    assertEquals(note1Id, notesInfo.get(0).getId());
    assertEquals(note1.getName(), notesInfo.get(0).getNoteName());
    verify(callback).onSuccess(notesInfo, context);

    // get note
    reset(callback);
    notebookService.getNote(note1.getId(), context, callback,
      note1_copy -> {
        assertEquals(note1, note1_copy);
        verify(callback).onSuccess(note1_copy, context);
        return null;
      });


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
    note2Id = notebookService.createNote("/note2", "test", true, context, callback);
    notebook.processNote(note2Id,
      note2 -> {
        assertEquals("note2", note2.getName());
        verify(callback).onSuccess(note2, context);
        return null;
      });

    // rename note
    reset(callback);
    notebookService.renameNote(note2Id, "new_note2", true, context, callback);
    notebook.processNote(note2Id,
      note2 -> {
        verify(callback).onSuccess(note2, context);
        assertEquals("new_note2", note2.getName());
        return null;
      });

    // list note
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // delete note
    reset(callback);
    notebookService.removeNote(note2Id, context, callback);
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
    String importedNoteId = notebookService.importNote("/Imported Note", "{}", context, callback);
    notebook.processNote(importedNoteId,
      importedNote -> {
        assertNotNull(importedNote);
        verify(callback).onSuccess(importedNote, context);
        return null;
      });

    // clone note
    reset(callback);
    String clonedNoteId = notebookService.cloneNote(importedNoteId, "/Backup/Cloned Note",
        context, callback);
    notebook.processNote(importedNoteId,
      importedNote -> {
        notebook.processNote(clonedNoteId,
          clonedNote -> {
            assertEquals(importedNote.getParagraphCount(), clonedNote.getParagraphCount());
            verify(callback).onSuccess(clonedNote, context);
            return null;
          });
        return null;
      });

    // list note
    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // test moving corrupted note to trash
    String corruptedNoteId = notebookService.createNote("/folder_1/corruptedNote", "test", true, context, callback);
    notebook.processNote(corruptedNoteId,
      corruptedNote -> {
        String corruptedNotePath = notebookDir.getAbsolutePath() + corruptedNote.getPath() + "_" + corruptedNote.getId() + ".zpln";
        // corrupt note
        FileWriter myWriter = new FileWriter(corruptedNotePath);
        myWriter.write("{{{I'm corrupted;;;");
        myWriter.close();
        return null;
      });
    notebookService.moveNoteToTrash(corruptedNoteId, context, callback);

    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(3, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);
    notebookService.removeNote(corruptedNoteId, context, callback);
    // move note to Trash
    notebookService.moveNoteToTrash(importedNoteId, context, callback);

    reset(callback);
    notesInfo = notebookService.listNotesInfo(false, context, callback);
    assertEquals(2, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    boolean moveToTrash = false;
    for (NoteInfo noteInfo : notesInfo) {
      if (noteInfo.getId().equals(importedNoteId)) {
        assertEquals("/~Trash/Imported Note", noteInfo.getPath());
        moveToTrash = true;
      }
    }
    assertTrue("No note is moved to trash", moveToTrash);

    // restore it
    notebookService.restoreNote(importedNoteId, context, callback);
    notebookService.getNote(importedNoteId, context, callback,
      restoredNote -> {
        assertNotNull(restoredNote);
        assertEquals("/Imported Note", restoredNote.getPath());
        return null;
      });
 // move it to Trash again
    notebookService.moveNoteToTrash(importedNoteId, context, callback);
    // remove note from Trash
    reset(callback);

    notebookService.removeNote(importedNoteId, context, callback);
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
      if (noteInfo.getId().equals(clonedNoteId)) {
        assertEquals("/~Trash/Backup/Cloned Note", noteInfo.getPath());
        moveToTrash = true;
      }
    }
    assertTrue("No folder is moved to trash", moveToTrash);

    // restore folder
    reset(callback);
    notebookService.restoreFolder("/~Trash/Backup", context, callback);
    notebookService.getNote(clonedNoteId, context, callback,
      restoredNote -> {
        assertNotNull(restoredNote);
        assertEquals("/Backup/Cloned Note", restoredNote.getPath());
        return null;
      });

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
  public void testRenameNoteRejectsDuplicate() throws IOException {
    String note1Id = notebookService.createNote("/folder/note1", "test", true, context, callback);
    notebook.processNote(note1Id,
      note1 -> {
        assertEquals("note1", note1.getName());
        verify(callback).onSuccess(note1, context);
        return null;
      });

    reset(callback);
    String note2Id = notebookService.createNote("/folder/note2", "test", true, context, callback);
    notebook.processNote(note2Id,
      note2 -> {
        assertEquals("note2", note2.getName());
        verify(callback).onSuccess(note2, context);
        return null;
      });

    reset(callback);
    ArgumentCaptor<NotePathAlreadyExistsException> exception = ArgumentCaptor.forClass(NotePathAlreadyExistsException.class);
    notebookService.renameNote(note1Id, "/folder/note2", false, context, callback);
    verify(callback).onFailure(exception.capture(), any(ServiceContext.class));
    assertEquals("Note '/folder/note2' existed", exception.getValue().getMessage());
    verify(callback, never()).onSuccess(any(), any());
  }


  @Test
  public void testParagraphOperations() throws IOException {
    // create note
    String note1Id = notebookService.createNote("note1", "python", false, context, callback);
    notebook.processNote(note1Id,
      note1 -> {
        assertEquals("note1", note1.getName());
        assertEquals(0, note1.getParagraphCount());
        verify(callback).onSuccess(note1, context);
        return null;
      });


    // add paragraph
    reset(callback);
    Paragraph p = notebookService.insertParagraph(note1Id, 0, new HashMap<>(), context,
        callback);
    assertNotNull(p);
    verify(callback).onSuccess(p, context);
    notebook.processNote(note1Id,
      note1 -> {
        assertEquals(1, note1.getParagraphCount());
        return null;
      });

    // update paragraph
    reset(callback);
    notebookService.updateParagraph(note1Id, p.getId(), "my_title", "my_text",
        new HashMap<>(), new HashMap<>(), context, callback);
    assertEquals("my_title", p.getTitle());
    assertEquals("my_text", p.getText());

    // move paragraph
    reset(callback);
    notebookService.moveParagraph(note1Id, p.getId(), 0, context, callback);
    notebook.processNote(note1Id,
      note1 -> {
        assertEquals(p, note1.getParagraph(0));
        verify(callback).onSuccess(p, context);
        return null;
      });

    // run paragraph asynchronously
    reset(callback);
    p.getConfig().put("colWidth", "6.0");
    p.getConfig().put("title", true);
    boolean runStatus = notebook.processNote(note1Id,
      note1 -> {
        return notebookService.runParagraph(note1, p.getId(), "my_title", "1+1",
          new HashMap<>(), new HashMap<>(), null, false, false, context, callback);
      });
    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);
    assertEquals(2, p.getConfig().size());

    // run paragraph synchronously via correct code
    reset(callback);
    runStatus = notebook.processNote(note1Id,
      note1 -> {
        return notebookService.runParagraph(note1, p.getId(), "my_title", "1+1",
          new HashMap<>(), new HashMap<>(), null, false, true, context, callback);
      });

    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);
    assertEquals(2, p.getConfig().size());

    // run all paragraphs, with null paragraph list provided
    reset(callback);
    assertTrue(notebookService.runAllParagraphs(
            note1Id,
            null,
            context, callback));

    reset(callback);
    runStatus = notebook.processNote(note1Id,
      note1 -> {
        return notebookService.runParagraph(note1, p.getId(), "my_title", "invalid_code",
          new HashMap<>(), new HashMap<>(), null, false, true, context, callback);
      });
    assertTrue(runStatus);
    // TODO(zjffdu) Enable it after ZEPPELIN-3699
    // assertNotNull(p.getResult());
    verify(callback).onSuccess(p, context);

    // clean output
    reset(callback);
    notebookService.clearParagraphOutput(note1Id, p.getId(), context, callback);
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
