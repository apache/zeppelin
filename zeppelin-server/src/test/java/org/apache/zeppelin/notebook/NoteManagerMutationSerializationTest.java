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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepoWithMoveGate;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduction test for ZEPPELIN-6594. {@link NoteManager#moveNote} calls
 * {@code notebookRepo.move} while holding the NoteManager monitor. {@code addNote} and
 * {@code removeFolder} must take the same monitor, otherwise they run concurrently with
 * {@code moveNote} and corrupt the folder tree and the noteId to path mapping.
 *
 * <p>The monitor is pinned deterministically with {@link VFSNotebookRepoWithMoveGate}, which
 * parks a {@code moveNote} call inside {@code notebookRepo.move}, and the test then checks that
 * the other mutation is blocked on the monitor until the gate is released.
 */
class NoteManagerMutationSerializationTest {

  private static final long JOIN_TIMEOUT_MILLIS = 30_000L;
  private static final long GATE_ARRIVAL_TIMEOUT_SECONDS = 30L;
  private static final long BLOCKED_TIMEOUT_MILLIS = 5_000L;

  private File notebookDir;
  private ZeppelinConfiguration zConf;
  private NoteParser noteParser;
  private VFSNotebookRepoWithMoveGate notebookRepo;
  private NoteManager noteManager;

  @BeforeEach
  void setUp() throws Exception {
    notebookDir = Files.createTempDirectory("notebookDir").toAbsolutePath().toFile();
    zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getAbsolutePath());
    noteParser = new GsonNoteParser(zConf);
    notebookRepo = new VFSNotebookRepoWithMoveGate();
    notebookRepo.init(zConf, noteParser);
    noteManager = new NoteManager(notebookRepo, zConf);
  }

  @AfterEach
  void tearDown() throws IOException {
    FileUtils.deleteDirectory(notebookDir);
  }

  /**
   * Given a moveNote parked inside notebookRepo.move (monitor held), when another thread calls
   * removeFolder, then it must wait for the monitor and only finish after the move resumes.
   */
  @Test
  void testRemoveFolderWaitsForInFlightMoveNote() throws Exception {
    Note moving = createAndSave("/src/moving");
    Note victim = createAndSave("/victim/note");

    List<Throwable> errors = runBlockedMutationAfterParkedMove(
        moving.getId(), "/dst/moving",
        () -> noteManager.removeFolder("/victim", AuthenticationInfo.ANONYMOUS));

    assertTrue(errors.isEmpty(), () -> "Mutations threw: " + errors);
    assertFalse(noteManager.getNotesInfo().containsKey(victim.getId()));
    assertEquals("/dst/moving", noteManager.getNotesInfo().get(moving.getId()));
  }

  /**
   * Given a moveNote parked inside notebookRepo.move (monitor held), when another thread calls
   * addNote, then it must wait for the monitor and only finish after the move resumes.
   */
  @Test
  void testAddNoteWaitsForInFlightMoveNote() throws Exception {
    Note moving = createAndSave("/src/moving");
    Note added = newNote("/other/added");

    List<Throwable> errors = runBlockedMutationAfterParkedMove(
        moving.getId(), "/dst/moving",
        () -> noteManager.addNote(added, AuthenticationInfo.ANONYMOUS));

    assertTrue(errors.isEmpty(), () -> "Mutations threw: " + errors);
    assertEquals("/other/added", noteManager.getNotesInfo().get(added.getId()));
    assertEquals("/dst/moving", noteManager.getNotesInfo().get(moving.getId()));
  }

  private interface Mutation {
    void run() throws IOException;
  }

  private List<Throwable> runBlockedMutationAfterParkedMove(
      String movingNoteId, String newPath, Mutation mutation) throws Exception {
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
    notebookRepo.armGate();

    Thread mover = new Thread(() -> {
      try {
        noteManager.moveNote(movingNoteId, newPath, AuthenticationInfo.ANONYMOUS);
      } catch (Throwable t) {
        errors.add(t);
      }
    }, "mutation-serialization-mover");
    mover.start();

    assertTrue(
        notebookRepo.awaitArrival(GATE_ARRIVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "moveNote never reached the gated notebookRepo.move()");

    Thread other = new Thread(() -> {
      try {
        mutation.run();
      } catch (Throwable t) {
        errors.add(t);
      }
    }, "mutation-serialization-other");
    other.start();

    try {
      awaitBlockedOnMonitor(other);
    } finally {
      notebookRepo.release();
    }
    mover.join(JOIN_TIMEOUT_MILLIS);
    other.join(JOIN_TIMEOUT_MILLIS);
    assertFalse(mover.isAlive(), "moveNote did not finish within the timeout");
    assertFalse(other.isAlive(), "The other mutation did not finish within the timeout");
    return errors;
  }

  private static void awaitBlockedOnMonitor(Thread thread) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BLOCKED_TIMEOUT_MILLIS);
    while (System.nanoTime() < deadline) {
      Thread.State state = thread.getState();
      if (state == Thread.State.BLOCKED) {
        return;
      }
      if (state == Thread.State.TERMINATED) {
        fail("The mutation completed while moveNote held the NoteManager monitor; "
            + "it is not serialized with moveNote");
      }
      Thread.sleep(10);
    }
    fail("The mutation neither blocked on the NoteManager monitor nor finished");
  }

  private Note newNote(String notePath) {
    return new Note(notePath, "test", null, null, null, null, null, zConf, noteParser);
  }

  private Note createAndSave(String notePath) throws IOException {
    Note note = newNote(notePath);
    noteManager.saveNote(note, AuthenticationInfo.ANONYMOUS);
    return note;
  }
}
