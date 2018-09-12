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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSettingsInfo;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.Before;
import org.junit.Test;

public class NotebookServiceTest {

  private static NotebookService notebookService;

  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  @Before
  public void setUp() throws Exception {
    ZeppelinConfiguration zeppelinConfiguration = ZeppelinConfiguration.create();
    NotebookRepo notebookRepo =
        new NotebookRepo() {
          Map<String, Note> notes = Maps.newHashMap();

          @Override
          public void init(ZeppelinConfiguration zConf) throws IOException {}

          @Override
          public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
            return notes.values().stream().map(NoteInfo::new).collect(Collectors.toList());
          }

          @Override
          public Note get(String noteId, AuthenticationInfo subject) throws IOException {
            return notes.get(noteId);
          }

          @Override
          public void save(Note note, AuthenticationInfo subject) throws IOException {
            notes.put(note.getId(), note);
          }

          @Override
          public void remove(String noteId, AuthenticationInfo subject) throws IOException {
            notes.remove(notes.get(noteId));
          }

          @Override
          public void close() {}

          @Override
          public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
            return null;
          }

          @Override
          public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {}
        };
    InterpreterSettingManager mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterFactory mockInterpreterFactory = mock(InterpreterFactory.class);
    Interpreter mockInterpreter = mock(Interpreter.class);
    when(mockInterpreterFactory.getInterpreter(any(), any(), any(), any())).thenReturn(mockInterpreter);
    when(mockInterpreter.interpret(eq("invalid_code"), any())).thenReturn(new InterpreterResult(Code.ERROR, "failed"));
    when(mockInterpreter.interpret(eq("1+1"), any())).thenReturn(new InterpreterResult(Code.SUCCESS, "succeed"));
    doCallRealMethod().when(mockInterpreter).getScheduler();
    when(mockInterpreter.getFormType()).thenReturn(FormType.NATIVE);
    ManagedInterpreterGroup mockInterpreterGroup = mock(ManagedInterpreterGroup.class);
    when(mockInterpreter.getInterpreterGroup()).thenReturn(mockInterpreterGroup);
    InterpreterSetting mockInterpreterSetting = mock(InterpreterSetting.class);
    when(mockInterpreterSetting.isUserAuthorized(any())).thenReturn(true);
    when(mockInterpreterGroup.getInterpreterSetting()).thenReturn(mockInterpreterSetting);
    SearchService searchService = new LuceneSearch(zeppelinConfiguration);
    NotebookAuthorization notebookAuthorization = NotebookAuthorization.getInstance();
    Credentials credentials = new Credentials(false, null, null);
    Notebook notebook =
        new Notebook(
            zeppelinConfiguration,
            notebookRepo,
            mockInterpreterFactory,
            mockInterpreterSettingManager,
            searchService,
            notebookAuthorization,
            credentials);
    notebookService = new NotebookService(notebook);

    String interpreterName = "test";
    when(mockInterpreterSetting.getName()).thenReturn(interpreterName);
    when(mockInterpreterSettingManager.getDefaultInterpreterSetting())
        .thenReturn(mockInterpreterSetting);
  }

  @Test
  public void testNoteOperations() throws IOException {
    // get home note
    Note homeNote = notebookService.getHomeNote(context, callback);
    assertNull(homeNote);
    verify(callback).onSuccess(homeNote, context);

    // create note
    Note note1 = notebookService.createNote("note1", "test", context, callback);
    assertEquals("note1", note1.getName());
    assertEquals(1, note1.getParagraphCount());
    verify(callback).onSuccess(note1, context);

    // list note
    reset(callback);
    List<Map<String, String>> notesInfo = notebookService.listNotes(false, context, callback);
    assertEquals(1, notesInfo.size());
    assertEquals(note1.getId(), notesInfo.get(0).get("id"));
    assertEquals(note1.getName(), notesInfo.get(0).get("name"));
    verify(callback).onSuccess(notesInfo, context);

    // get note
    reset(callback);
    Note note2 = notebookService.getNote(note1.getId(), context, callback);
    assertEquals(note1, note2);
    verify(callback).onSuccess(note2, context);

    // rename note
    reset(callback);
    notebookService.renameNote(note1.getId(), "new_name", context, callback);
    verify(callback).onSuccess(note1, context);
    assertEquals("new_name", note1.getName());

    // delete note
    reset(callback);
    notebookService.removeNote(note1.getId(), context, callback);
    verify(callback).onSuccess("Delete note successfully", context);

    // list note again
    reset(callback);
    notesInfo = notebookService.listNotes(false, context, callback);
    assertEquals(0, notesInfo.size());
    verify(callback).onSuccess(notesInfo, context);

    // import note
    reset(callback);
    Note importedNote = notebookService.importNote("imported note", "{}", context, callback);
    assertNotNull(importedNote);
    verify(callback).onSuccess(importedNote, context);

    // clone note
    reset(callback);
    Note clonedNote = notebookService.cloneNote(importedNote.getId(), "Cloned Note", context,
        callback);
    assertEquals(importedNote.getParagraphCount(), clonedNote.getParagraphCount());
    verify(callback).onSuccess(clonedNote, context);
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
    boolean runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "1+1",
        new HashMap<>(), new HashMap<>(), false, false, context, callback);
    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);

    // run paragraph synchronously via correct code
    reset(callback);
    runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "1+1",
        new HashMap<>(), new HashMap<>(), false, true, context, callback);
    assertTrue(runStatus);
    verify(callback).onSuccess(p, context);

    // run paragraph synchronously via invalid code
    reset(callback);
    runStatus = notebookService.runParagraph(note1.getId(), p.getId(), "my_title", "invalid_code",
        new HashMap<>(), new HashMap<>(), false, true, context, callback);
    assertFalse(runStatus);
    // TODO(zjffdu) Enable it after ZEPPELIN-3699
    // assertNotNull(p.getResult());
    verify(callback).onSuccess(p, context);

    // clean output
    reset(callback);
    notebookService.clearParagraphOutput(note1.getId(), p.getId(), context, callback);
    assertNull(p.getReturn());
    verify(callback).onSuccess(p, context);
  }
}
