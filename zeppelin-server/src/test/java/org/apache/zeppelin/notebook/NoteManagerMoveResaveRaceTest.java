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
import org.apache.zeppelin.notebook.repo.VFSNotebookRepoWithGetGate;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test for ZEPPELIN-5858 and ZEPPELIN-6595. {@link NoteManager#moveNote} used to
 * reload and re-save a note after a rename so that the {@code name} field in its JSON matched
 * the new leaf name. That reload could race with a concurrent move and leave two {@code .zpln}
 * files for the same noteId. Since ZEPPELIN-6595 the leaf name is derived from the note tree
 * on reload, so {@code moveNote} no longer reloads or re-saves at all.
 *
 * <p>{@link VFSNotebookRepoWithGetGate} detects any {@code get()} call made by
 * {@code moveNote}, and the note cache threshold is lowered to 1 (evicting the target note via
 * a filler note) so that such a call would have to go to the repo.
 */
class NoteManagerMoveResaveRaceTest {

  private static final String DEFAULT_INTERPRETER_GROUP = "test";
  private static final long JOIN_TIMEOUT_MILLIS = 30_000L;

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
   * Given a note evicted from the (threshold=1) note cache, when it is renamed (leaf name
   * changes) and then moved again, then {@code moveNote} must not reload the note from the
   * repo, exactly one {@code .zpln} file remains, and a reload reports the latest path.
   */
  @Test
  void testRenameDoesNotReloadOrResave() throws Exception {
    String noteId = notebook.createNote(
        "/folder_0/note", DEFAULT_INTERPRETER_GROUP, AuthenticationInfo.ANONYMOUS, true);

    // A filler note pushes the target note out of the (threshold=1) cache, so any reload by
    // moveNote would have to go through the gated get() call.
    notebook.createNote(
        "/filler", DEFAULT_INTERPRETER_GROUP, AuthenticationInfo.ANONYMOUS, true);
    assertEquals(1, noteManager.getCacheSize(),
        "creating the filler note should have evicted the target note from the cache");

    notebookRepo.armGate();

    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
    // Run the moves on a separate thread so that a regression that reloads inside moveNote
    // parks in the gate instead of blocking the test thread.
    Thread mover = new Thread(() -> {
      try {
        // Leaf name changes: this used to trigger the reload and re-save.
        notebook.moveNote(noteId, "/folder_1/renamed", AuthenticationInfo.ANONYMOUS);
        // Folder changes, leaf name stays the same.
        notebook.moveNote(noteId, "/folder_2/renamed", AuthenticationInfo.ANONYMOUS);
      } catch (Throwable t) {
        errors.add(t);
      }
    }, "move-note-thread");
    mover.start();
    mover.join(JOIN_TIMEOUT_MILLIS);

    boolean reloaded = notebookRepo.awaitArrival(0, TimeUnit.SECONDS);
    // Let a parked get() (if any) and later reloads pass through.
    notebookRepo.release();
    mover.join(JOIN_TIMEOUT_MILLIS);

    assertFalse(reloaded, "moveNote must not reload the note from the repo after a rename");
    assertFalse(mover.isAlive(), "moveNote did not finish within the timeout");
    assertTrue(errors.isEmpty(), () -> "moveNote threw: " + errors);

    List<String> zplnFilesForNote = findZplnFilesForNote(noteId);
    assertEquals(1, zplnFilesForNote.size(),
        () -> "Expected exactly one .zpln file for note " + noteId + ", but found: "
            + zplnFilesForNote);

    assertEquals("/folder_2/renamed",
        noteManager.processNote(noteId, true, note -> note.getPath()));
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
