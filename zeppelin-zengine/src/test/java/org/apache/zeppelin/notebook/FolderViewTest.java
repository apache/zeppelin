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

    rootFolder = folderView.get("/");
    aFolder = folderView.get("a");
    abFolder = folderView.get("a/b");

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
    assertEquals(rootFolder, folderView.get("/"));

    assertEquals(aFolder, folderView.get("a"));
    assertEquals(aFolder, folderView.get("/a"));
    assertEquals(aFolder, folderView.get("a/"));
    assertEquals(aFolder, folderView.get("/a/"));

    assertEquals(abFolder, folderView.get("a/b"));
    assertEquals(abFolder, folderView.get("/a/b"));
    assertEquals(abFolder, folderView.get("a/b/"));
    assertEquals(abFolder, folderView.get("/a/b/"));
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
    Folder newFolder = folderView.get(newName);

    assertNull(folderView.get(oldName));
    assertNotNull(newFolder);

    assertEquals(3, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(abFolder, oldFolder);
    assertEquals(abFolder, newFolder);

    assertEquals(newName, abFolder.getId());

    assertEquals(newName + "/note1", abNote1.getName());
    assertEquals(newName + "/note2", abNote2.getName());
  }

  @Test
  public void renameFolderTargetExistsTest() {
    // "a" -> "a/b"
    String oldName = "a";
    String newName = "a/b";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.get(newName);

    assertNull(folderView.get(oldName));
    assertNotNull(newFolder);

    assertEquals(2, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(4, abFolder.countNotes());

    assertEquals(aFolder, oldFolder);
    assertEquals(abFolder, newFolder);

    assertEquals(newName, aFolder.getId());

    assertEquals(newName + "/note1", aNote1.getName());
    assertEquals(newName + "/note2", aNote2.getName());
  }

  @Test
  public void renameFolderFromRootTest() {
    // "/" -> "a/c"
    String oldName = "/";
    String newName = "a/c";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.get(newName);

    assertNull(folderView.get(oldName));
    assertNotNull(newFolder);

    assertEquals(3, folderView.countFolders());
    assertEquals(6, folderView.countNotes());

    assertEquals(rootFolder, oldFolder);
    assertEquals(rootFolder, newFolder);

    assertEquals(newName, rootFolder.getId());

    assertEquals(newName + "/note1", rootNote1.getName());
    assertEquals(newName + "/note2", rootNote2.getName());
  }

  @Test
  public void renameFolderToRootTest() {
    // "a/b" -> "/"
    String oldName = "a/b";
    String newName = "/";

    Folder oldFolder = folderView.renameFolder(oldName, newName);
    Folder newFolder = folderView.get(newName);

    assertNull(folderView.get(oldName));
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
    Folder newFolder = folderView.get(sameName);

    assertEquals(aFolder, oldFolder);
    assertEquals(aFolder, newFolder);

    assertNotNull(folderView.get(sameName));
    assertNotNull(newFolder);

    assertEquals(sameName, aFolder.getId());
  }

  @Test
  public void onNameChangedTest() {
    Note newNote = createNote();

    assert(!folderView.hasNote(newNote));

    newNote.setName("      ");
    assert(!folderView.hasNote(newNote));

    newNote.setName("a/b/newNote");
    assert(folderView.hasNote(newNote));
    assertEquals(abFolder, folderView.getFolderOf(newNote));

    newNote.setName("newNote");
    assert(!abFolder.getNotes().contains(newNote));
    assertEquals(rootFolder, folderView.getFolderOf(newNote));
  }
}
