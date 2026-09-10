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
package org.apache.zeppelin.socket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotebookServerStreamingScopeTest {

  private NotebookServer server;
  private Note note;
  private ConnectionManager connections;

  @BeforeEach
  void setUp() throws Exception {
    ZeppelinConfiguration conf = mock(ZeppelinConfiguration.class);
    when(conf.getBoolean(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_WEBSOCKET_PARAGRAPH_STATUS_PROGRESS))
        .thenReturn(true);
    note = new Note();
    note.setId("note");
    note.getParagraphs().add(new Paragraph("para", note, null));
    Notebook notebook = mock(Notebook.class);
    doAnswer(invocation -> {
      Notebook.NoteProcessor<?> processor = invocation.getArgument(1);
      return processor.process(note);
    }).when(notebook).processNote(eq("note"), any());
    connections = mock(ConnectionManager.class);
    server = new NotebookServer();
    server.setZeppelinConfiguration(conf);
    server.setNotebook(() -> notebook);
    server.setConnectionManager(connections);
  }

  @Test
  void sharedNoteReceivesIncrementalOutput() {
    server.onOutputAppend("note", "para", 0, "append");
    server.onOutputUpdated("note", "para", 0, InterpreterResult.Type.TEXT, "update");

    verify(connections, times(2)).broadcast(eq("note"), any());
  }

  @Test
  void personalizedNoteDoesNotReceiveUnownedIncrementalOutput() {
    note.setPersonalizedMode(true);

    server.onOutputAppend("note", "para", 0, "private append");
    server.onOutputUpdated("note", "para", 0, InterpreterResult.Type.TEXT, "private update");

    verify(connections, never()).broadcast(eq("note"), any());
    verify(connections, never()).multicastToUser(any(), any());
  }
}
