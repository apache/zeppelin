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

import static org.apache.zeppelin.search.EmbeddingSearch.formatId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for {@link EmbeddingSearch}.
 *
 * <p>These tests require the ONNX model to be downloaded, so they are gated behind
 * the {@code ZEPPELIN_EMBEDDING_TEST} environment variable. To run:
 * <pre>
 *   ZEPPELIN_EMBEDDING_TEST=true mvn test -pl zeppelin-zengine \
 *     -Dtest=EmbeddingSearchTest
 * </pre>
 *
 * <p>The model (~86MB) is downloaded once to a temp directory and cached for the
 * duration of the test run.
 */
@EnabledIfEnvironmentVariable(named = "ZEPPELIN_EMBEDDING_TEST", matches = "true")
class EmbeddingSearchTest {

  private Notebook notebook;
  private InterpreterSettingManager interpreterSettingManager;
  private NoteManager noteManager;
  private EmbeddingSearch searchService;
  private File indexDir;

  @BeforeEach
  public void startUp() throws IOException {
    indexDir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SEARCH_INDEX_PATH.getVarName(),
        indexDir.getAbsolutePath());

    noteManager = new NoteManager(new InMemoryNotebookRepo(), zConf);
    interpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterSetting defaultInterpreterSetting = mock(InterpreterSetting.class);
    when(defaultInterpreterSetting.getName()).thenReturn("test");
    when(interpreterSettingManager.getDefaultInterpreterSetting())
        .thenReturn(defaultInterpreterSetting);
    notebook = new Notebook(zConf, mock(AuthorizationService.class),
        mock(NotebookRepo.class), noteManager,
        mock(InterpreterFactory.class), interpreterSettingManager,
        mock(Credentials.class), null);
    searchService = new EmbeddingSearch(zConf, notebook);
  }

  @AfterEach
  public void shutDown() throws IOException {
    searchService.close();
    FileUtils.deleteDirectory(indexDir);
  }

  private void drainSearchEvents() throws InterruptedException {
    while (!searchService.isEventQueueEmpty()) {
      Thread.sleep(1000);
    }
    Thread.sleep(1000);
  }

  @Test
  void canIndexAndQuery() throws IOException, InterruptedException {
    // given
    newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when — semantic search should find "all" in "not test at all"
    List<Map<String, String>> results = searchService.query("all");

    // then
    assertFalse(results.isEmpty());
    // The paragraph containing "all" should be in results
    boolean foundAll = results.stream()
        .anyMatch(r -> r.get("text").contains("all"));
    assertTrue(foundAll, "Should find paragraph containing 'all'");
  }

  @Test
  void canIndexAndQueryByNotebookName() throws IOException, InterruptedException {
    // given
    newNoteWithParagraph("Notebook1", "test");
    newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = searchService.query("Notebook1");

    // then
    assertFalse(results.isEmpty());
    assertTrue(results.get(0).get("name").contains("Notebook1"));
  }

  @Test
  void canIndexAndQueryByParagraphTitle() throws IOException, InterruptedException {
    // given
    newNoteWithParagraph("Notebook1", "test", "testingTitleSearch");
    newNoteWithParagraph("Notebook2", "not test", "notTestingTitleSearch");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = searchService.query("testingTitleSearch");

    // then
    assertFalse(results.isEmpty());
    boolean foundTitle = results.stream()
        .anyMatch(r -> r.get("header").contains("testingTitleSearch"));
    assertTrue(foundTitle);
  }

  @Test
  void semanticSearchFindsRelatedConcepts() throws IOException, InterruptedException {
    // given — this is the key test that differentiates from Lucene
    newNoteWithParagraph("SpendAnalysis",
        "SELECT sum(cost) FROM analytics.daily_sales WHERE date = current_date - interval '1' day");
    newNoteWithParagraph("UserCounts",
        "SELECT count(distinct user_id) FROM sessions WHERE region = 'us'");
    drainSearchEvents();

    // when — natural language query, no exact keyword match
    List<Map<String, String>> results = searchService.query("yesterday's spending");

    // then — should rank the spend query higher than the user count query
    assertFalse(results.isEmpty());
    assertEquals("SpendAnalysis", results.get(0).get("name"),
        "Semantic search should rank spend-related paragraph first");
  }

  @Test
  void indexKeyContract() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    drainSearchEvents();

    // when
    List<Map<String, String>> results = searchService.query("test");
    assertFalse(results.isEmpty());

    // then — find the paragraph result (not the note-name result)
    String id = results.stream()
        .filter(r -> r.get("id").contains("paragraph"))
        .findFirst()
        .map(r -> r.get("id"))
        .orElse("");

    notebook.processNote(note1Id, note1 -> {
      String expected = formatId(note1.getId(), note1.getLastParagraph());
      assertEquals(expected, id, "Key should be <noteId>/paragraph/<paragraphId>");
      return null;
    });
  }

  @Test
  void canNotSearchBeforeIndexing() {
    // given NO indexing was done
    // when
    List<Map<String, String>> result = searchService.query("anything");
    // then
    assertTrue(result.isEmpty());
  }

  @Test
  void canIndexAndReIndex() throws IOException, InterruptedException {
    // given
    newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    notebook.processNote(note2Id, note2 -> {
      Paragraph p2 = note2.getLastParagraph();
      p2.setText("test indeed");
      searchService.updateParagraphIndex(note2Id, p2.getId());
      return null;
    });

    // then — "indeed" should now be findable
    List<Map<String, String>> results = searchService.query("indeed");
    assertFalse(results.isEmpty());
  }

  @Test
  void canDeleteNull() {
    // should not throw
    searchService.deleteNoteIndex(null);
  }

  @Test
  void canDeleteFromIndex() throws IOException, InterruptedException {
    // given
    newNoteWithParagraph("Notebook1", "test");
    String note2Id = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    assertFalse(searchService.query("Notebook2").isEmpty());

    // when
    searchService.deleteNoteIndex(note2Id);

    // then — no results should reference the deleted note's ID
    boolean foundNote2After = searchService.query("not test at all").stream()
        .anyMatch(r -> r.get("id").startsWith(note2Id));
    assertFalse(foundNote2After, "Note2 should be removed from index after deletion");
    assertFalse(searchService.query("Notebook1").isEmpty());
  }

  @Test
  void indexParagraphUpdatedOnNoteSave() throws IOException, InterruptedException {
    // given
    String note1Id = newNoteWithParagraph("Notebook1", "test");
    newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    drainSearchEvents();

    // when
    notebook.processNote(note1Id, note1 -> {
      Paragraph p1 = note1.getLastParagraph();
      p1.setText("no no no");
      notebook.saveNote(note1, AuthenticationInfo.ANONYMOUS);
      p1.getNote().fireParagraphUpdateEvent(p1);
      return null;
    });
    drainSearchEvents();

    // then — "Notebook1" note name should still be findable
    assertFalse(searchService.query("Notebook1").isEmpty());
  }

  @Test
  void newParagraphIsLiveIndexed() throws IOException, InterruptedException {
    // given — one notebook exists
    String noteId = newNoteWithParagraph("Analytics", "SELECT 1");
    drainSearchEvents();

    // when — add a new paragraph with unique content
    notebook.processNote(noteId, note -> {
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setText("SELECT customer_id, SUM(amount) as lifetime_value FROM orders GROUP BY 1");
      notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
      note.fireParagraphUpdateEvent(p);
      return null;
    });
    drainSearchEvents();

    // then — the new paragraph should be findable by semantic query
    List<Map<String, String>> results = searchService.query("lifetime value");
    assertFalse(results.isEmpty(), "Newly added paragraph should be searchable");
    boolean found = results.stream()
        .anyMatch(r -> r.get("text").contains("lifetime_value"));
    assertTrue(found, "Should find the paragraph with lifetime_value");
  }

  // ---- Helper methods (same as LuceneSearchTest) ----

  private String newNoteWithParagraph(String noteName, String parText) throws IOException {
    String noteId = newNote(noteName);
    notebook.processNote(noteId, note -> {
      addParagraphWithText(note, parText);
      return null;
    });
    return noteId;
  }

  private String newNoteWithParagraph(String noteName, String parText, String title)
      throws IOException {
    String noteId = newNote(noteName);
    notebook.processNote(noteId, note -> {
      addParagraphWithTextAndTitle(note, parText, title);
      return null;
    });
    return noteId;
  }

  private String newNoteWithParagraphs(String noteName, String... parTexts) throws IOException {
    String noteId = newNote(noteName);
    notebook.processNote(noteId, note -> {
      for (String parText : parTexts) {
        addParagraphWithText(note, parText);
      }
      return null;
    });
    return noteId;
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
