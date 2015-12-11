package org.apache.zeppelin.search;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.junit.Before;
import org.junit.Test;

public class SearchServiceTest {

  SearchService notebookIndex;

  @Before
  public void startUp() {
    notebookIndex = new SearchService();
  }

  @Test public void canIndexNotebook() {
    //give
    Note note1 = newNoteWithParapgraph("Notebook1", "test");
    Note note2 = newNoteWithParapgraph("Notebook2", "not test");    
    List<Note> notebook = Arrays.asList(note1, note2);
    
    //when
    notebookIndex.index(notebook);
  }

  @Test public void canIndexAndQuery() {
    //given
    Note note1 = newNoteWithParapgraph("Notebook1", "test");
    Note note2 = newNoteWithParapgraphs("Notebook2", "not test", "not test at all");
    notebookIndex.index(Arrays.asList(note1, note2));
    
    //when
    List<Map<String, String>> results = notebookIndex.search("all");
    
    //then
    assertThat(results).isNotEmpty();    
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0)).containsEntry("id",
        String.format("%s/paragraph/%s", note2.getId(), note2.getLastParagraph().getId()));
  }
  
  @Test(expected=IllegalStateException.class) 
  public void canNotSearchBeforeIndexing() {
    //given no notebookIndex.index() was made
    //when
    notebookIndex.search("anything");
  }

  /**
   * Creates a new Note \w given name,
   * adds a new paragraph \w given text
   * 
   * @param noteName name of the note
   * @param parText text of the paragraph
   * @return Note
   */
  private Note newNoteWithParapgraph(String noteName, String parText) {
    Note note1 = newNote(noteName);
    addParagraphWithText(note1, parText);
    return note1;
  }
  
  /**
   * Creates a new Note \w given name,
   * adds N paragraphs \w given texts
   */
  private Note newNoteWithParapgraphs(String noteName, String... parTexts) {
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

  private Note newNote(String name) {
    Note note = new Note(null, null, null);
    note.setName(name);
    return note;
  }

}
