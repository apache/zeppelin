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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 3: verifies the input=output (overwrite) and input!=output (new note, original
 * untouched) save branches. Reloads notes with {@code reload=true} to bypass the in-process
 * note cache and prove the assertions hold against what {@code VFSNotebookRepo} actually wrote
 * to disk, not just in-memory state.
 */
class NotebookRunnerOutputSaveTest {

  private CliTestFixtures.TestDirs dirs;
  private NotebookRunnerContext context;

  @BeforeEach
  void setUp() throws Exception {
    dirs = CliTestFixtures.setUp(NotebookRunnerOutputSaveTest.class);
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
  void noOutputPathOverwritesTheInputNoteInPlace() throws Exception {
    Notebook notebook = context.getNotebook();
    String noteId = createNoteWithParagraph("/save-inplace-note", "%mock1 in-place");

    NotebookRunner.run(context, RunNoteCliOptions.parse(new String[] {"-i", "/save-inplace-note"}));

    // Reload from disk (bypass cache) to prove the result was actually persisted.
    notebook.processNote(noteId, true, note -> {
      assertEquals(noteId, note.getId());
      assertEquals("/save-inplace-note", note.getPath());
      assertEquals("repl1: in-place",
          note.getParagraphs().get(0).getReturn().message().get(0).getData());
      return null;
    });
    assertEquals(noteId, notebook.getNoteIdByPath("/save-inplace-note"));
  }

  @Test
  @Timeout(30)
  void outputPathSavesANewNoteAndLeavesTheInputNoteUntouched() throws Exception {
    Notebook notebook = context.getNotebook();
    String inputNoteId = createNoteWithParagraph("/save-output-input", "%mock1 to-output");

    NotebookRunner.run(context, RunNoteCliOptions.parse(
        new String[] {"-i", "/save-output-input", "-o", "/save-output-result"}));

    String outputNoteId = notebook.getNoteIdByPath("/save-output-result");
    assertNotEquals(inputNoteId, outputNoteId);

    notebook.processNote(outputNoteId, true, note -> {
      assertEquals("repl1: to-output",
          note.getParagraphs().get(0).getReturn().message().get(0).getData());
      return null;
    });

    // Original input note, reloaded fresh from disk, must be untouched (no result attached).
    notebook.processNote(inputNoteId, true, note -> {
      assertEquals("/save-output-input", note.getPath());
      assertNull(note.getParagraphs().get(0).getReturn());
      return null;
    });
  }

  @Test
  @Timeout(30)
  void runThrowsWhenParagraphFailsButSavesPartialResult() throws Exception {
    Notebook notebook = context.getNotebook();
    String noteId = notebook.createNote("/save-failure-note", AuthenticationInfo.ANONYMOUS);
    notebook.processNote(noteId, note -> {
      Paragraph ok = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      ok.setText("%mock1 succeeds");
      Paragraph bad = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      bad.setText("%nonexistent boom");
      notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
      return null;
    });

    RunNoteCliOptions options = RunNoteCliOptions.parse(new String[] {"-i", "/save-failure-note"});
    assertThrows(IOException.class, () -> NotebookRunner.run(context, options));

    // Even though the run failed overall, the successful paragraph's result must have been
    // persisted -- a failing note must not silently lose the work it did complete.
    notebook.processNote(noteId, true, note -> {
      assertEquals("repl1: succeeds",
          note.getParagraphs().get(0).getReturn().message().get(0).getData());
      return null;
    });
  }

  @Test
  @Timeout(30)
  void outputPathCollisionWithExistingNoteThrows() throws Exception {
    createNoteWithParagraph("/save-collision-input", "%mock1 input");
    createNoteWithParagraph("/save-collision-existing", "%mock1 existing");

    RunNoteCliOptions options = RunNoteCliOptions.parse(
        new String[] {"-i", "/save-collision-input", "-o", "/save-collision-existing"});

    assertThrows(IOException.class, () -> NotebookRunner.run(context, options));
  }

  @Test
  @Timeout(30)
  void outputPathWithoutLeadingSlashThrowsBeforeExecution() throws Exception {
    String noteId = createNoteWithParagraph("/save-noslash-input", "%mock1 input");

    RunNoteCliOptions options = RunNoteCliOptions.parse(
        new String[] {"-i", "/save-noslash-input", "-o", "no-leading-slash"});

    assertThrows(IllegalArgumentException.class, () -> NotebookRunner.run(context, options));

    // Must have failed before execution: no result attached to the paragraph.
    context.getNotebook().processNote(noteId, true, note -> {
      assertNull(note.getParagraphs().get(0).getReturn());
      return null;
    });
  }
}
