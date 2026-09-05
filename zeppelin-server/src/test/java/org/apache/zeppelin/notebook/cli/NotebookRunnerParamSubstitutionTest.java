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

import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.apache.zeppelin.scheduler.Job.Status.ERROR;

class NotebookRunnerParamSubstitutionTest {

  private CliTestFixtures.TestDirs dirs;
  private NotebookRunnerContext context;

  @BeforeEach
  void setUp() throws Exception {
    dirs = CliTestFixtures.setUp(NotebookRunnerParamSubstitutionTest.class);
    context = NotebookRunnerContext.bootstrap(dirs.zConf);
  }

  @AfterEach
  void tearDown() throws Exception {
    context.close();
    CliTestFixtures.tearDown(dirs);
  }

  private String createNoteWithParagraph(String path, String scriptText) throws Exception {
    Notebook notebook = context.getNotebook();
    String noteId = notebook.createNote(path, AuthenticationInfo.ANONYMOUS);
    notebook.processNote(noteId, note -> {
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setText(scriptText);
      notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
      return null;
    });
    return noteId;
  }

  @Test
  @Timeout(30)
  void explicitParamOverridesDefaultValue() throws Exception {
    String noteId = createNoteWithParagraph("/param-note-1", "%mock1 Hello ${name=World}");

    RunNoteCliOptions options = RunNoteCliOptions.parse(
        new String[] {"-i", "/param-note-1", "-p", "name", "Zeppelin"});
    NotebookRunner.run(context, options);

    context.getNotebook().processNote(noteId, note -> {
      Paragraph p = note.getParagraphs().get(0);
      assertNotEquals(ERROR, p.getStatus());
      assertEquals("repl1: Hello Zeppelin", p.getReturn().message().get(0).getData());
      return null;
    });
  }

  @Test
  @Timeout(30)
  void missingParamFallsBackToDefaultValue() throws Exception {
    String noteId = createNoteWithParagraph("/param-note-2", "%mock1 Hello ${name=World}");

    RunNoteCliOptions options = RunNoteCliOptions.parse(new String[] {"-i", "/param-note-2"});
    NotebookRunner.run(context, options);

    context.getNotebook().processNote(noteId, note -> {
      Paragraph p = note.getParagraphs().get(0);
      assertNotEquals(ERROR, p.getStatus());
      assertEquals("repl1: Hello World", p.getReturn().message().get(0).getData());
      return null;
    });
  }

  @Test
  @Timeout(30)
  void repeatedParamOptionsSubstituteAllKeys() throws Exception {
    String noteId = createNoteWithParagraph("/param-note-3",
        "%mock1 ${greeting=Hi} ${name=World}");

    RunNoteCliOptions options = RunNoteCliOptions.parse(new String[] {
        "-i", "/param-note-3",
        "-p", "greeting", "Hello",
        "-p", "name", "Zeppelin"});
    NotebookRunner.run(context, options);

    context.getNotebook().processNote(noteId, note -> {
      Paragraph p = note.getParagraphs().get(0);
      assertNotEquals(ERROR, p.getStatus());
      assertEquals("repl1: Hello Zeppelin", p.getReturn().message().get(0).getData());
      return null;
    });
  }

  @Test
  void parseFailsFastWhenNotePathMissing() {
    assertThrows(IllegalArgumentException.class,
        () -> RunNoteCliOptions.parse(new String[] {"-p", "name", "Zeppelin"}));
  }

  @Test
  void parseFailsFastWhenInputValueMissing() {
    assertThrows(IllegalArgumentException.class,
        () -> RunNoteCliOptions.parse(new String[] {"-i"}));
  }

  @Test
  void parseFailsFastWhenOutputValueMissing() {
    assertThrows(IllegalArgumentException.class,
        () -> RunNoteCliOptions.parse(new String[] {"-i", "n.zpln", "-o"}));
  }

  @Test
  void parseFailsFastWhenParamValueMissing() {
    assertThrows(IllegalArgumentException.class,
        () -> RunNoteCliOptions.parse(new String[] {"-i", "n.zpln", "-p", "key"}));
  }
}
