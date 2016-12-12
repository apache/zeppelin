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

  private AuthenticationInfo anonymous = new AuthenticationInfo("anonymous");

  @Test
  public void runNormalTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);
    when(interpreter.getScheduler()).thenReturn(scheduler);

    String pText = "%spark sc.version";
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);

    Paragraph p = note.addParagraph();
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
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);

    Paragraph p = note.addParagraph();
    assertNull(p.getText());
  }

  @Test
  public void addParagraphWithLastReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addParagraph();
    p1.setText("%spark ");
    Paragraph p2 = note.addParagraph();

    assertEquals("%spark\n", p2.getText());
  }

  @Test
  public void insertParagraphWithLastReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("spark"))).thenReturn(interpreter);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addParagraph();
    p1.setText("%spark ");
    Paragraph p2 = note.insertParagraph(note.getParagraphs().size());

    assertEquals("%spark\n", p2.getText());
  }

  @Test
  public void insertParagraphWithInvalidReplNameTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("invalid"))).thenReturn(null);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addParagraph();
    p1.setText("%invalid ");
    Paragraph p2 = note.insertParagraph(note.getParagraphs().size());

    assertNull(p2.getText());
  }

  @Test
  public void clearAllParagraphOutputTest() {
    when(interpreterFactory.getInterpreter(anyString(), anyString(), eq("md"))).thenReturn(interpreter);
    when(interpreter.getScheduler()).thenReturn(scheduler);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
    Paragraph p1 = note.addParagraph();
    InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TEXT, "result");
    p1.setResult(result);

    Paragraph p2 = note.addParagraph();
    p2.setReturn(result, new Throwable());

    note.clearAllParagraphOutput();

    assertNull(p1.getReturn());
    assertNull(p2.getReturn());
  }

  @Test
  public void getFolderIdTest() {
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
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
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, noteEventListener);
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
}
