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
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5, the completion-criteria proof: drives the CLI end to end as a real, separate JVM
 * process via {@code bin/run-note.sh} (no server, no mocked note/paragraph) and asserts on the
 * one thing an in-process call to {@link NotebookRunner#main} can never observe: whether the
 * process actually exits.
 *
 * <p>This has to run out-of-process for two independent reasons: (1) proving there's no JVM
 * hang requires watching an external process die on its own -- a same-JVM call can't see that,
 * the surefire JVM is alive for its own reasons regardless of what {@code close()} did; and (2)
 * {@link NotebookRunner#main} now calls {@code System.exit()} as a last-resort cleanup net, which
 * would kill the test runner itself if called in-process.
 */
class NotebookRunnerIntegrationTest {

  private CliTestFixtures.TestDirs dirs;

  @BeforeEach
  void setUp() throws Exception {
    dirs = CliTestFixtures.setUp(NotebookRunnerIntegrationTest.class);
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName(),
        dirs.zeppelinHome.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName(),
        dirs.confDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_DIR.getVarName(),
        dirs.interpreterDir.getAbsolutePath());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        dirs.notebookDir.getAbsolutePath());
    System.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName(), "test");

    // Fixture notes, written directly through a throwaway context so the subprocess only has to
    // run them.
    try (NotebookRunnerContext setupContext = NotebookRunnerContext.bootstrap(dirs.zConf)) {
      Notebook notebook = setupContext.getNotebook();

      String successNoteId = notebook.createNote("/integration-note", AuthenticationInfo.ANONYMOUS);
      notebook.processNote(successNoteId, note -> {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("%mock1 ${msg=default}");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });

      String failureNoteId =
          notebook.createNote("/integration-failure-note", AuthenticationInfo.ANONYMOUS);
      notebook.processNote(failureNoteId, note -> {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("%nonexistent boom");
        notebook.saveNote(note, AuthenticationInfo.ANONYMOUS);
        return null;
      });
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.getVarName());
    System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONF_DIR.getVarName());
    System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_DIR.getVarName());
    System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName());
    System.clearProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT.getVarName());
    CliTestFixtures.tearDown(dirs);
  }

  @Test
  @Timeout(90)
  void mainExitsZeroSavesResultAndLeavesNoOrphanProcessWhenNoteSucceeds() throws Exception {
    SubprocessResult result =
        runNoteScript("-i", "/integration-note", "-p", "msg", "subprocess-hello");

    assertTrue(result.exited,
        "run-note.sh subprocess did not exit within 60s -- JVM hang. Output so far:\n"
            + result.output);
    assertEquals(0, result.exitCode, "Unexpected exit code. Output:\n" + result.output);

    // Verify the saved note independently, through a fresh context.
    try (NotebookRunnerContext verifyContext = NotebookRunnerContext.bootstrap(dirs.zConf)) {
      Notebook notebook = verifyContext.getNotebook();
      String noteId = notebook.getNoteIdByPath("/integration-note");
      notebook.processNote(noteId, true, note -> {
        assertEquals("repl1: subprocess-hello",
            note.getParagraphs().get(0).getReturn().message().get(0).getData());
        return null;
      });
    }

    // Confirm at the OS level (not just in-JVM bookkeeping) that no interpreter subprocess
    // survived the parent CLI process exiting. orphanPids is every descendant pid observed
    // under the run-note.sh process (which includes the interpreter subprocess it spawns)
    // that is still alive now that the parent has exited.
    assertTrue(result.orphanPids.isEmpty(),
        "Orphan descendant process(es) survived parent exit: " + result.orphanPids);
  }

  @Test
  @Timeout(90)
  void mainExitsOneWhenParagraphFails() throws Exception {
    SubprocessResult result = runNoteScript("-i", "/integration-failure-note");

    assertTrue(result.exited,
        "run-note.sh subprocess did not exit within 60s -- JVM hang. Output so far:\n"
            + result.output);
    assertEquals(1, result.exitCode, "Unexpected exit code. Output:\n" + result.output);
  }

  private static final class SubprocessResult {
    final boolean exited;
    final int exitCode;
    final String output;
    /**
     * Descendant pids of the run-note.sh process (collected while it was still alive, since
     * {@link Process#descendants()} stops reporting anything useful once the parent has
     * terminated) that are still alive now that the parent has exited.
     */
    final List<Long> orphanPids;

    SubprocessResult(boolean exited, int exitCode, String output, List<Long> orphanPids) {
      this.exited = exited;
      this.exitCode = exitCode;
      this.output = output;
      this.orphanPids = orphanPids;
    }
  }

  /**
   * Runs {@code bin/run-note.sh} as a genuinely separate JVM process against this test's fixture
   * dirs. Uses the script rather than a raw {@code java -cp ...} child process because surefire's
   * default fork mode uses a manifest-only jar for its own classpath -- {@code
   * System.getProperty("java.class.path")} inside this test JVM would not be directly reusable
   * for a child {@code java -cp} invocation, while the script assembles its own classpath from
   * {@code ZEPPELIN_HOME}.
   */
  private SubprocessResult runNoteScript(String... args) throws Exception {
    File runNoteScript = new File(dirs.zeppelinHome, "bin/run-note.sh");
    assertTrue(runNoteScript.isFile(), "bin/run-note.sh not found at " + runNoteScript);

    java.util.List<String> command = new java.util.ArrayList<>();
    command.add(runNoteScript.getAbsolutePath());
    command.addAll(Arrays.asList(args));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    pb.environment().put("ZEPPELIN_HOME", dirs.zeppelinHome.getAbsolutePath());
    pb.environment().put("ZEPPELIN_CONF_DIR", dirs.confDir.getAbsolutePath());
    pb.environment().put("ZEPPELIN_LOG_DIR",
        new File(dirs.zeppelinHome, "logs_" + getClass().getSimpleName()).getAbsolutePath());
    pb.environment().put("ZEPPELIN_JAVA_OPTS",
        "-Dzeppelin.home=" + dirs.zeppelinHome.getAbsolutePath()
            + " -Dzeppelin.conf.dir=" + dirs.confDir.getAbsolutePath()
            + " -Dzeppelin.interpreter.dir=" + dirs.interpreterDir.getAbsolutePath()
            + " -Dzeppelin.notebook.dir=" + dirs.notebookDir.getAbsolutePath()
            + " -Dzeppelin.interpreter.group.default=test");

    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    Thread drain = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append(System.lineSeparator());
        }
      } catch (Exception e) {
        // best-effort draining only
      }
    });
    drain.setDaemon(true);
    drain.start();

    // run-note.sh forks its own java child (NotebookRunner), which in turn forks the
    // interpreter subprocess (RemoteInterpreterServer) -- both are descendants of `process`.
    // Process#descendants() only reliably walks that tree while the parent is still alive, so
    // we have to poll and union pids while run-note.sh is running rather than snapshot once
    // after it exits.
    Set<Long> descendantPids = ConcurrentHashMap.newKeySet();
    AtomicBoolean stopPolling = new AtomicBoolean(false);
    Thread descendantPoller = new Thread(() -> {
      while (!stopPolling.get()) {
        process.descendants().map(ProcessHandle::pid).forEach(descendantPids::add);
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    });
    descendantPoller.setDaemon(true);
    descendantPoller.start();

    boolean exited = process.waitFor(60, TimeUnit.SECONDS);
    if (!exited) {
      process.destroyForcibly();
    }
    stopPolling.set(true);
    descendantPoller.join(TimeUnit.SECONDS.toMillis(5));
    drain.join(TimeUnit.SECONDS.toMillis(5));

    List<Long> orphanPids = descendantPids.stream()
        .filter(pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
        .collect(Collectors.toList());

    return new SubprocessResult(
        exited, exited ? process.exitValue() : -1, output.toString(), orphanPids);
  }
}
