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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepoWithGetGate;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduction test for ZEPPELIN-5858. {@link NoteManager#moveNote} only re-saves a note (to
 * refresh the {@code path} field baked into its JSON) when the move changes the note's leaf
 * name, and it re-saves using the destination path that was passed into that specific
 * {@code moveNote} call, captured before the (possibly slow) reload from {@link NotebookRepo}.
 * If a second {@code moveNote} call for the same note (with the same leaf name, so it takes no
 * re-save path of its own) completes while the first call is still reloading the note, the
 * first call resumes and saves the note back at its own, now-stale destination path -- leaving
 * behind two {@code .zpln} files for the same noteId.
 *
 * <p>The scenario is pinned deterministically with {@link VFSNotebookRepoWithGetGate}, which
 * parks the reloading {@code get()} call after it has read the note from disk, and with the
 * note cache threshold lowered to 1 (evicting the target note via a filler note) so the reload
 * actually happens.
 */
class NoteManagerMoveResaveRaceTest {

  private static final String DEFAULT_INTERPRETER_GROUP = "test";
  private static final long JOIN_TIMEOUT_MILLIS = 30_000L;
  private static final long GATE_ARRIVAL_TIMEOUT_SECONDS = 30L;

  private File notebookDir;
  private Notebook notebook;
  private NoteManager noteManager;
  private VFSNotebookRepoWithGetGate notebookRepo;

  @BeforeEach
  void setUp() throws Exception {
    notebookDir = Files.createTempDirectory("notebookDir").toAbsolutePath().toFile();
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getAbsolutePath());
    // Must be set before NoteManager is constructed, since NoteCache reads the threshold once
    // at construction time.
    zConf.setProperty(ConfVars.ZEPPELIN_NOTE_CACHE_THRESHOLD.getVarName(), "1");

    NoteParser noteParser = new GsonNoteParser(zConf);
    ConfigStorage storage = ConfigStorage.createConfigStorage(zConf);
    notebookRepo = new VFSNotebookRepoWithGetGate();
    notebookRepo.init(zConf, noteParser);

    InterpreterSettingManager mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterFactory mockInterpreterFactory = mock(InterpreterFactory.class);
    Credentials credentials = new Credentials();
    noteManager = new NoteManager(notebookRepo, zConf);
    AuthorizationService authorizationService =
        new AuthorizationService(noteManager, zConf, storage);
    notebook =
        new Notebook(
            zConf,
            authorizationService,
            notebookRepo,
            noteManager,
            mockInterpreterFactory,
            mockInterpreterSettingManager,
            credentials,
            null);
    notebook.initNotebook();
    notebook.waitForFinishInit(1, TimeUnit.MINUTES);
  }

  @AfterEach
  void tearDown() {
    notebookDir.delete();
  }

  /**
   * Given a note evicted from the (threshold=1) note cache, when a second, unrelated-looking
   * {@code moveNote} call (same leaf name, so no re-save of its own) runs to completion while
   * the first {@code moveNote} call's re-save is still reloading the note from the repo, then
   * the first call must not resurrect a {@code .zpln} file at its own, now-stale destination.
   */
  @Test
  void testConcurrentMoveNoteResaveRace() throws Exception {
    String noteId = notebook.createNote(
        "/folder_0/note", DEFAULT_INTERPRETER_GROUP, AuthenticationInfo.ANONYMOUS, true);

    // A filler note pushes the target note out of the (threshold=1) cache, forcing the re-save
    // path in moveNote to reload it from the repo.
    notebook.createNote(
        "/filler", DEFAULT_INTERPRETER_GROUP, AuthenticationInfo.ANONYMOUS, true);
    assertEquals(1, noteManager.getCacheSize(),
        "creating the filler note should have evicted the target note from the cache; "
            + "the race scenario depends on a cache miss during moveNote's re-save");

    notebookRepo.armGate();

    List<Throwable> thread1Errors = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> thread2Errors = Collections.synchronizedList(new ArrayList<>());

    // Thread 1: rename note -> renamed. Leaf name changes, so moveNote reloads (cache miss)
    // and parks inside the gated get() call, having already read the (still current) note
    // path from disk.
    Thread thread1 = new Thread(() -> {
      try {
        notebook.moveNote(noteId, "/folder_1/renamed", AuthenticationInfo.ANONYMOUS);
      } catch (Throwable t) {
        thread1Errors.add(t);
      }
    }, "move-note-race-thread-1");
    thread1.start();

    assertTrue(
        notebookRepo.awaitArrival(GATE_ARRIVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "Thread 1's gated get() call never arrived. The scenario did not pin as expected: "
            + "either the target note was not evicted from the cache, or moveNote's re-save "
            + "path was not entered.");

    // Thread 2: rename renamed -> renamed (different folder, same leaf name), while thread 1
    // is parked. Leaf name is unchanged, so this move takes no re-save path of its own and
    // runs to completion using only the (fast) synchronized block in moveNote.
    Thread thread2 = new Thread(() -> {
      try {
        notebook.moveNote(noteId, "/folder_2/renamed", AuthenticationInfo.ANONYMOUS);
      } catch (Throwable t) {
        thread2Errors.add(t);
      }
    }, "move-note-race-thread-2");
    thread2.start();
    thread2.join(JOIN_TIMEOUT_MILLIS);
    assertFalse(thread2.isAlive(), "Thread 2's moveNote did not finish within the timeout");

    // Only now let thread 1 resume: it will save the reloaded note back at its own, stale
    // destination path ("/folder_1/renamed"), even though thread 2 already moved the note to
    // "/folder_2/renamed".
    notebookRepo.release();
    thread1.join(JOIN_TIMEOUT_MILLIS);
    assertFalse(thread1.isAlive(), "Thread 1's moveNote did not finish within the timeout");

    assertTrue(thread1Errors.isEmpty(), () -> "Thread 1 threw: " + thread1Errors);
    assertTrue(thread2Errors.isEmpty(), () -> "Thread 2 threw: " + thread2Errors);

    List<String> zplnFilesForNote = findZplnFilesForNote(noteId);
    assertEquals(1, zplnFilesForNote.size(),
        () -> "Expected exactly one .zpln file for note " + noteId + ", but found: "
            + zplnFilesForNote);
  }

  private List<String> findZplnFilesForNote(String noteId) throws IOException {
    Path notebookPath = notebookDir.toPath();
    try (Stream<Path> paths = Files.walk(notebookPath)) {
      return paths
          .filter(p -> p.toString().endsWith("_" + noteId + ".zpln"))
          .map(p -> notebookPath.relativize(p).toString())
          .collect(Collectors.toList());
    }
  }
}
