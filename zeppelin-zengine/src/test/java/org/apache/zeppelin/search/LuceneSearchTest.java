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

import static com.google.common.truth.Truth.assertThat;
import static org.apache.zeppelin.search.LuceneSearch.formatId;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;


import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LuceneSearchTest {

  private Notebook notebook;
  private InterpreterSettingManager interpreterSettingManager;
  private LuceneSearch noteSearchService;
  private File indexDir;

  @Before
  public void startUp() throws IOException {
    indexDir = Files.createTempDirectory("lucene").toFile();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SEARCH_INDEX_PATH.getVarName(), indexDir.getAbsolutePath());
    noteSearchService = new LuceneSearch(ZeppelinConfiguration.create());
    interpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterSetting defaultInterpreterSetting = mock(InterpreterSetting.class);
    when(defaultInterpreterSetting.getName()).thenReturn("test");
    when(interpreterSettingManager.getDefaultInterpreterSetting()).thenReturn(defaultInterpreterSetting);
    notebook = new Notebook(ZeppelinConfiguration.create(), mock(AuthorizationService.class), mock(NotebookRepo.class), mock(NoteManager.class),
            mock(InterpreterFactory.class), interpreterSettingManager,
            noteSearchService,
            mock(Credentials.class), null);
  }

  @After
  public void shutDown() {
    noteSearchService.close();
    indexDir.delete();
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
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("all");

    // then
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0))
        .containsEntry("id", formatId(note2.getId(), note2.getLastParagraph()));
  }

  @Test
  public void canIndexAndQueryByNotebookName() throws IOException, InterruptedException {
    // given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("Notebook1");

    // then
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0)).containsEntry("id", note1.getId());
  }

  @Test
  public void canIndexAndQueryByParagraphTitle() throws IOException, InterruptedException {
    // given
    Note note1 = newNoteWithParagraph("Notebook1", "test", "testingTitleSearch");
    Note note2 = newNoteWithParagraph("Notebook2", "not test", "notTestingTitleSearch");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = noteSearchService.query("testingTitleSearch");

    // then
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isAtLeast(1);
    int TitleHits = 0;
    for (Map<String, String> res : results) {
      if (res.get("header").contains("testingTitleSearch")) {
        TitleHits++;
      }
    }
    assertThat(TitleHits).isAtLeast(1);
  }

  @Test
  public void indexKeyContract() throws IOException, InterruptedException {
    // given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    drainSearchEvents();
    // when
    String id = resultForQuery("test").get(0).get("id"); // LuceneSearch.ID_FIELD
    // then
    assertThat(id.split("/")).asList() // key structure <noteId>/paragraph/<paragraphId>
        .containsAllOf(
            note1.getId(), "paragraph", note1.getLastParagraph().getId()); // LuceneSearch.PARAGRAPH
  }

  @Test // (expected=IllegalStateException.class)
  public void canNotSearchBeforeIndexing() {
    // given NO noteSearchService.index() was called
    // when
    List<Map<String, String>> result = noteSearchService.query("anything");
    // then
    assertThat(result).isEmpty();
    // assert logs were printed
    // "ERROR org.apache.zeppelin.search.SearchService:97 - Failed to open index dir RAMDirectory"
  }

  @Test
  public void canIndexAndReIndex() throws IOException, InterruptedException {
    // given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    Paragraph p2 = note2.getLastParagraph();
    p2.setText("test indeed");
    noteSearchService.updateNoteIndex(note2);
    noteSearchService.updateParagraphIndex(p2);

    // then
    List<Map<String, String>> results = noteSearchService.query("all");
    assertThat(results).isEmpty();

    results = noteSearchService.query("indeed");
    assertThat(results).isNotEmpty();
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
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    assertThat(resultForQuery("Notebook2")).isNotEmpty();

    // when
    noteSearchService.deleteNoteIndex(note2);

    // then
    assertThat(noteSearchService.query("all")).isEmpty();
    assertThat(resultForQuery("Notebook2")).isEmpty();

    List<Map<String, String>> results = resultForQuery("test");
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
  }

  @Test
  public void indexParagraphUpdatedOnNoteSave() throws IOException, InterruptedException {
    // given: total 2 notebooks, 3 paragraphs
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    assertThat(resultForQuery("test").size()).isEqualTo(3);

    // when
    Paragraph p1 = note1.getLastParagraph();
    p1.setText("no no no");
    notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);
    p1.getNote().fireParagraphUpdateEvent(p1);
    drainSearchEvents();

    // then
    assertThat(resultForQuery("Notebook1").size()).isEqualTo(1);

    List<Map<String, String>> results = resultForQuery("test");
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(2);

    // does not include Notebook1's paragraph any more
    for (Map<String, String> result : results) {
      assertThat(result.get("id").startsWith(note1.getId())).isFalse();
    }
  }

  @Test
  public void indexNoteNameUpdatedOnNoteSave() throws IOException, InterruptedException {
    // given: total 2 notebooks, 3 paragraphs
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();
    assertThat(resultForQuery("test").size()).isEqualTo(3);

    // when
    note1.setName("NotebookN");
    notebook.updateNote(note1, AuthenticationInfo.ANONYMOUS);
    drainSearchEvents();
    Thread.sleep(1000);
    // then
    assertThat(resultForQuery("Notebook1")).isEmpty();
    assertThat(resultForQuery("NotebookN")).isNotEmpty();
    assertThat(resultForQuery("NotebookN").size()).isEqualTo(1);
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
  private Note newNoteWithParagraph(String noteName, String parText) throws IOException {
    Note note1 = newNote(noteName);
    addParagraphWithText(note1, parText);
    return note1;
  }

  private Note newNoteWithParagraph(String noteName, String parText, String title) throws IOException {
    Note note = newNote(noteName);
    addParagraphWithTextAndTitle(note, parText, title);
    return note;
  }

  /** Creates a new Note \w given name, adds N paragraphs \w given texts */
  private Note newNoteWithParagraphs(String noteName, String... parTexts) throws IOException {
    Note note1 = newNote(noteName);
    for (String parText : parTexts) {
      addParagraphWithText(note1, parText);
    }
    return note1;
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

  private Note newNote(String name) throws IOException {
    return notebook.createNote(name, AuthenticationInfo.ANONYMOUS);
  }
}
