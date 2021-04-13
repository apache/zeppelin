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

package org.apache.zeppelin.integration;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.NoteResult;
import org.apache.zeppelin.client.ParagraphResult;

import org.apache.zeppelin.client.Status;
import org.apache.zeppelin.client.ZeppelinClient;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.common.SessionInfo;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZeppelinClientIntegrationTest extends AbstractTestRestApi {
  private static Notebook notebook;

  private static ClientConfig clientConfig;
  private static ZeppelinClient zeppelinClient;

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(), "*");

    AbstractTestRestApi.startUp(ZeppelinClientIntegrationTest.class.getSimpleName());
    notebook = TestUtils.getInstance(Notebook.class);

    clientConfig = new ClientConfig("http://localhost:8080");
    zeppelinClient = new ZeppelinClient(clientConfig);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testZeppelinVersion() throws Exception {
    String version = zeppelinClient.getVersion();
    LOG.info("Zeppelin version: " + version);
  }

  @Test
  public void testImportNote() throws Exception {
    String noteContent = IOUtils.toString(ZeppelinClientIntegrationTest.class.getResource("/Test_Note.zpln"));
    String noteId = zeppelinClient.importNote("/imported_notes/note_1", noteContent);
    assertNotNull("Import note failed because returned noteId is null", noteId);

    NoteResult noteResult = zeppelinClient.queryNoteResult(noteId);
    assertFalse(noteResult.isRunning());
    assertEquals(2, noteResult.getParagraphResultList().size());
    assertEquals(1, noteResult.getParagraphResultList().get(0).getResults().size());
    assertEquals("TEXT", noteResult.getParagraphResultList().get(0).getResults().get(0).getType());
    assertEquals("Hello World\n", noteResult.getParagraphResultList().get(0).getResults().get(0).getData());

    // import to the same notePath again
    try {
      zeppelinClient.importNote("/imported_notes/note_1", noteContent);
      fail("Should fail to import note to the same notePath");
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue(e.getMessage(), e.getMessage().contains("Note '/imported_notes/note_1' existed"));
    }

    // import invalid noteContent
    try {
      zeppelinClient.importNote("/imported_notes/note_1", "Invalid_content");
      fail("Should fail to import note with invalid note content");
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue(e.getMessage(), e.getMessage().contains("Invalid JSON"));
    }
  }

  @Test
  public void testNoteOperation() throws Exception {
    String noteId = zeppelinClient.createNote("/project_1/note1");
    assertNotNull(notebook.getNote(noteId));

    // create duplicated note
    try {
      zeppelinClient.createNote("/project_1/note1");
      fail("Should fail to create duplicated note");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("existed"));
    }

    // query NoteResult
    NoteResult noteResult = zeppelinClient.queryNoteResult(noteId);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    // note is created with 0 paragraph.
    assertEquals(0, noteResult.getParagraphResultList().size());

    // query non-existed note
    try {
      zeppelinClient.queryNoteResult("unknown-noteId");
      fail("Should fail to query non-existed note");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such note"));
    }

    zeppelinClient.deleteNote(noteId);

    // deleting the same note again will fail
    try {
      zeppelinClient.deleteNote(noteId);
      fail("Should fail to delete non-existed note");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such note"));
    }
  }

  @Test
  public void testCloneNote() throws Exception {
    String noteId = zeppelinClient.createNote("/clone_note_test/note1");
    Note note1 = notebook.getNote(noteId);
    assertNotNull(note1);

    zeppelinClient.addParagraph(noteId, "title_1", "text_1");
    assertEquals(1, note1.getParagraphCount());

    String clonedNoteId = zeppelinClient.cloneNote(noteId, "/clone_note_test/cloned_note1");
    Note clonedNote = notebook.getNote(clonedNoteId);
    assertEquals(1, clonedNote.getParagraphCount());
    assertEquals("title_1", clonedNote.getParagraph(0).getTitle());
    assertEquals("text_1", clonedNote.getParagraph(0).getText());
  }

  @Test
  public void testRenameNote() throws Exception {
    String noteId = zeppelinClient.createNote("/rename_note_test/note1");
    Note note1 = notebook.getNote(noteId);
    assertNotNull(note1);

    zeppelinClient.addParagraph(noteId, "title_1", "text_1");
    assertEquals(1, note1.getParagraphCount());

    zeppelinClient.renameNote(noteId, "/rename_note_test/note1_renamed");
    Note renamedNote = notebook.getNote(noteId);
    assertEquals("/rename_note_test/note1_renamed", renamedNote.getPath());
    assertEquals(1, renamedNote.getParagraphCount());
    assertEquals("title_1", renamedNote.getParagraph(0).getTitle());
    assertEquals("text_1", renamedNote.getParagraph(0).getText());
  }

  @Test
  public void testDeleteParagraph() throws Exception {
    String noteId = zeppelinClient.createNote("/test/note_1");
    Note note = notebook.getNote(noteId);
    assertNotNull(note);

    String paragraphId = zeppelinClient.addParagraph(noteId, "title_1", "text_1");
    assertEquals(1, note.getParagraphCount());

    zeppelinClient.deleteParagraph(noteId, paragraphId);
    assertEquals(0, note.getParagraphCount());
  }

  @Test
  public void testExecuteParagraph() throws Exception {
    // run paragraph succeed
    String noteId = zeppelinClient.createNote("/test/note_1");
    String paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello world'");
    ParagraphResult paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello world\n", paragraphResult.getResults().get(0).getData());

    // run paragraph succeed with dynamic forms
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello ${name=abc}'");
    paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(paragraphResult.toString(), Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello abc\n", paragraphResult.getResults().get(0).getData());

    // run paragraph succeed with parameters
    Map<String, String> parameters = new HashMap<>();
    parameters.put("name", "zeppelin");
    paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId, parameters);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", paragraphResult.getResults().get(0).getData());

    // run paragraph failed
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh invalid_command");
    paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.ERROR, paragraphResult.getStatus());
    assertEquals(2, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertTrue(paragraphResult.getResults().get(0).getData(), paragraphResult.getResults().get(0).getData().contains("command not found"));
    assertEquals("TEXT", paragraphResult.getResults().get(1).getType());
    assertTrue(paragraphResult.getResults().get(1).getData(), paragraphResult.getResults().get(1).getData().contains("ExitValue"));

    // run non-existed interpreter
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%non_existed hello");
    paragraphResult = zeppelinClient.executeParagraph(noteId, paragraphId);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.ERROR, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertTrue(paragraphResult.getResults().get(0).getData(), paragraphResult.getResults().get(0).getData().contains("Interpreter non_existed not found"));

    // run non-existed paragraph
    try {
      zeppelinClient.executeParagraph(noteId, "invalid_paragraph_id");
      fail("Should fail to run non-existed paragraph");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such paragraph"));
    }
  }

  @Test
  public void testSubmitParagraph() throws Exception {
    String noteId = zeppelinClient.createNote("/test/note_2");
    String paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello world'");
    zeppelinClient.submitParagraph(noteId, paragraphId);
    ParagraphResult paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, paragraphId, 10 * 1000);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello world\n", paragraphResult.getResults().get(0).getData());

    // submit paragraph succeed with dynamic forms
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello ${name=abc}'");
    zeppelinClient.submitParagraph(noteId, paragraphId);
    paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, paragraphId, 10 * 1000);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(paragraphResult.toString(), Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello abc\n", paragraphResult.getResults().get(0).getData());

    // run paragraph succeed with parameters
    Map<String, String> parameters = new HashMap<>();
    parameters.put("name", "zeppelin");
    zeppelinClient.submitParagraph(noteId, paragraphId, parameters);
    paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, paragraphId, 10 * 1000);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.FINISHED, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", paragraphResult.getResults().get(0).getData());

    // run paragraph failed
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%sh invalid_command");
    zeppelinClient.submitParagraph(noteId, paragraphId);
    paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, paragraphId, 10 * 1000);

    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.ERROR, paragraphResult.getStatus());
    assertEquals(2, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertTrue(paragraphResult.getResults().get(0).getData(), paragraphResult.getResults().get(0).getData().contains("command not found"));
    assertEquals("TEXT", paragraphResult.getResults().get(1).getType());
    assertTrue(paragraphResult.getResults().get(1).getData(), paragraphResult.getResults().get(1).getData().contains("ExitValue"));

    // run non-existed interpreter
    paragraphId = zeppelinClient.addParagraph(noteId, "run sh", "%non_existed hello");
    zeppelinClient.submitParagraph(noteId, paragraphId);
    paragraphResult = zeppelinClient.waitUtilParagraphFinish(noteId, paragraphId, 10 * 1000);
    assertEquals(paragraphId, paragraphResult.getParagraphId());
    assertEquals(Status.ERROR, paragraphResult.getStatus());
    assertEquals(1, paragraphResult.getResults().size());
    assertEquals("TEXT", paragraphResult.getResults().get(0).getType());
    assertTrue(paragraphResult.getResults().get(0).getData(), paragraphResult.getResults().get(0).getData().contains("Interpreter non_existed not found"));

    // run non-existed paragraph
    try {
      zeppelinClient.submitParagraph(noteId, "invalid_paragraph_id");
      fail("Should fail to run non-existed paragraph");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such paragraph"));
    }
  }

  @Test
  public void testExecuteNote() throws Exception {
    try {
      zeppelinClient.executeNote("unknown_id");
      fail("Should fail to submit non-existed note");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such note"));
    }
    String noteId = zeppelinClient.createNote("/test/note_3");
    String p0Id = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello world'");
    NoteResult noteResult = zeppelinClient.executeNote(noteId, new HashMap<>());
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    ParagraphResult p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello world\n", p0.getResults().get(0).getData());

    // update paragraph with dynamic forms
    zeppelinClient.updateParagraph(noteId, p0Id, "run sh", "%sh echo 'hello ${name=abc}'");
    noteResult = zeppelinClient.executeNote(noteId);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello abc\n", p0.getResults().get(0).getData());

    // execute paragraph with parameters
    Map<String, String> parameters = new HashMap<>();
    parameters.put("name", "zeppelin");
    noteResult = zeppelinClient.executeNote(noteId, parameters);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", p0.getResults().get(0).getData());

    zeppelinClient.addParagraph(noteId, "run sh", "%sh invalid_command");
    zeppelinClient.addParagraph(noteId, "run sh", "%sh pwd");
    noteResult = zeppelinClient.executeNote(noteId, parameters);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(3, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", p0.getResults().get(0).getData());

    ParagraphResult p1 = noteResult.getParagraphResultList().get(1);
    assertEquals(Status.ERROR, p1.getStatus());
    assertEquals("TEXT", p1.getResults().get(0).getType());
    assertTrue(p1.getResults().get(0).getData(), p1.getResults().get(0).getData().contains("command not found"));
    assertEquals("TEXT", p1.getResults().get(1).getType());
    assertTrue(p1.getResults().get(1).getData(), p1.getResults().get(1).getData().contains("ExitValue"));

    // p2 will be skipped because p1 fails.
    ParagraphResult p2 = noteResult.getParagraphResultList().get(2);
    assertEquals(Status.READY, p2.getStatus());
    assertEquals(0, p2.getResults().size());
  }

  @Test
  public void testSubmitNote() throws Exception {
    try {
      zeppelinClient.submitNote("unknown_id");
      fail("Should fail to submit non-existed note");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("No such note"));
    }
    String noteId = zeppelinClient.createNote("/test/note_4");
    String p0Id = zeppelinClient.addParagraph(noteId, "run sh", "%sh echo 'hello world'");
    zeppelinClient.submitNote(noteId);
    NoteResult noteResult = zeppelinClient.waitUntilNoteFinished(noteId);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    ParagraphResult p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello world\n", p0.getResults().get(0).getData());

    // update paragraph with dynamic forms
    zeppelinClient.updateParagraph(noteId, p0Id, "run sh", "%sh sleep 5\necho 'hello ${name=abc}'");
    noteResult = zeppelinClient.submitNote(noteId);
    assertEquals(true, noteResult.isRunning());
    noteResult = zeppelinClient.waitUntilNoteFinished(noteId);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello abc\n", p0.getResults().get(0).getData());

    // execute paragraph with parameters
    Map<String, String> parameters = new HashMap<>();
    parameters.put("name", "zeppelin");
    noteResult = zeppelinClient.executeNote(noteId, parameters);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(1, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", p0.getResults().get(0).getData());

    zeppelinClient.addParagraph(noteId, "run sh", "%sh invalid_command");
    zeppelinClient.addParagraph(noteId, "run sh", "%sh pwd");
    zeppelinClient.submitNote(noteId, parameters);
    noteResult = zeppelinClient.waitUntilNoteFinished(noteId);
    assertEquals(noteId, noteResult.getNoteId());
    assertEquals(false, noteResult.isRunning());
    assertEquals(3, noteResult.getParagraphResultList().size());
    p0 = noteResult.getParagraphResultList().get(0);
    assertEquals(Status.FINISHED, p0.getStatus());
    assertEquals(1, p0.getResults().size());
    assertEquals("TEXT", p0.getResults().get(0).getType());
    assertEquals("hello zeppelin\n", p0.getResults().get(0).getData());

    ParagraphResult p1 = noteResult.getParagraphResultList().get(1);
    assertEquals(Status.ERROR, p1.getStatus());
    assertEquals("TEXT", p1.getResults().get(0).getType());
    assertTrue(p1.getResults().get(0).getData(), p1.getResults().get(0).getData().contains("command not found"));
    assertEquals("TEXT", p1.getResults().get(1).getType());
    assertTrue(p1.getResults().get(1).getData(), p1.getResults().get(1).getData().contains("ExitValue"));

    // p2 will be skipped because p1 fails.
    ParagraphResult p2 = noteResult.getParagraphResultList().get(2);
    assertEquals(Status.READY, p2.getStatus());
    assertEquals(0, p2.getResults().size());
  }

  @Test
  public void testSession() throws Exception {
    SessionInfo sessionInfo = zeppelinClient.getSession("invalid_session");
    assertNull(sessionInfo);

    try {
      zeppelinClient.stopSession("invalid_session");
      fail("Should fail to stop session after it is stopped");
    } catch (Exception e) {
      e.printStackTrace();
      assertTrue(e.getMessage().contains("No such session"));
    }
  }
}
