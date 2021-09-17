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
package org.apache.zeppelin.search;
import static org.apache.zeppelin.search.LuceneSearch.formatId;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LuceneSearchTest {

  private Notebook notebook;
  private InterpreterSettingManager interpreterSettingManager;
  private NoteManager noteManager;
  private LuceneSearch noteSearchService;
  private File indexDir;

  @Before
  public void startUp() throws IOException {
    indexDir = Files.createTempDirectory("lucene").toFile();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SEARCH_INDEX_PATH.getVarName(), indexDir.getAbsolutePath());

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    noteManager = new NoteManager(new InMemoryNotebookRepo(), conf);
    interpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterSetting defaultInterpreterSetting = mock(InterpreterSetting.class);
    when(defaultInterpreterSetting.getName()).thenReturn("test");
    when(interpreterSettingManager.getDefaultInterpreterSetting()).thenReturn(defaultInterpreterSetting);
    notebook = new Notebook(conf, mock(AuthorizationService.class),
        mock(NotebookRepo.class), noteManager,
            mock(InterpreterFactory.class), interpreterSettingManager,
            mock(Credentials.class), null);
    noteSearchService = new LuceneSearch(ZeppelinConfiguration.create(), notebook);
  }

  @After
  public void shutDown() throws IOException {
    noteSearchService.close();
    FileUtils.deleteDirectory(indexDir);
  }

  private void drainSearchEvents() throws InterruptedException {
    while (!noteSearchService.isEventQueueEmpty()) {
      Thread.sleep(1000);
    }
    Thread.sleep(1000);
  }

  @Test
  public void canIndexAndQuery() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("all");

    // then
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
    notebook.processNote(note2Id,
      note2 -> {
        assertEquals(formatId(note2.getId(), note2.getLastParagraph()), results.get(0).get("id"));
        return null;
      });
  }

  @Test
  public void canIndexAndQueryByNotebookName() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("Notebook1");

    // then
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
    assertEquals(note1Id, results.get(0).get("id"));
  }

  @Test
  public void canIndexAndQueryByParagraphTitle() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test", "testingTitleSearch");
    String note2Id = newNoteWithParagraph("Notebook2", "not test", "notTestingTitleSearch");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("testingTitleSearch");

    // then
    assertFalse(results.isEmpty());
    assertTrue(results.size() >= 1);
    int TitleHits = 0;
    for (Map<String, String> res : results) {
      if (res.get("header").contains("testingTitleSearch")) {
        TitleHits++;
      }
    }
    assertTrue(TitleHits >= 1);
  }

  @Test
  public void indexKeyContract() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    drainSearchEvents();
    // when
    String id = resultForQuery("test").get(0).get("id"); // LuceneSearch.ID_FIELD
    // then
    notebook.processNote(note1Id,
      note1 -> {
        assertArrayEquals(id.split("/"),  // key structure <noteId>/paragraph/<paragraphId>
            new String[]{note1.getId(), "paragraph", note1.getLastParagraph().getId()}); // LuceneSearch.PARAGRAPH
        return null;
      });
  }

  @Test // (expected=IllegalStateException.class)
  public void canNotSearchBeforeIndexing() {
    // given NO noteSearchService.index() was called
    // when
    List<Map<String, String>> result = noteSearchService.query("anything");
    // then
    assertTrue(result.isEmpty());
    // assert logs were printed
    // "ERROR org.apache.zeppelin.search.SearchService:97 - Failed to open index dir RAMDirectory"
  }

  @Test
  public void canIndexAndReIndex() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    notebook.processNote(note2Id,
      note2 -> {
        Paragraph p2 = note2.getLastParagraph();
        p2.setText("test indeed");
        noteSearchService.updateNoteIndex(note2Id);
        noteSearchService.updateParagraphIndex(note2Id, p2.getId());
        return null;
      });

    // then
    List<Map<String, String>> results = noteSearchService.query("all");
    assertTrue(results.isEmpty());

    results = noteSearchService.query("indeed");
    assertFalse(results.isEmpty());
  }

  @Test
  public void canDeleteNull() throws IOException {
    // give
    // looks like a bug in web UI: it tries to delete a note twice (after it has just been deleted)
    // when
    noteSearchService.deleteNoteIndex(null);
  }

  @Test
  public void canDeleteFromIndex() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    assertFalse(resultForQuery("Notebook2").isEmpty());

    // when
    noteSearchService.deleteNoteIndex(note2Id);

    // then
    assertTrue(noteSearchService.query("all").isEmpty());
    assertTrue(resultForQuery("Notebook2").isEmpty());

    List<Map<String, String>> results = resultForQuery("test");
    assertFalse(results.isEmpty());
    assertEquals(1, results.size());
  }

  @Test
  public void indexParagraphUpdatedOnNoteSave() throws IOException, InterruptedException {
    // given: total 2 notebooks, 3 paragraphs
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    assertEquals(3, resultForQuery("test").size());

    // when
    notebook.processNote(note1Id,
      note1 ->  {
        Paragraph p1 = note1.getLastParagraph();
        p1.setText("no no no");
        notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);
        p1.getNote().fireParagraphUpdateEvent(p1);
        return null;
      });
    drainSearchEvents();

    // then
    assertEquals(1, resultForQuery("Notebook1").size());

    List<Map<String, String>> results = resultForQuery("test");
    assertFalse(results.isEmpty());
    assertEquals(2, results.size());

    // does not include Notebook1's paragraph any more
    for (Map<String, String> result : results) {
      assertFalse(result.get("id").startsWith(note1Id));
    }
  }

  @Test
  public void indexNoteNameUpdatedOnNoteSave() throws IOException, InterruptedException {
    // given: total 2 notebooks, 3 paragraphs
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();
    assertEquals(3, resultForQuery("test").size());

    // when
    // use write lock, because name is overwritten
    notebook.processNote(note1Id,
      note1 -> {
        note1.setName("NotebookN");
        notebook.updateNote(note1, AuthenticationInfo.ANONYMOUS);
        return null;
      });
    drainSearchEvents();
    Thread.sleep(1000);
    // then
    assertTrue(resultForQuery("Notebook1").isEmpty());
    assertFalse(resultForQuery("NotebookN").isEmpty());
    assertEquals(1, resultForQuery("NotebookN").size());
  }

  private List<Map<String, String>> resultForQuery(String q) {
    return noteSearchService.query(q);
  }

  /**
   * Creates a new Note \w given name, adds a new paragraph \w given text
   *
   * @param noteName name of the note
   * @param parText text of the paragraph
   * @return Note
   */
  private String newNoteWithParagraph(String noteName, String parText) throws IOException {
    String note1Id = newNote(noteName);
    notebook.processNote(note1Id,
      note1 -> {
        addParagraphWithText(note1, parText);
        return null;
      });
    return note1Id;
  }

  private String newNoteWithParagraph(String noteName, String parText, String title) throws IOException {
    String noteId = newNote(noteName);
    notebook.processNote(noteId,
      note -> {
        addParagraphWithTextAndTitle(note, parText, title);
        return null;
      });
    return noteId;
  }

  /** Creates a new Note \w given name, adds N paragraphs \w given texts */
  private String newNoteWithParagraphs(String noteName, String... parTexts) throws IOException {
    String note1Id = newNote(noteName);
    notebook.processNote(note1Id,
      note1 -> {
        for (String parText : parTexts) {
          addParagraphWithText(note1, parText);
        }
        return null;
      });
    return note1Id;
  }

  private Paragraph addParagraphWithText(Note note, String text) {
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText(text);
    return p;
  }

  private Paragraph addParagraphWithTextAndTitle(Note note, String text, String title) {
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText(text);
    p.setTitle(title);
    return p;
  }

  private String newNote(String name) throws IOException {
    return notebook.createNote(name, AuthenticationInfo.ANONYMOUS);
  }
}
