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
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NoteTest {
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

  @Mock
  InterpreterSettingManager interpreterSettingManager;

  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  @Test
  public void runNormalTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);
    when(interpreter.getScheduler()).thenReturn(scheduler);

    String pText = "%spark sc.version";
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p.setText(pText);
    p.setAuthenticationInfo(anonymous);
    note.run(p.getId());

    ArgumentCaptor<Paragraph> pCaptor = ArgumentCaptor.forClass(Paragraph.class);
    verify(scheduler, only()).submit(pCaptor.capture());
    verify(interpreterFactory, times(2)).getInterpreter(anyString(), anyString(), eq("spark"));

    assertEquals("Paragraph text", pText, pCaptor.getValue().getText());
  }

  @Test
  public void addParagraphWithEmptyReplNameTest() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);

    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    assertNull(p.getText());
  }

  @Test
  public void addParagraphWithLastReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);

    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%spark ");
    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);

    assertEquals("%spark\n", p2.getText());
  }

  @Test
  public void insertParagraphWithLastReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);

    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%spark ");
    Paragraph p2 = note.insertNewParagraph(note.getParagraphs().size(), AuthenticationInfo.ANONYMOUS);

    assertEquals("%spark\n", p2.getText());
  }

  @Test
  public void insertParagraphWithInvalidReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("invalid"))).thenReturn(null);

    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p1.setText("%invalid ");
    Paragraph p2 = note.insertNewParagraph(note.getParagraphs().size(), AuthenticationInfo.ANONYMOUS);

    assertNull(p2.getText());
  }

  @Test
  public void insertParagraphwithUser() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p = note.insertNewParagraph(note.getParagraphs().size(), AuthenticationInfo.ANONYMOUS);
    assertEquals("anonymous", p.getUser());
  }

  @Test
  public void clearAllParagraphOutputTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("md"))).thenReturn(interpreter);
    when(interpreter.getScheduler()).thenReturn(scheduler);

    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TEXT, "result");
    p1.setResult(result);

    Paragraph p2 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    p2.setReturn(result, new Throwable());

    note.clearAllParagraphOutput();

    assertNull(p1.getReturn());
    assertNull(p2.getReturn());
  }

  @Test
  public void getFolderIdTest() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    // Ordinary case test
    note.setName("this/is/a/folder/noteName");
    assertEquals("this/is/a/folder", note.getFolderId());
    // Normalize test
    note.setName("/this/is/a/folder/noteName");
    assertEquals("this/is/a/folder", note.getFolderId());
    // Root folder test
    note.setName("noteOnRootFolder");
    assertEquals(Folder.ROOT_FOLDER_ID, note.getFolderId());
    note.setName("/noteOnRootFolderStartsWithSlash");
    assertEquals(Folder.ROOT_FOLDER_ID, note.getFolderId());
  }

  @Test
  public void getNameWithoutPathTest() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    // Notes in the root folder
    note.setName("noteOnRootFolder");
    assertEquals("noteOnRootFolder", note.getNameWithoutPath());
    note.setName("/noteOnRootFolderStartsWithSlash");
    assertEquals("noteOnRootFolderStartsWithSlash", note.getNameWithoutPath());
    // Notes in subdirectories
    note.setName("/a/b/note");
    assertEquals("note", note.getNameWithoutPath());
    note.setName("a/b/note");
    assertEquals("note", note.getNameWithoutPath());
  }

  @Test
  public void isTrashTest() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);
    // Notes in the root folder
    note.setName("noteOnRootFolder");
    assertFalse(note.isTrash());
    note.setName("/noteOnRootFolderStartsWithSlash");
    assertFalse(note.isTrash());

    // Notes in subdirectories
    note.setName("/a/b/note");
    assertFalse(note.isTrash());
    note.setName("a/b/note");
    assertFalse(note.isTrash());

    // Notes in trash
    note.setName(Folder.TRASH_FOLDER_ID + "/a");
    assertTrue(note.isTrash());
    note.setName("/" + Folder.TRASH_FOLDER_ID + "/a");
    assertTrue(note.isTrash());
    note.setName(Folder.TRASH_FOLDER_ID + "/a/b/c");
    assertTrue(note.isTrash());
  }

  @Test
  public void getNameWithoutNameItself() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);

    assertEquals("getName should return same as getId when name is empty", note.getId(), note.getName());
  }

  @Test
  public void personalizedModeReturnDifferentParagraphInstancePerUser() {
    Note note = new Note(repo, interpreterFactory, interpreterSettingManager, jobListenerFactory, index, credentials, noteEventListener);

    String user1 = "user1";
    String user2 = "user2";
    note.setPersonalizedMode(true);
    note.addNewParagraph(new AuthenticationInfo(user1));
    Paragraph baseParagraph = note.getParagraphs().get(0);
    Paragraph user1Paragraph = baseParagraph.getUserParagraph(user1);
    Paragraph user2Paragraph = baseParagraph.getUserParagraph(user2);
    assertNotEquals(System.identityHashCode(baseParagraph), System.identityHashCode(user1Paragraph));
    assertNotEquals(System.identityHashCode(baseParagraph), System.identityHashCode(user2Paragraph));
    assertNotEquals(System.identityHashCode(user1Paragraph), System.identityHashCode(user2Paragraph));
  }
}
