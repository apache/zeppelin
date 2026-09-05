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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.IdHashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for running a Zeppelin note without starting the Zeppelin server (no
 * Jetty/REST/WebSocket). Executes every paragraph of the given note via
 * {@link Note#runAll}, substituting {@code ${param}} placeholders with values supplied via
 * {@code -p}, then saves the executed note back (overwriting the input note, or to a new note
 * when {@code -o} is given). Any paragraph left in a non-{@code FINISHED} state (error, abort,
 * or skipped because an earlier paragraph failed) fails the run with an exception, so a CI/batch
 * caller relying on the process exit code sees the failure.
 */
public final class NotebookRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRunner.class);

  private NotebookRunner() {
  }

  public static void main(String[] args) throws Exception {
    RunNoteCliOptions options = RunNoteCliOptions.parse(args);
    if (options == null) {
      // -h/--help was given, usage already printed.
      return;
    }

    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    int exitCode = 0;
    // try-with-resources: if run() throws and close() also throws, run()'s exception is the one
    // propagated (with close()'s exception attached via Throwable#addSuppressed), instead of
    // close() silently masking the real failure.
    try (NotebookRunnerContext context = NotebookRunnerContext.bootstrap(zConf)) {
      run(context, options);
    } catch (Exception e) {
      LOGGER.error("Failed to run note", e);
      System.err.println("Run failed: " + e.getMessage());
      exitCode = 1;
    }
    // ExecutorFactory#shutdownAll() belongs here, in main(), rather than in
    // NotebookRunnerContext#close(): ExecutorFactory.singleton() is a JVM-wide singleton, not
    // owned by any one context, so tearing it down is only correct once this process is truly
    // done with it -- exactly the point main() reaches right here. (It must not live in
    // close(): that method also runs for every NotebookRunnerContext a test suite creates and
    // closes in-process; killing the shared pool there breaks every subsequent test sharing the
    // JVM, since e.g. SchedulerFactory lazily creates its backing executor once via
    // ExecutorFactory and keeps reusing that same reference for the rest of the JVM's life.)
    ExecutorFactory.singleton().shutdownAll();
    // Safety net: everything above already tries to shut down every thread pool it knows about
    // (interpreter processes, the event server, RemoteScheduler's own executors, ExecutorFactory's
    // named pools) so the JVM can exit on its own. System.exit() here is the backstop for
    // whatever it doesn't know about -- some interpreter or future scheduler variant spinning up
    // a non-daemon thread this CLI layer has no visibility into -- and it also carries the
    // failure signal (HIGH-1) out as a real process exit code for CI/batch callers.
    System.exit(exitCode);
  }

  static void run(NotebookRunnerContext context, RunNoteCliOptions options) throws IOException {
    Notebook notebook = context.getNotebook();
    String noteId = notebook.getNoteIdByPath(options.getNotePath());
    if (noteId == null) {
      throw new IOException("Note not found: " + options.getNotePath());
    }

    String outputPath = options.getOutputPath();
    if (outputPath != null) {
      if (!outputPath.startsWith("/")) {
        throw new IllegalArgumentException(
            "-o <outputPath> must be an absolute note path starting with '/': " + outputPath);
      }
      if (notebook.containsNote(outputPath)) {
        throw new IOException("Output note already exists at path: " + outputPath);
      }
    }

    notebook.processNote(noteId, note -> {
      try {
        note.runAll(AuthenticationInfo.ANONYMOUS, true, false, options.getParams());
      } catch (Exception e) {
        throw new IOException("Failed to run note: " + options.getNotePath(), e);
      }
      // Save whatever was produced -- including partial results from a run that failed partway
      // through -- before deciding whether the run itself should be reported as a failure.
      saveResult(notebook, note, options);
      // Paragraph output (stdout, via HeadlessProcessListener) may not end with a newline (e.g.
      // Python's print(..., end=' ')), so force a line break before the completion summary --
      // otherwise it visually runs into the last paragraph's output on the terminal.
      System.out.flush();
      System.err.println();
      System.err.println("Saved executed note to " + note.getPath());
      failIfAnyParagraphDidNotFinish(note);
      return null;
    });
  }

  private static void saveResult(Notebook notebook, Note note, RunNoteCliOptions options)
      throws IOException {
    if (options.getOutputPath() != null) {
      note.setId(IdHashes.generateId());
      note.setPath(options.getOutputPath());
    }
    notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
  }

  /**
   * @throws IOException listing every enabled paragraph left in a non-{@code FINISHED} state
   *     (error, abort, or skipped after an earlier paragraph failed), so a CI/batch caller sees
   *     a non-zero exit instead of a silently incomplete run.
   */
  private static void failIfAnyParagraphDidNotFinish(Note note) throws IOException {
    List<String> failedParagraphIds = new ArrayList<>();
    int enabledCount = 0;
    for (Paragraph p : note.getParagraphs()) {
      if (!p.isEnabled()) {
        continue;
      }
      enabledCount++;
      if (p.getStatus() != Job.Status.FINISHED) {
        failedParagraphIds.add(p.getId());
      }
    }

    if (enabledCount == 0) {
      LOGGER.warn("Note {} has no enabled paragraph to run", note.getPath());
      return;
    }

    int succeededCount = enabledCount - failedParagraphIds.size();
    LOGGER.info("Note {} finished: {}/{} paragraphs succeeded", note.getPath(), succeededCount,
        enabledCount);
    System.err.println("Note " + note.getPath() + " finished: " + succeededCount + "/"
        + enabledCount + " paragraphs succeeded");

    if (!failedParagraphIds.isEmpty()) {
      throw new IOException("Note " + note.getPath() + " has " + failedParagraphIds.size()
          + " failed/incomplete paragraph(s): " + failedParagraphIds);
    }
  }
}
