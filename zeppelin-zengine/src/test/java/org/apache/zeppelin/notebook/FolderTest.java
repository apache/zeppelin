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

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class FolderTest {
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

  Folder folder;

  Note note1;
  Note note2;
  Note note3;

  @Before
  public void createFolderAndNotes() {
    note1 = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    note1.setName("this/is/a/folder/note1");

    note2 = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    note2.setName("this/is/a/folder/note2");

    note3 = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    note3.setName("this/is/a/folder/note3");

    folder = new Folder("this/is/a/folder");
    folder.addNote(note1);
    folder.addNote(note2);
    folder.addNote(note3);
  }

  @Test
  public void normalizeFolderTest() {
    // The root folder tests
    folder.setIdAndRenameNotes("/");
    assertEquals(Folder.ROOT_FOLDER_ID, folder.getId());
    folder.setIdAndRenameNotes("//");
    assertEquals(Folder.ROOT_FOLDER_ID, folder.getId());
    folder.setIdAndRenameNotes("///");
    assertEquals(Folder.ROOT_FOLDER_ID, folder.getId());
    folder.setIdAndRenameNotes("\\\\///////////");
    assertEquals(Folder.ROOT_FOLDER_ID, folder.getId());

    // Folders under the root
    folder.setIdAndRenameNotes("a");
    assertEquals("a", folder.getId());
    folder.setIdAndRenameNotes("/a");
    assertEquals("a", folder.getId());
    folder.setIdAndRenameNotes("a/");
    assertEquals("a", folder.getId());
    folder.setIdAndRenameNotes("/a/");
    assertEquals("a", folder.getId());

    // Subdirectories
    folder.setIdAndRenameNotes("a/b/c");
    assertEquals("a/b/c", folder.getId());
    folder.setIdAndRenameNotes("/a/b/c");
    assertEquals("a/b/c", folder.getId());
    folder.setIdAndRenameNotes("a/b/c/");
    assertEquals("a/b/c", folder.getId());
    folder.setIdAndRenameNotes("/a/b/c/");
    assertEquals("a/b/c", folder.getId());
  }

  @Test
  public void folderIdTest() {
    assertEquals(note1.getFolderId(), folder.getId());
    assertEquals(note2.getFolderId(), folder.getId());
    assertEquals(note3.getFolderId(), folder.getId());
  }

  @Test
  public void addNoteTest() {
    Note note4 = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    note4.setName("this/is/a/folder/note4");

    folder.addNote(note4);

    assert(folder.getNotes().contains(note4));
  }

  @Test
  public void removeNoteTest() {
    folder.removeNote(note3);

    assert(!folder.getNotes().contains(note3));
  }

  @Test
  public void setIdTest() {
    // The root folder tests
    folder.setIdAndRenameNotes(Folder.ROOT_FOLDER_ID);

    assertEquals(Folder.ROOT_FOLDER_ID, note1.getFolderId());
    assertEquals(Folder.ROOT_FOLDER_ID, note2.getFolderId());
    assertEquals(Folder.ROOT_FOLDER_ID, note3.getFolderId());

    assertEquals("note1", note1.getName());
    assertEquals("note2", note2.getName());
    assertEquals("note3", note3.getName());

    // Subdirectory tests
    folder.setIdAndRenameNotes("renamed/folder");

    assertEquals("renamed/folder", note1.getFolderId());
    assertEquals("renamed/folder", note2.getFolderId());
    assertEquals("renamed/folder", note3.getFolderId());

    assertEquals("renamed/folder/note1", note1.getName());
    assertEquals("renamed/folder/note2", note2.getName());
    assertEquals("renamed/folder/note3", note3.getName());

    // Folders under the root tests
    folder.setIdAndRenameNotes("a");

    assertEquals("a", note1.getFolderId());
    assertEquals("a", note2.getFolderId());
    assertEquals("a", note3.getFolderId());

    assertEquals("a/note1", note1.getName());
    assertEquals("a/note2", note2.getName());
    assertEquals("a/note3", note3.getName());
  }
}
