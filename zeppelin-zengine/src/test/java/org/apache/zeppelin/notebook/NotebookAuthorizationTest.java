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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

public class NotebookAuthorizationTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  NotebookAuthorization notebookAuthorization;
  FolderView folderView;

  @Before
  public void setUp() throws Exception {
    NotebookAuthorization.setPersist(false);
    notebookAuthorization = NotebookAuthorization.init(ZeppelinConfiguration.create());
    folderView = new FolderView();
    notebookAuthorization.setFolderView(folderView);
  }

  private Note addNote(String name) {
    Note note = new Note(null, null, null,
        null, null, null, null);
    note.setName(name);
    folderView.putNote(note);
    return note;
  }

  private Set<String> generateEntities() {
    Set<String> entities = new HashSet<>();
    entities.add("username");
    entities.add("admin");
    return entities;
  }

  @Test
  public void cannotSetPermissionsToRootFolder() throws Exception {
    exception.expect(RuntimeException.class);
    notebookAuthorization.setOwners("/", null);
  }

  @Test
  public void cannotSetPermissionsToNoteUnderFolderWithPermissions() throws Exception {
    Note note = addNote("/f1/f2/a");
    notebookAuthorization.setOwners("/f1", Collections.singleton("username"));
    String noteId = note.getId();
    exception.expect(RuntimeException.class);
    notebookAuthorization.setOwners(noteId, Collections.singleton("username1"));
  }

  @Test
  public void cannotSetPermissionsToFolderUnderFolderWithPermissions() throws Exception {
    Note note = addNote("/f1/f2/a");
    notebookAuthorization.setOwners("/f1", Collections.singleton("username"));
    String noteId = note.getId();
    exception.expect(RuntimeException.class);
    notebookAuthorization.setOwners("/f1/f2", Collections.singleton("username1"));
  }

  @Test
  public void canSetPermissionsToFolderUnderFolderWithPermissionsIfMatch() throws Exception {
    Note note = addNote("/f1/f2/a");
    notebookAuthorization.setOwners("/f1", Collections.singleton("username"));
    String noteId = note.getId();
    notebookAuthorization.setOwners("/f1/f2", Collections.singleton("username"));
  }

  @Test
  public void checkOwnersSetForNoteUnderFolderWithPermissions() throws Exception {
    Set<String> entities = generateEntities();
    Note note = addNote("/f1/f2/a");
    notebookAuthorization.setOwners("/f1", entities);
    String noteId = note.getId();
    assertEquals(entities, notebookAuthorization.getOwners(noteId));
  }

  @Test
  public void checkOwnerIsWriter() throws Exception {
    Note note = addNote("a");
    Set<String> entities = generateEntities();
    notebookAuthorization.setOwners(note.getId(), entities);
    assertTrue(notebookAuthorization.isWriter(note.getId(), entities));
  }

  @Test
  public void checkWriterIsReader() throws Exception {
    Note note = addNote("a");
    Set<String> entities = generateEntities();
    notebookAuthorization.setWriters(note.getId(), entities);
    assertTrue(notebookAuthorization.isReader(note.getId(), entities));
  }

  @Test
  public void testSetNewNotePermissionsNotPublicUnderFolderWithPermissionsOk() throws Exception {
    try {
      System.setProperty("zeppelin.notebook.public", "false");
      String folderName = "/f";
      addNote(folderName + "/a");
      Set<String> entities = new HashSet<>();
      entities.add("user1");
      notebookAuthorization.setOwners(folderName, entities);
      notebookAuthorization.setWriters(folderName, entities);
      notebookAuthorization.setReaders(folderName, entities);
      notebookAuthorization.setRunners(folderName, entities);
      Note note = addNote("/f/b");
      notebookAuthorization.setNewNotePermissions(note.getId(), new AuthenticationInfo("user1"));
    } finally {
      System.setProperty("zeppelin.notebook.public", "true");
    }
  }

  @Test
  public void testSetNewNotePermissionsNotPublicUnderFolderWithPermissionsBad() throws Exception {
    try {
      System.setProperty("zeppelin.notebook.public", "false");
      String folderName = "/f";
      addNote(folderName + "/a");
      Set<String> entities = new HashSet<>();
      entities.add("user1");
      notebookAuthorization.setOwners(folderName, entities);
      notebookAuthorization.setWriters(folderName, entities);
      notebookAuthorization.setReaders(folderName, entities);
      Note note = addNote("/f/b");
      exception.expect(RuntimeException.class);
      notebookAuthorization.setNewNotePermissions(note.getId(), new AuthenticationInfo("user2"));
    } finally {
      System.setProperty("zeppelin.notebook.public", "true");
    }
  }

  @Test
  public void testSetNewNotePermissionsPublicUnderFolderWithPermissions() throws Exception {
    String folderName = "/f";
    addNote(folderName + "/a");
    Set<String> owners = new HashSet<>();
    owners.add("user1");
    Set<String> writers = new HashSet<>();
    writers.add("user2, user3");
    Set<String> readers = new HashSet<>();
    readers.add("user4");
    notebookAuthorization.setOwners(folderName, owners);
    notebookAuthorization.setWriters(folderName, writers);
    notebookAuthorization.setReaders(folderName, readers);
    Note note = addNote("/f/b");
    String noteId = note.getId();
    notebookAuthorization.setNewNotePermissions(note.getId(), new AuthenticationInfo("user1"));
    assertEquals(owners, notebookAuthorization.getOwners(noteId));
    assertEquals(writers, notebookAuthorization.getWriters(noteId));
    assertEquals(readers, notebookAuthorization.getReaders(noteId));
  }

  @Test
  public void testSetNewNotePermissionsPublic() throws Exception {
    Note note = addNote("a");
    notebookAuthorization.setNewNotePermissions(note.getId(), new AuthenticationInfo("user1"));
    Set<String> entities = new HashSet<>();
    entities.add("user1");
    String noteId = note.getId();
    notebookAuthorization.setOwners(noteId, entities);
    notebookAuthorization.setWriters(noteId, new HashSet<String>());
    notebookAuthorization.setReaders(noteId, new HashSet<String>());
  }

  @Test
  public void testCanSetPermissionsAfterSetAndRemoveParentFolderPermissions() throws Exception {
    Note note = addNote("/f/a");
    String folderName = "/f";
    addNote(folderName + "/a");
    Set<String> entities = new HashSet<>();
    entities.add("user1");
    notebookAuthorization.setOwners(folderName, entities);
    notebookAuthorization.setOwners(folderName, new HashSet<String>());
    Set<String> entities2 = new HashSet<>();
    entities.add("user2");
    notebookAuthorization.setOwners(note.getId(), entities2);
  }

}