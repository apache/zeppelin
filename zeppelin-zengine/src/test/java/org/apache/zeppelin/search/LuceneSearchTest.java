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
import static org.mockito.Mockito.*;
import static org.apache.zeppelin.search.LuceneSearch.formatId;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInterpreterLoader;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class LuceneSearchTest {

  private static NoteInterpreterLoader replLoaderMock;
  private static NotebookRepo notebookRepoMock;
  private SearchService notebookIndex;

  @BeforeClass
  public static void beforeStartUp() {
    notebookRepoMock = mock(NotebookRepo.class);
    replLoaderMock = mock(NoteInterpreterLoader.class);

    when(replLoaderMock.getInterpreterSettings())
      .thenReturn(ImmutableList.<InterpreterSetting>of());
  }

  @Before
  public void startUp() {
    notebookIndex = new LuceneSearch();
  }

  @After
  public void shutDown() {
    notebookIndex.close();
  }

  @Test public void canIndexNotebook() {
    //give
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraph("Notebook2", "not test");
    List<Note> notebook = Arrays.asList(note1, note2);

    //when
    notebookIndex.addIndexDocs(notebook);
  }

  @Test public void canIndexAndQuery() {
    //given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));

    //when
    List<Map<String, String>> results = notebookIndex.query("all");

    //then
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0))
      .containsEntry("id", formatId(note2.getId(), note2.getLastParagraph()));
  }

  @Test public void canIndexAndQueryByNotebookName() {
    //given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));

    //when
    List<Map<String, String>> results = notebookIndex.query("Notebook1");

    //then
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0)).containsEntry("id", note1.getId());
  }

  @Test
  public void canIndexAndQueryByParagraphTitle() {
    //given
    Note note1 = newNoteWithParagraph("Notebook1", "test", "testingTitleSearch");
    Note note2 = newNoteWithParagraph("Notebook2", "not test", "notTestingTitleSearch");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));

    //when
    List<Map<String, String>> results = notebookIndex.query("testingTitleSearch");

    //then
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

  @Test public void indexKeyContract() throws IOException {
    //give
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    //when
    notebookIndex.addIndexDoc(note1);
    //then
    String id = resultForQuery("test").get(0).get(LuceneSearch.ID_FIELD);

    assertThat(Splitter.on("/").split(id)) //key structure <noteId>/paragraph/<paragraphId>
      .containsAllOf(note1.getId(), LuceneSearch.PARAGRAPH, note1.getLastParagraph().getId());
  }

  @Test //(expected=IllegalStateException.class)
  public void canNotSearchBeforeIndexing() {
    //given NO notebookIndex.index() was called
    //when
    List<Map<String, String>> result = notebookIndex.query("anything");
    //then
    assertThat(result).isEmpty();
    //assert logs were printed
    //"ERROR org.apache.zeppelin.search.SearchService:97 - Failed to open index dir RAMDirectory"
  }

  @Test public void canIndexAndReIndex() throws IOException {
    //given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));

    //when
    Paragraph p2 = note2.getLastParagraph();
    p2.setText("test indeed");
    notebookIndex.updateIndexDoc(note2);

    //then
    List<Map<String, String>> results = notebookIndex.query("all");
    assertThat(results).isEmpty();

    results = notebookIndex.query("indeed");
    assertThat(results).isNotEmpty();
  }

  @Test public void canDeleteNull() throws IOException {
    //give
    // looks like a bug in web UI: it tries to delete a note twice (after it has just been deleted)
    //when
    notebookIndex.deleteIndexDocs(null);
  }

  @Test public void canDeleteFromIndex() throws IOException {
    //given
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));
    assertThat(resultForQuery("Notebook2")).isNotEmpty();

    //when
    notebookIndex.deleteIndexDocs(note2);

    //then
    assertThat(notebookIndex.query("all")).isEmpty();
    assertThat(resultForQuery("Notebook2")).isEmpty();

    List<Map<String, String>> results = resultForQuery("test");
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(1);
  }

  @Test public void indexParagraphUpdatedOnNoteSave() throws IOException {
    //given: total 2 notebooks, 3 paragraphs
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));
    assertThat(resultForQuery("test").size()).isEqualTo(3);

    //when
    Paragraph p1 = note1.getLastParagraph();
    p1.setText("no no no");
    note1.persist();

    //then
    assertThat(resultForQuery("Notebook1").size()).isEqualTo(1);

    List<Map<String, String>> results = resultForQuery("test");
    assertThat(results).isNotEmpty();
    assertThat(results.size()).isEqualTo(2);

    //does not include Notebook1's paragraph any more
    for (Map<String, String> result: results) {
      assertThat(result.get("id").startsWith(note1.getId())).isFalse();;
    }
  }

  @Test public void indexNoteNameUpdatedOnNoteSave() throws IOException {
    //given: total 2 notebooks, 3 paragraphs
    Note note1 = newNoteWithParagraph("Notebook1", "test");
    Note note2 = newNoteWithParagraphs("Notebook2", "not test", "not test at all");
    notebookIndex.addIndexDocs(Arrays.asList(note1, note2));
    assertThat(resultForQuery("test").size()).isEqualTo(3);

    //when
    note1.setName("NotebookN");
    note1.persist();

    //then
    assertThat(resultForQuery("Notebook1")).isEmpty();
    assertThat(resultForQuery("NotebookN")).isNotEmpty();
    assertThat(resultForQuery("NotebookN").size()).isEqualTo(1);
  }

  private List<Map<String, String>> resultForQuery(String q) {
    return notebookIndex.query(q);
  }

  /**
   * Creates a new Note \w given name,
   * adds a new paragraph \w given text
   *
   * @param noteName name of the note
   * @param parText text of the paragraph
   * @return Note
   */
  private Note newNoteWithParagraph(String noteName, String parText) {
    Note note1 = newNote(noteName);
    addParagraphWithText(note1, parText);
    return note1;
  }

  private Note newNoteWithParagraph(String noteName, String parText,String title) {
    Note note = newNote(noteName);
    addParagraphWithTextAndTitle(note, parText, title);
    return note;
  }

  /**
   * Creates a new Note \w given name,
   * adds N paragraphs \w given texts
   */
  private Note newNoteWithParagraphs(String noteName, String... parTexts) {
    Note note1 = newNote(noteName);
    for (String parText : parTexts) {
      addParagraphWithText(note1, parText);
    }
    return note1;
  }

  private Paragraph addParagraphWithText(Note note, String text) {
    Paragraph p = note.addParagraph();
    p.setText(text);
    return p;
  }

  private Paragraph addParagraphWithTextAndTitle(Note note, String text, String title) {
    Paragraph p = note.addParagraph();
    p.setText(text);
    p.setTitle(title);
    return p;
  }

  private Note newNote(String name) {
    Note note = new Note(notebookRepoMock, replLoaderMock, null, notebookIndex);
    note.setName(name);
    return note;
  }

}
