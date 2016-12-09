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

package org.apache.zeppelin.notebook;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.Credentials;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class FolderViewTest {
  @Mock
  NotebookRepo repo;

  @Mock
  JobListenerFactory jobListenerFactory;

  @Mock
  SearchService index;

  @Mock
  Credentials credentials;

  @Mock
  Interpreter interpreter;

  @Mock
  Scheduler scheduler;

  @Mock
  NoteEventListener noteEventListener;

  @Mock
  InterpreterFactory interpreterFactory;

  FolderView folderView;

  Note note1;
  Note note2;
  Note note3;

  List<String> testNoteNames = Arrays.asList(
          "note1", "/note2",
          "a/note1", "/a/note2",
          "a/b/note1", "/a/b/note2"
  );

  Folder rootFolder;
  Folder aFolder;
  Folder abFolder;

  Note rootNote1;
  Note rootNote2;
  Note aNote1;
  Note aNote2;
  Note abNote1;
  Note abNote2;

  private Note createNote() {
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    note.setNoteNameListener(folderView);
    return note;
  }

  @Before
  public void createNotesAndFolderMap() {
    folderView = new FolderView();

    for (String noteName : testNoteNames) {
      Note note = createNote();
      note.setName(noteName);
      folderView.putNote(note);
    }

    rootFolder = folderView.getFolder("/");
    aFolder = folderView.getFolder("a");
    abFolder = folderView.getFolder("a/b");

    rootNote1 = rootFolder.getNotes().get(0);
    rootNote2 = rootFolder.getNotes().get(1);
    aNote1 = aFolder.getNotes().get(0);
    aNote2 = aFolder.getNotes().get(1);
    abNote1 = abFolder.getNotes().get(0);
    abNote2 = abFolder.getNotes().get(1);
  }

  @Test
  public void putNoteTest() {
    assertEquals(6, folderView.countNotes());
    assertEquals(3, folderView.countFolders());

    assertEquals(2, rootFolder.countNotes());
    assertEquals(2, aFolder.countNotes());
    assertEquals(2, abFolder.countNotes());

    assertEquals("note1", rootNote1.getName());
    assertEquals("/note2", rootNote2.getName());
    assertEquals("a/note1", aNote1.getName());
    assertEquals("/a/note2", aNote2.getName());
    assertEquals("a/b/note1", abNote1.getName());
    assertEquals("/a/b/note2", abNote2.getName());
  }

  @Test
  public void getTest() {
    assertEquals(rootFolder, folderView.getFolder("/"));

    assertEquals(aFolder, folderView.getFolder("a"));
    assertEquals(aFolder, folderView.getFolder("/a"));
    assertEquals(aFolder, folderView.getFolder("a/"));
    assertEquals(aFolder, folderView.getFolder("/a/"));

    assertEquals(abFolder, folderView.getFolder("a/b"));
    assertEquals(abFolder, folderView.getFolder("/a/b"));
    assertEquals(abFolder, folderView.getFolder("a/b/"));
    assertEquals(abFolder, folderView.getFolder("/a/b/"));
  }

  @Test
  public void removeNoteTest() {
    Note rootNote1 = rootFolder.getNotes().get(0);
    Note aNote1 = aFolder.getNotes().get(0);
    Note abNote1 = abFolder.getNotes().get(0);

    folderView.removeNote(rootNote1);
    folderView.removeNote(aNote1);
    folderView.removeNote(abNote1);

    assertEquals(3, folderView.countFolders());
    assertEquals(3, folderView.countNotes());
    assertEquals(1, rootFolder.countNotes());
    assertEquals(1, aFolder.countNotes());
    assertEquals(1, abFolder.countNotes());
  }

  @Test
  public void renameFolderOrdinaryTest() {
    // "a/b" -> "a/c"
    String oldName = "a/b";
    String newName = "a/c";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.getFolder(newName);

    assertNull(folderView.getFolder(oldName));
    assertNotNull(newFolder);

    assertEquals(3, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(abFolder, oldFolder);

    assertEquals(newName, abFolder.getId());
    assertEquals(newName, newFolder.getId());

    assertEquals(newName + "/note1", abNote1.getName());
    assertEquals(newName + "/note2", abNote2.getName());
  }

  @Test
  public void renameFolderTargetExistsAndHasChildTest() {
    // "a" -> "a/b"
    String oldName = "a";
    String newName = "a/b";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.getFolder(newName);

    assertNotNull(folderView.getFolder("a"));
    assertNotNull(folderView.getFolder("a/b"));
    assertNotNull(folderView.getFolder("a/b/b"));

    assertEquals(0, folderView.getFolder("a").countNotes());
    assertEquals(2, folderView.getFolder("a/b").countNotes());
    assertEquals(2, folderView.getFolder("a/b/b").countNotes());

    assertEquals(4, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(newName, aFolder.getId());
    assertEquals(newName, newFolder.getId());

    assertEquals(newName + "/note1", aNote1.getName());
    assertEquals(newName + "/note2", aNote2.getName());
    assertEquals(newName + "/b" + "/note1", abNote1.getName());
    assertEquals(newName + "/b" + "/note2", abNote2.getName());
  }

  @Test
  public void renameRootFolderTest() {
    String newName = "lalala";
    Folder nothing = folderView.renameFolder("/", newName);

    assertNull(nothing);
    assertNull(folderView.getFolder(newName));
  }

  @Test
  public void renameFolderToRootTest() {
    // "a/b" -> "/"
    String oldName = "a/b";
    String newName = "/";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.getFolder(newName);

    assertNull(folderView.getFolder(oldName));
    assertNotNull(newFolder);

    assertEquals(2, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(abFolder, oldFolder);
    assertEquals(rootFolder, newFolder);

    assertEquals(newName, rootFolder.getId());

    assertEquals("note1", abNote1.getName());
    assertEquals("note2", abNote2.getName());
  }

  @Test
  public void renameFolderNotExistsTest() {
    // "x/y/z" -> "a"
    String oldName = "x/y/z";
    String newName = "a";

    Folder oldFolder = folderView.renameFolder(oldName, newName);

    assertNull(oldFolder);
  }

  @Test
  public void renameFolderSameNameTest() {
    // "a" -> "a"
    String sameName = "a";

    Folder oldFolder = folderView.renameFolder(sameName, sameName);
    Folder newFolder = folderView.getFolder(sameName);

    assertEquals(aFolder, oldFolder);
    assertEquals(aFolder, newFolder);

    assertNotNull(folderView.getFolder(sameName));
    assertNotNull(newFolder);

    assertEquals(sameName, aFolder.getId());
  }

  /**
   * Should rename a empty folder
   */
  @Test
  public void renameEmptyFolderTest() {
    // Create a note of which name is "x/y/z" and rename "x" -> "u"

    Note note = createNote();
    note.setName("x/y/z");
    folderView.putNote(note);

    folderView.renameFolder("x", "u");

    assertNotNull(folderView.getFolder("u"));
    assertNotNull(folderView.getFolder("u/y"));
  }

  /**
   * Should also rename child folders of the target folder
   */
  @Test
  public void renameFolderHasChildrenTest() {
    // "a" -> "x"
    // "a/b" should also be renamed to "x/b"

    folderView.renameFolder("a", "x");

    assertNotNull(folderView.getFolder("x/b"));
  }

  @Test
  public void onNameChangedTest() {
    Note newNote = createNote();

    assert (!folderView.hasNote(newNote));

    newNote.setName("      ");
    assert (!folderView.hasNote(newNote));

    newNote.setName("a/b/newNote");
    assert (folderView.hasNote(newNote));
    assertEquals(abFolder, folderView.getFolderOf(newNote));

    newNote.setName("newNote");
    assert (!abFolder.getNotes().contains(newNote));
    assertEquals(rootFolder, folderView.getFolderOf(newNote));
  }

  @Test
  public void renameHighDepthFolderTest() {
    Note note = createNote();
    note.setName("x/y/z");

    Folder folder = folderView.getFolder("x");
    folder.rename("d");

    assertEquals("d/y/z", note.getName());

    assertNull(folderView.getFolder("x"));
    assertNotNull(folderView.getFolder("d"));
    assertNotNull(folderView.getFolder("d/y"));
  }

  @Test
  public void renameFolderMergingTest() {
    Note xNote1 = createNote();
    Note xbNote1 = createNote();

    xNote1.setName("x/note1");
    xbNote1.setName("x/b/note1");

    folderView.getFolder("a").rename("x");

    assertEquals(3, folderView.getFolder("x").countNotes());
    assertEquals(3, folderView.getFolder("x/b").countNotes());
  }
}
