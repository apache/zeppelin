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
package org.apache.zeppelin.notebook.cli;

import org.apache.zeppelin.interpreter.RemoteInterpreterEventServer;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotebookRunnerContextTest {

  private CliTestFixtures.TestDirs dirs;
  private NotebookRunnerContext context;

  @BeforeEach
  void setUp() throws Exception {
    dirs = CliTestFixtures.setUp(NotebookRunnerContextTest.class);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (context != null) {
      context.close();
    }
    CliTestFixtures.tearDown(dirs);
  }

  @Test
  @Timeout(30)
  void bootstrapProducesWiredComponentsAndClosesCleanly() throws Exception {
    context = NotebookRunnerContext.bootstrap(dirs.zConf);

    assertNotNull(context.getNotebook());
    assertNotNull(context.getInterpreterSettingManager());
    assertNotNull(context.getInterpreterFactory());

    NotebookRunnerContext toClose = context;
    context = null;
    assertDoesNotThrow(toClose::close);
  }

  @Test
  @Timeout(30)
  void getParagraphListDelegatesToNotebookAndReturnsMatchingInfo() throws Exception {
    context = NotebookRunnerContext.bootstrap(dirs.zConf);
    Notebook notebook = context.getNotebook();
    String noteId = notebook.createNote("/paragraph-list-note", AuthenticationInfo.ANONYMOUS);

    notebook.processNote(noteId, note -> {
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p1.setTitle("first");
      p1.setText("%test.echo hello");
      notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
      return null;
    });

    List<ParagraphInfo> paragraphInfos =
        context.getProcessListener().getParagraphList("anonymous", noteId);

    assertEquals(1, paragraphInfos.size());
    assertEquals("first", paragraphInfos.get(0).getParagraphTitle());
    assertEquals("%test.echo hello", paragraphInfos.get(0).getParagraphText());
  }

  @Test
  @Timeout(30)
  void runParagraphsDelegatesToNotebookAndActuallyExecutesThem() throws Exception {
    context = NotebookRunnerContext.bootstrap(dirs.zConf);
    Notebook notebook = context.getNotebook();
    String noteId = notebook.createNote("/run-paragraphs-note", AuthenticationInfo.ANONYMOUS);

    String paragraphId = notebook.processNote(noteId, note -> {
      Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p1.setText("%mock1 hello-from-run-paragraphs");
      notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
      return p1.getId();
    });

    context.getProcessListener()
        .runParagraphs(noteId, Collections.emptyList(), Collections.emptyList(), "");

    await().atMost(15, TimeUnit.SECONDS).until(() -> notebook.processNote(noteId,
        note -> note.getParagraph(paragraphId).getReturn() != null));

    notebook.processNote(noteId, note -> {
      assertEquals("repl1: hello-from-run-paragraphs",
          note.getParagraph(paragraphId).getReturn().message().get(0).getData());
      return null;
    });
  }

  @Test
  @Timeout(15)
  void closeStopsTheEventServerSoItNoLongerAcceptsConnections() throws Exception {
    context = NotebookRunnerContext.bootstrap(dirs.zConf);
    RemoteInterpreterEventServer eventServer =
        context.getInterpreterSettingManager().getInterpreterEventServer();
    String host = eventServer.getHost();
    int port = eventServer.getPort();

    // Sanity check: the event server is actually listening before close() -- otherwise the
    // "connection refused after close()" assertion below would pass for the wrong reason.
    try (Socket before = new Socket(host, port)) {
      assertTrue(before.isConnected());
    }

    NotebookRunnerContext toClose = context;
    context = null;
    toClose.close();

    // close() must have stopped RemoteInterpreterEventServer's (non-daemon) Thrift server
    // thread -- otherwise that thread keeps the JVM alive forever after main() returns. Proven
    // black-box here (no accessor for the server's internal isServing() state) by observing
    // that the port it was listening on now refuses connections.
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
      try (Socket after = new Socket(host, port)) {
        return false;
      } catch (IOException e) {
        return true;
      }
    });
  }
}
