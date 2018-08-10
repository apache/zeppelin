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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class NotebookServiceTest extends AbstractTestRestApi {

  private static NotebookService notebookService;

  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    AbstractTestRestApi.startUp(NotebookServiceTest.class.getSimpleName());
    notebookService = ZeppelinServer.notebookWsServer.getNotebookService();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
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
    assertNull(p.getResult());
    verify(callback).onSuccess(p, context);
  }
}
