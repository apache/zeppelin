package org.apache.zeppelin.notebook;

import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NoteManagerTest {
  private NoteManager noteManager;

  @Before
  public void setUp() throws IOException {
    this.noteManager = new NoteManager(new InMemoryNotebookRepo());
  }

  @Test
  public void testNoteOperations() throws IOException {
    assertEquals(0, this.noteManager.getNotesInfo().size());

    Note note1 = createNote("/prod/my_note1");
    Note note2 = createNote("/dev/project_2/my_note2");
    Note note3 = createNote("/dev/project_3/my_note3");

    // add note
    this.noteManager.saveNote(note1);
    this.noteManager.saveNote(note2);
    this.noteManager.saveNote(note3);

    // list notes
    assertEquals(3, this.noteManager.getNotesInfo().size());
    assertEquals(note1, this.noteManager.getNote(note1.getId()));
    assertEquals(note2, this.noteManager.getNote(note2.getId()));
    assertEquals(note3, this.noteManager.getNote(note3.getId()));

    // move note
    this.noteManager.moveNote(note1.getId(), "/dev/project_1/my_note1",
        AuthenticationInfo.ANONYMOUS);
    assertEquals(3, this.noteManager.getNotesInfo().size());
    assertEquals("/dev/project_1/my_note1", this.noteManager.getNote(note1.getId()).getPath());

    // move folder
    this.noteManager.moveFolder("/dev", "/staging", AuthenticationInfo.ANONYMOUS);
    Map<String, String> notesInfo = this.noteManager.getNotesInfo();
    assertEquals(3, notesInfo.size());
    assertEquals("/staging/project_1/my_note1", notesInfo.get(note1.getId()));
    assertEquals("/staging/project_2/my_note2", notesInfo.get(note2.getId()));
    assertEquals("/staging/project_3/my_note3", notesInfo.get(note3.getId()));

    this.noteManager.removeNote(note1.getId(), AuthenticationInfo.ANONYMOUS);
    assertEquals(2, this.noteManager.getNotesInfo().size());

    // remove folder
    this.noteManager.removeFolder("/staging", AuthenticationInfo.ANONYMOUS);
    notesInfo = this.noteManager.getNotesInfo();
    assertEquals(0, notesInfo.size());
  }

  private Note createNote(String notePath) {
    return new Note(notePath, "test", null, null, null, null, null);
  }
}
