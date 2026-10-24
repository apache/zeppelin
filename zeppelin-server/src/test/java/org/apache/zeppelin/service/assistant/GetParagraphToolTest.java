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

package org.apache.zeppelin.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Notebook.NoteProcessor;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.Test;

class GetParagraphToolTest {
  private final AuthenticationInfo authInfo = AuthenticationInfo.ANONYMOUS;
  private final Set<String> userAndRoles = Set.of("user");

  @Test
  void getsParagraph() throws Exception {
    var note = new Note();
    note.setInterpreterFactory(mock(InterpreterFactory.class));
    var notebook = mock(Notebook.class);
    when(notebook.processNote(eq("noteId"), eq(false), any())).thenAnswer(invocation ->
        ((NoteProcessor<?>) invocation.getArgument(2)).process(note));
    var authorization = mock(AuthorizationService.class);
    when(authorization.isReader("noteId", userAndRoles)).thenReturn(true);
    var sut = new GetParagraphTool(new NotebookService(notebook, authorization, null, null));

    var paragraph = note.addNewParagraph(authInfo);
    var text = "%python\n" + "x".repeat(600);
    paragraph.setText(text);
    paragraph.setTitle("Example");
    paragraph.setStatus(Status.FINISHED);
    paragraph.setResult(new InterpreterResult(
        InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TEXT, "x".repeat(20_000)));

    var actual = sut.call(
        "noteId",
        Map.of("paragraphId", paragraph.getId()),
        authInfo,
        userAndRoles
    );
    assertEquals(paragraph.getId(), actual.get("id"));
    assertEquals("Example", actual.get("title"));
    assertEquals(text, actual.get("input"));
    assertEquals("FINISHED", actual.get("status"));
  }

  @Test
  void rejectsNonReaders() throws Exception {
    var notebook = mock(Notebook.class);
    when(notebook.processNote(eq("noteId"), eq(false), any())).thenAnswer(invocation ->
        ((NoteProcessor<?>) invocation.getArgument(2)).process(new Note()));
    var authorization = mock(AuthorizationService.class);
    when(authorization.isReader("noteId", userAndRoles)).thenReturn(false);
    var sut = new GetParagraphTool(new NotebookService(notebook, authorization, null, null));

    assertThrows(
        ForbiddenException.class,
        () -> sut.call("noteId", Map.of("paragraphId", "any"), authInfo, userAndRoles)
    );
  }
}
