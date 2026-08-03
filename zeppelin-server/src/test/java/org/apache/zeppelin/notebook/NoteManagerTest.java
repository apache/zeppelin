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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.exception.NotePathAlreadyExistsException;
import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteManagerTest {
  private NoteManager noteManager;
  private ZeppelinConfiguration zConf;
  private NoteParser noteParser;


  @BeforeEach
  public void setUp() throws IOException {
    zConf = ZeppelinConfiguration.load();
    this.noteManager = new NoteManager(new InMemoryNotebookRepo(), zConf);
    this.noteParser = new GsonNoteParser(zConf);
  }

  @Test
  void testNoteOperations() throws IOException {
    assertEquals(0, this.noteManager.getNotesInfo().size());

    Note note1 = createNote("/prod/my_note1");
    Note note2 = createNote("/dev/project_2/my_note2");
    Note note3 = createNote("/dev/project_3/my_note3");

    // add note
    this.noteManager.saveNote(note1);
    this.noteManager.saveNote(note2);
    this.noteManager.saveNote(note3);

    // list notes
    assertEquals(3, this.noteManager.getNotesInfo().size());
    assertEquals(note1, this.noteManager.processNote(note1.getId(), n -> n));
    assertEquals(note2, this.noteManager.processNote(note2.getId(), n -> n));
    assertEquals(note3, this.noteManager.processNote(note3.getId(), n -> n));

    // move note
    this.noteManager.moveNote(note1.getId(), "/dev/project_1/my_note1",
            AuthenticationInfo.ANONYMOUS);
    assertEquals(3, this.noteManager.getNotesInfo().size());
    assertEquals("/dev/project_1/my_note1",
            this.noteManager.processNote(note1.getId(), n -> n).getPath());

    // move folder
    this.noteManager.moveFolder("/dev", "/staging", AuthenticationInfo.ANONYMOUS);
    Map<String, String> notesInfo = this.noteManager.getNotesInfo();
    assertEquals(3, notesInfo.size());
    assertEquals("/staging/project_1/my_note1", notesInfo.get(note1.getId()));
    assertEquals("/staging/project_2/my_note2", notesInfo.get(note2.getId()));
    assertEquals("/staging/project_3/my_note3", notesInfo.get(note3.getId()));

    this.noteManager.removeNote(note1.getId(), AuthenticationInfo.ANONYMOUS);
    assertEquals(2, this.noteManager.getNotesInfo().size());

    // remove folder
    this.noteManager.removeFolder("/staging", AuthenticationInfo.ANONYMOUS);
    notesInfo = this.noteManager.getNotesInfo();
    assertEquals(0, notesInfo.size());
  }

  @Test
  void testAddNoteRejectsDuplicatePath() throws IOException {

    assertThrows(NotePathAlreadyExistsException.class,
            () -> {
              Note note1 = createNote("/prod/note");
              Note note2 = createNote("/prod/note");

              noteManager.addNote(note1, AuthenticationInfo.ANONYMOUS);
              noteManager.addNote(note2, AuthenticationInfo.ANONYMOUS);
            },
            "Note '/prod/note' existed");
  }

  @Test
  void testMoveNoteRejectsDuplicatePath() throws IOException {
    assertThrows(NotePathAlreadyExistsException.class,
            () -> {
              Note note1 = createNote("/prod/note-1");
              Note note2 = createNote("/prod/note-2");

              noteManager.addNote(note1, AuthenticationInfo.ANONYMOUS);
              noteManager.addNote(note2, AuthenticationInfo.ANONYMOUS);

              noteManager.moveNote(note2.getId(), "/prod/note-1", AuthenticationInfo.ANONYMOUS);
            },
            "Note '/prod/note-1' existed");
  }

  @Test
  void failedNoteMoveKeepsSourceMetadataAndCachedPath() throws IOException {
    NoteManager manager = new NoteManager(new FailingNoteMoveRepo(), zConf);
    Note note = createNote("/source/note");
    manager.saveNote(note);

    assertThrows(
        IllegalStateException.class,
        () -> manager.moveNote(
            note.getId(), "/destination/note", AuthenticationInfo.ANONYMOUS));

    assertEquals("/source/note", manager.getNotesInfo().get(note.getId()));
    assertEquals("/source/note", note.getPath());
    assertTrue(manager.containsNote("/source/note"));
    assertFalse(manager.containsNote("/destination/note"));
  }

  @Test
  void testMoveFolderMergesExistingDestination() throws IOException {
    Note source = createNote("/source/source-note");
    Note destination = createNote("/destination/destination-note");
    noteManager.saveNote(source);
    noteManager.saveNote(destination);
    long versionBeforeMove = noteManager.getNotesInfoSnapshot().getVersion();

    noteManager.moveFolder("/source", "/destination", AuthenticationInfo.ANONYMOUS);

    assertEquals("/destination/source-note", noteManager.getNotesInfo().get(source.getId()));
    assertEquals(
        "/destination/destination-note", noteManager.getNotesInfo().get(destination.getId()));
    assertEquals("/destination/source-note", source.getPath());
    assertEquals("/destination/destination-note", destination.getPath());
    assertFalse(noteManager.containsFolder("/source"));
    assertTrue(noteManager.containsNote("/destination/source-note"));
    assertTrue(noteManager.containsNote("/destination/destination-note"));
    assertEquals(versionBeforeMove + 1, noteManager.getNotesInfoSnapshot().getVersion());
  }

  @Test
  void testMoveFolderRejectsExistingNoteInDestination() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(0, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note source = createNote("/source/note");
    Note destination = createNote("/destination/note");
    manager.saveNote(source);
    manager.saveNote(destination);
    long versionBeforeMove = manager.getNotesInfoSnapshot().getVersion();

    assertThrows(
        NotePathAlreadyExistsException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(0, repo.moveAttempts);
    assertEquals(versionBeforeMove, manager.getNotesInfoSnapshot().getVersion());
    assertEquals("/source/note", manager.getNotesInfo().get(source.getId()));
    assertEquals("/destination/note", manager.getNotesInfo().get(destination.getId()));
  }

  @Test
  void testMoveFolderRecursivelyMergesExistingDestinationFolders() throws IOException {
    Note source = createNote("/source/shared/source-note");
    Note destination = createNote("/destination/shared/destination-note");
    noteManager.saveNote(source);
    noteManager.saveNote(destination);

    noteManager.moveFolder("/source", "/destination", AuthenticationInfo.ANONYMOUS);

    assertEquals(
        "/destination/shared/source-note", noteManager.getNotesInfo().get(source.getId()));
    assertEquals(
        "/destination/shared/destination-note",
        noteManager.getNotesInfo().get(destination.getId()));
    assertEquals("/destination/shared/source-note", source.getPath());
    assertEquals("/destination/shared/destination-note", destination.getPath());
    assertFalse(noteManager.containsFolder("/source"));
  }

  @Test
  void testMoveFolderRejectsNoteFolderTypeCollisionsBeforeMovingNotes() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(0, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note sourceNote = createNote("/source/shared");
    Note destinationNote = createNote("/destination/shared/destination-note");
    manager.saveNote(sourceNote);
    manager.saveNote(destinationNote);

    assertThrows(
        NotePathAlreadyExistsException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(0, repo.moveAttempts);
    assertEquals("/source/shared", manager.getNotesInfo().get(sourceNote.getId()));
    assertEquals(
        "/destination/shared/destination-note",
        manager.getNotesInfo().get(destinationNote.getId()));
  }

  @Test
  void testMoveFolderRejectsFolderNoteTypeCollisionsBeforeMovingNotes() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(0, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note sourceNote = createNote("/source/shared/source-note");
    Note destinationNote = createNote("/destination/shared");
    manager.saveNote(sourceNote);
    manager.saveNote(destinationNote);

    assertThrows(
        NotePathAlreadyExistsException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(0, repo.moveAttempts);
    assertEquals("/source/shared/source-note", manager.getNotesInfo().get(sourceNote.getId()));
    assertEquals("/destination/shared", manager.getNotesInfo().get(destinationNote.getId()));
  }

  @Test
  void failedFolderMergeRollsBackDurableMovesAndKeepsMetadataUnchanged() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(2, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note firstSource = createNote("/source/a-note");
    Note secondSource = createNote("/source/b-note");
    Note destination = createNote("/destination/destination-note");
    manager.saveNote(firstSource);
    manager.saveNote(secondSource);
    manager.saveNote(destination);
    long versionBeforeMove = manager.getNotesInfoSnapshot().getVersion();

    assertThrows(
        IllegalStateException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(4, repo.moveAttempts);
    assertEquals(
        Set.of("/source/a-note", "/source/b-note", "/destination/destination-note"),
        repo.persistedPaths);
    assertEquals(versionBeforeMove, manager.getNotesInfoSnapshot().getVersion());
    assertEquals("/source/a-note", manager.getNotesInfo().get(firstSource.getId()));
    assertEquals("/source/b-note", manager.getNotesInfo().get(secondSource.getId()));
    assertEquals("/source/a-note", firstSource.getPath());
    assertEquals("/source/b-note", secondSource.getPath());
    assertTrue(manager.containsFolder("/source"));
    assertFalse(manager.containsNote("/destination/a-note"));
  }

  @Test
  void failedFolderMergeReconcilesMetadataWhenCompensationFails() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(2, 4);
    NoteManager manager = new NoteManager(repo, zConf);
    Note firstSource = createNote("/source/a-note");
    Note secondSource = createNote("/source/b-note");
    Note destination = createNote("/destination/destination-note");
    manager.saveNote(firstSource);
    manager.saveNote(secondSource);
    manager.saveNote(destination);
    long versionBeforeMove = manager.getNotesInfoSnapshot().getVersion();

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(1, failure.getSuppressed().length);
    assertEquals(4, repo.moveAttempts);
    assertEquals(
        Set.of("/destination/a-note", "/source/b-note", "/destination/destination-note"),
        repo.persistedPaths);
    assertTrue(manager.getNotesInfoSnapshot().getVersion() > versionBeforeMove);
    assertEquals("/destination/a-note", manager.getNotesInfo().get(firstSource.getId()));
    assertEquals("/source/b-note", manager.getNotesInfo().get(secondSource.getId()));
    assertEquals(
        firstSource.getId(),
        manager.processNote(firstSource.getId(), note -> note.getId()));
  }

  @Test
  void failedFolderMergeFailsClosedUntilMetadataCanBeReloaded() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(2, 4, true);
    NoteManager manager = new NoteManager(repo, zConf);
    Note firstSource = createNote("/source/a-note");
    Note secondSource = createNote("/source/b-note");
    Note destination = createNote("/destination/destination-note");
    manager.saveNote(firstSource);
    manager.saveNote(secondSource);
    manager.saveNote(destination);

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals(2, failure.getSuppressed().length);
    assertEquals(4, repo.moveAttempts);
    IOException unavailable = assertThrows(IOException.class, manager::getNotesInfoSnapshot);
    assertEquals(
        "Notebook metadata is unavailable after repository recovery failed",
        unavailable.getMessage());
    assertThrows(
        IOException.class,
        () -> manager.processNote(firstSource.getId(), note -> note));
    assertThrows(
        IOException.class,
        () -> manager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));
    assertEquals(4, repo.moveAttempts);
    assertThrows(IllegalStateException.class, manager::getNotesInfo);

    repo.allowList();
    manager.reloadNotes();

    assertEquals(
        Set.of("/destination/a-note", "/source/b-note", "/destination/destination-note"),
        repo.persistedPaths);
    assertEquals("/destination/a-note", manager.getNotesInfo().get(firstSource.getId()));
    assertEquals("/source/b-note", manager.getNotesInfo().get(secondSource.getId()));
    assertEquals(
        firstSource.getId(),
        manager.processNote(firstSource.getId(), note -> note.getId()));
  }

  @Test
  void testMoveFolderMergeUsesCanonicalFolderPaths() throws IOException {
    Note source = createNote("/source/note");
    Note destination = createNote("/destination/destination-note");
    noteManager.saveNote(source);
    noteManager.saveNote(destination);

    noteManager.moveFolder("/source/", "/destination", AuthenticationInfo.ANONYMOUS);

    assertEquals("/destination/note", noteManager.getNotesInfo().get(source.getId()));
    assertEquals("/destination/note", source.getPath());
    assertFalse(noteManager.containsFolder("/source"));
    assertTrue(noteManager.containsNote("/destination/note"));
  }

  @Test
  void testMoveFolderRejectsOwnDescendantWithTrailingSlashAlias() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(0, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note source = createNote("/source/source-note");
    Note child = createNote("/source/child/child-note");
    manager.saveNote(source);
    manager.saveNote(child);

    IOException existingChildFailure = assertThrows(
        IOException.class,
        () -> manager.moveFolder(
            "/source/", "/source/child", AuthenticationInfo.ANONYMOUS));
    assertEquals(
        "Can not move folder '/source' into its own descendant",
        existingChildFailure.getMessage());

    assertThrows(
        IOException.class,
        () -> manager.moveFolder(
            "/source/", "/source/new-child", AuthenticationInfo.ANONYMOUS));
    assertEquals(0, repo.moveAttempts);
    assertEquals("/source/source-note", manager.getNotesInfo().get(source.getId()));
    assertEquals("/source/child/child-note", manager.getNotesInfo().get(child.getId()));
    assertTrue(manager.containsFolder("/source/child"));
  }

  @Test
  void testMoveFolderRejectsMovingTheRootBeforeRepositoryMutation() throws IOException {
    TrackingFolderMergeRepo repo = new TrackingFolderMergeRepo(0, 0);
    NoteManager manager = new NoteManager(repo, zConf);
    Note rootNote = createNote("/root-note");
    manager.saveNote(rootNote);
    long versionBeforeMove = manager.getNotesInfoSnapshot().getVersion();

    IOException failure = assertThrows(
        IOException.class,
        () -> manager.moveFolder("/", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals("Can not move the root folder", failure.getMessage());
    assertEquals(0, repo.moveAttempts);
    assertEquals(versionBeforeMove, manager.getNotesInfoSnapshot().getVersion());
    assertEquals("/root-note", manager.getNotesInfo().get(rootNote.getId()));
  }

  @Test
  void testMoveFolderIntoAncestorPreservesNoteIdentityWhenAPathIsReused() throws IOException {
    Note first = createNote("/a/b/x");
    Note second = createNote("/a/b/b/x");
    noteManager.saveNote(first);
    noteManager.saveNote(second);

    noteManager.moveFolder("/a/b", "/a", AuthenticationInfo.ANONYMOUS);

    assertEquals("/a/x", noteManager.getNotesInfo().get(first.getId()));
    assertEquals("/a/b/x", noteManager.getNotesInfo().get(second.getId()));
    assertEquals(first.getId(), noteManager.processNote(first.getId(), note -> note.getId()));
    assertEquals(second.getId(), noteManager.processNote(second.getId(), note -> note.getId()));
  }

  @Test
  void testMoveFolderMergesIntoTheRootWithoutCreatingDoubleSlashPaths() throws IOException {
    Note source = createNote("/source/source-note");
    Note destination = createNote("/destination-note");
    noteManager.saveNote(source);
    noteManager.saveNote(destination);

    noteManager.moveFolder("/source", "/", AuthenticationInfo.ANONYMOUS);

    assertEquals("/source-note", noteManager.getNotesInfo().get(source.getId()));
    assertEquals("/destination-note", noteManager.getNotesInfo().get(destination.getId()));
    assertEquals("/source-note", source.getPath());
    assertFalse(noteManager.containsFolder("/source"));
    assertTrue(noteManager.containsNote("/source-note"));
  }

  @Test
  void processNoteFailsClosedWhenPathMetadataResolvesToAnotherNote() throws IOException {
    Note first = createNote("/shared/path");
    Note second = createNote("/shared/path");
    noteManager.saveNote(first);
    noteManager.saveNote(second);

    IOException failure = assertThrows(
        IOException.class,
        () -> noteManager.processNote(first.getId(), note -> note));

    assertEquals(
        "Note metadata changed while resolving note: " + first.getId(),
        failure.getMessage());
    assertEquals(second, noteManager.processNote(second.getId(), note -> note));
  }

  @Test
  void testMoveFolderRejectsNoteAtDestination() throws IOException {
    Note source = createNote("/source/note");
    Note destination = createNote("/destination");
    noteManager.saveNote(source);
    noteManager.saveNote(destination);

    assertThrows(
        NotePathAlreadyExistsException.class,
        () -> noteManager.moveFolder(
            "/source", "/destination", AuthenticationInfo.ANONYMOUS));

    assertEquals("/source/note", noteManager.getNotesInfo().get(source.getId()));
    assertEquals("/destination", noteManager.getNotesInfo().get(destination.getId()));
  }

  @Test
  void testMoveFolderRejectsOwnDescendant() throws IOException {
    Note source = createNote("/source/note");
    noteManager.saveNote(source);

    assertThrows(
        IOException.class,
        () -> noteManager.moveFolder(
            "/source", "/source/child", AuthenticationInfo.ANONYMOUS));

    assertEquals("/source/note", noteManager.getNotesInfo().get(source.getId()));
  }

  @Test
  void folderMutationRejectsAnAuthorizationSnapshotAfterMembershipChanges() throws IOException {
    Note original = createNote("/source/original");
    noteManager.saveNote(original);
    NoteManager.NoteMetadataSnapshot authorized = noteManager.getNotesInfoSnapshot();

    Note addedAfterAuthorization = createNote("/source/added-later");
    noteManager.saveNote(addedAfterAuthorization);

    IOException failure = assertThrows(
        IOException.class,
        () -> noteManager.moveFolder(
            "/source",
            "/destination",
            AuthenticationInfo.ANONYMOUS,
            authorized.getVersion()));
    assertEquals(
        "Notebook metadata changed while authorizing the folder operation",
        failure.getMessage());
    assertEquals("/source/original", noteManager.getNotesInfo().get(original.getId()));
    assertEquals(
        "/source/added-later", noteManager.getNotesInfo().get(addedAfterAuthorization.getId()));
  }

  @Test
  void folderMoveUpdatesCachedNotePathBeforeASubsequentSave() throws IOException {
    Note note = createNote("/source/note");
    noteManager.saveNote(note);

    noteManager.moveFolder("/source", "/destination", AuthenticationInfo.ANONYMOUS);

    assertEquals("/destination/note", note.getPath());
    noteManager.saveNote(note);
    assertEquals("/destination/note", noteManager.getNotesInfo().get(note.getId()));
    assertFalse(noteManager.containsNote("/source/note"));
  }

  @Test
  void restoreAllUpdatesDirectAndNestedCachedNotePathsBeforeReturning() throws IOException {
    Note directNote = createNote("/~Trash/direct-note");
    Note nestedNote = createNote("/~Trash/folder/nested-note");
    noteManager.saveNote(directNote);
    noteManager.saveNote(nestedNote);
    NoteManager.NoteMetadataSnapshot authorized = noteManager.getNotesInfoSnapshot();

    noteManager.restoreAllFromTrash(AuthenticationInfo.ANONYMOUS, authorized.getVersion());

    assertEquals("/direct-note", directNote.getPath());
    assertEquals("/folder/nested-note", nestedNote.getPath());
    noteManager.saveNote(directNote);
    noteManager.saveNote(nestedNote);
    assertEquals("/direct-note", noteManager.getNotesInfo().get(directNote.getId()));
    assertEquals("/folder/nested-note", noteManager.getNotesInfo().get(nestedNote.getId()));
  }

  @Test
  void emptyTrashKeepsTheLiveTrashNodeForLaterRestoreAll() throws IOException {
    Note discardedNote = createNote("/~Trash/discarded-note");
    noteManager.saveNote(discardedNote);
    noteManager.removeFolder("/~Trash", AuthenticationInfo.ANONYMOUS);

    Note laterNote = createNote("/~Trash/later-note");
    noteManager.saveNote(laterNote);
    NoteManager.NoteMetadataSnapshot authorized = noteManager.getNotesInfoSnapshot();
    noteManager.restoreAllFromTrash(AuthenticationInfo.ANONYMOUS, authorized.getVersion());

    assertEquals("/later-note", laterNote.getPath());
    assertEquals("/later-note", noteManager.getNotesInfo().get(laterNote.getId()));
    assertTrue(noteManager.containsFolder("/~Trash"));
  }

  @Test
  void failedFolderRemovalRollsBackRemovedStateBeforeWaitingSaveContinues() throws Exception {
    BlockingFailingFolderRemoveRepo repo = new BlockingFailingFolderRemoveRepo();
    NoteManager manager = new NoteManager(repo, zConf);
    Note note = createNote("/folder/note");
    manager.saveNote(note);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch removalFinished = new CountDownLatch(1);
    CountDownLatch saveStarted = new CountDownLatch(1);
    CountDownLatch saveFinished = new CountDownLatch(1);
    List<Throwable> removalFailures = Collections.synchronizedList(new ArrayList<>());

    try {
      executor.execute(() -> {
        try {
          manager.removeFolder(
              "/folder", AuthenticationInfo.ANONYMOUS, -1, List.of(note));
        } catch (Throwable t) {
          removalFailures.add(t);
        } finally {
          removalFinished.countDown();
        }
      });
      assertTrue(repo.removeStarted.await(5, TimeUnit.SECONDS));

      executor.execute(() -> {
        saveStarted.countDown();
        try {
          manager.saveNote(note);
        } catch (Throwable t) {
          removalFailures.add(t);
        } finally {
          saveFinished.countDown();
        }
      });
      assertTrue(saveStarted.await(5, TimeUnit.SECONDS));
      assertFalse(saveFinished.await(200, TimeUnit.MILLISECONDS));

      repo.allowRemoveToFail.countDown();
      assertTrue(removalFinished.await(5, TimeUnit.SECONDS));
      assertTrue(saveFinished.await(5, TimeUnit.SECONDS));
      assertEquals(1, removalFailures.size());
      assertTrue(removalFailures.get(0) instanceof IllegalStateException);
      assertFalse(note.isRemoved());
      assertEquals("/folder/note", manager.getNotesInfo().get(note.getId()));
    } finally {
      repo.allowRemoveToFail.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void setRevisionAndMovePublishOnePathGeneration() throws Exception {
    BlockingVersionedRepo repo = new BlockingVersionedRepo();
    NoteManager manager = new NoteManager(repo, zConf);
    Note note = createNote("/source/note");
    manager.saveNote(note);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch moveStarted = new CountDownLatch(1);
    CountDownLatch moveFinished = new CountDownLatch(1);

    try {
      Future<Note> revision = executor.submit(() -> manager.setNoteRevision(
          note.getId(), "/source/note", "revision", AuthenticationInfo.ANONYMOUS));
      assertTrue(repo.revisionStarted.await(5, TimeUnit.SECONDS));

      Future<?> move = executor.submit(() -> {
        moveStarted.countDown();
        try {
          manager.moveNote(
              note.getId(), "/destination/note", AuthenticationInfo.ANONYMOUS);
        } finally {
          moveFinished.countDown();
        }
        return null;
      });
      assertTrue(moveStarted.await(5, TimeUnit.SECONDS));
      assertFalse(moveFinished.await(200, TimeUnit.MILLISECONDS));

      repo.allowRevisionToReturn.countDown();
      assertNotNull(revision.get(5, TimeUnit.SECONDS));
      move.get(5, TimeUnit.SECONDS);

      assertEquals("/destination/note", manager.getNotesInfo().get(note.getId()));
      assertEquals("/destination/note", note.getPath());
      assertEquals(Set.of("/destination/note"), repo.persistedPaths);
      assertFalse(manager.containsNote("/source/note"));
      assertTrue(manager.containsNote("/destination/note"));

      IOException stalePath = assertThrows(
          IOException.class,
          () -> manager.setNoteRevision(
              note.getId(), "/source/note", "revision", AuthenticationInfo.ANONYMOUS));
      assertEquals("Note path changed while setting the revision", stalePath.getMessage());
      assertEquals(Set.of("/destination/note"), repo.persistedPaths);
    } finally {
      repo.allowRevisionToReturn.countDown();
      executor.shutdownNow();
    }
  }

  private static final class BlockingFailingFolderRemoveRepo extends InMemoryNotebookRepo {
    private final CountDownLatch removeStarted = new CountDownLatch(1);
    private final CountDownLatch allowRemoveToFail = new CountDownLatch(1);

    @Override
    public void remove(String folderPath, AuthenticationInfo subject) {
      removeStarted.countDown();
      try {
        if (!allowRemoveToFail.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to fail folder removal");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while failing folder removal", e);
      }
      throw new IllegalStateException("Failed to remove folder");
    }
  }

  private static final class FailingNoteMoveRepo extends InMemoryNotebookRepo {
    @Override
    public void move(
        String noteId,
        String notePath,
        String newNotePath,
        AuthenticationInfo subject) {
      throw new IllegalStateException("Failed to move note");
    }
  }

  private static final class TrackingFolderMergeRepo extends InMemoryNotebookRepo {
    private final int failAfterMutationAttempt;
    private final int failBeforeMutationAttempt;
    private boolean failListAfterMove;
    private final Map<String, String> persistedPathsById = new ConcurrentHashMap<>();
    private final Set<String> persistedPaths = ConcurrentHashMap.newKeySet();
    private int moveAttempts;

    private TrackingFolderMergeRepo(
        int failAfterMutationAttempt, int failBeforeMutationAttempt) {
      this(failAfterMutationAttempt, failBeforeMutationAttempt, false);
    }

    private TrackingFolderMergeRepo(
        int failAfterMutationAttempt,
        int failBeforeMutationAttempt,
        boolean failListAfterMove) {
      this.failAfterMutationAttempt = failAfterMutationAttempt;
      this.failBeforeMutationAttempt = failBeforeMutationAttempt;
      this.failListAfterMove = failListAfterMove;
    }

    @Override
    public void save(Note note, AuthenticationInfo subject) throws IOException {
      super.save(note, subject);
      persistedPathsById.put(note.getId(), note.getPath());
      persistedPaths.add(note.getPath());
    }

    @Override
    public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
      if (failListAfterMove && moveAttempts > 0) {
        throw new IOException("Failed to reload notebook metadata");
      }
      Map<String, NoteInfo> noteInfos = super.list(subject);
      for (Map.Entry<String, String> entry : persistedPathsById.entrySet()) {
        noteInfos.put(entry.getKey(), new NoteInfo(entry.getKey(), entry.getValue()));
      }
      return noteInfos;
    }

    private void allowList() {
      failListAfterMove = false;
    }

    @Override
    public void move(
        String noteId,
        String notePath,
        String newNotePath,
        AuthenticationInfo subject) {
      moveAttempts++;
      if (failBeforeMutationAttempt == moveAttempts) {
        throw new IllegalStateException("Failed to move note " + noteId);
      }
      if (!persistedPaths.remove(notePath)) {
        throw new IllegalStateException("Missing source note at " + notePath);
      }
      if (!persistedPaths.add(newNotePath)) {
        persistedPaths.add(notePath);
        throw new IllegalStateException("Destination note exists at " + newNotePath);
      }
      persistedPathsById.put(noteId, newNotePath);
      super.move(noteId, notePath, newNotePath, subject);
      if (failAfterMutationAttempt == moveAttempts) {
        throw new IllegalStateException("Failed after moving note " + noteId);
      }
    }
  }

  private static final class BlockingVersionedRepo extends InMemoryNotebookRepo
      implements NotebookRepoWithVersionControl {
    private final CountDownLatch revisionStarted = new CountDownLatch(1);
    private final CountDownLatch allowRevisionToReturn = new CountDownLatch(1);
    private final Set<String> persistedPaths = ConcurrentHashMap.newKeySet();

    @Override
    public void save(Note note, AuthenticationInfo subject) throws IOException {
      super.save(note, subject);
      persistedPaths.add(note.getPath());
    }

    @Override
    public void move(
        String noteId,
        String notePath,
        String newNotePath,
        AuthenticationInfo subject) {
      super.move(noteId, notePath, newNotePath, subject);
      persistedPaths.remove(notePath);
      persistedPaths.add(newNotePath);
    }

    @Override
    public Revision checkpoint(
        String noteId,
        String notePath,
        String checkpointMsg,
        AuthenticationInfo subject) {
      return Revision.EMPTY;
    }

    @Override
    public Note get(
        String noteId,
        String notePath,
        String revId,
        AuthenticationInfo subject) throws IOException {
      return get(noteId, notePath, subject);
    }

    @Override
    public List<Revision> revisionHistory(
        String noteId,
        String notePath,
        AuthenticationInfo subject) {
      return Collections.emptyList();
    }

    @Override
    public Note setNoteRevision(
        String noteId,
        String notePath,
        String revId,
        AuthenticationInfo subject) throws IOException {
      revisionStarted.countDown();
      try {
        if (!allowRevisionToReturn.await(5, TimeUnit.SECONDS)) {
          throw new IOException("Timed out waiting to return a note revision");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while returning a note revision", e);
      }
      Note note = get(noteId, notePath, subject);
      save(note, subject);
      return note;
    }
  }

  private Note createNote(String notePath) {
    return new Note(notePath, "test", null, null, null, null, null, zConf, noteParser);
  }

  @Test
  void testLruCache() throws IOException {

    int cacheThreshold = zConf.getNoteCacheThreshold();

    // fill cache
    for (int i = 0; i < cacheThreshold; ++i) {
      Note note = createNote("/prod/note" + i);
      noteManager.addNote(note, AuthenticationInfo.ANONYMOUS);
    }
    assertEquals(cacheThreshold, noteManager.getCacheSize());

    // add cache + 1
    Note noteNew = createNote("/prod/notenew");
    noteManager.addNote(noteNew, AuthenticationInfo.ANONYMOUS);
    // check for first eviction
    assertEquals(cacheThreshold, noteManager.getCacheSize());

    // add notes with read flag
    for (int i = 0; i < cacheThreshold; ++i) {
      Note note = createNote("/prod/noteDirty" + i);
      note.getLock().readLock().lock();
      noteManager.addNote(note, AuthenticationInfo.ANONYMOUS);
    }
    assertEquals(cacheThreshold, noteManager.getCacheSize());

    // add cache + 1 with read flag
    Note noteNew2 = createNote("/prod/notenew2");
    noteNew2.getLock().readLock().lock();
    noteManager.addNote(noteNew2, AuthenticationInfo.ANONYMOUS);

    // since all notes in the cache are with a read lock, the cache grows
    assertEquals(cacheThreshold + 1, noteManager.getCacheSize());

    assertTrue(noteManager.containsNote(noteNew2.getPath()));
    noteManager.removeNote(noteNew2.getId(), AuthenticationInfo.ANONYMOUS);
    assertFalse(noteManager.containsNote(noteNew2.getPath()));
    assertEquals(cacheThreshold, noteManager.getCacheSize());

    // add cache + 1 without read flag
    Note noteNew3 = createNote("/prod/notenew3");
    noteManager.addNote(noteNew3, AuthenticationInfo.ANONYMOUS);

    // since all dirty notes in the cache are with a read flag, the cache removes noteNew3, because it has no read flag
    assertEquals(cacheThreshold, noteManager.getCacheSize());
    assertTrue(noteManager.containsNote(noteNew3.getPath()));
  }

  @Test
  void testRemoveFolderEvictsNoteCache() throws IOException {
    // add 2 notes under the same folder
    Note note1 = createNote("/folder1/note1");
    Note note2 = createNote("/folder1/note2");
    noteManager.addNote(note1, AuthenticationInfo.ANONYMOUS);
    noteManager.addNote(note2, AuthenticationInfo.ANONYMOUS);
    assertEquals(2, noteManager.getCacheSize());

    // remove folder should evict its notes from the cache as well
    noteManager.removeFolder("/folder1", AuthenticationInfo.ANONYMOUS);
    assertEquals(0, noteManager.getCacheSize());
  }

  @Test
  void testConcurrentOperation() throws Exception {
    int threshold = 10, noteNum = 150;
    Map<Integer, String> notes = new ConcurrentHashMap<>();
    ExecutorService threadPool = Executors.newFixedThreadPool(threshold);
    // Save note concurrently
    ConcurrentTask saveNote = new ConcurrentTaskSaveNote(threadPool, noteNum, notes, "/prod/note%s");
    saveNote.exec();
    // Move note concurrently
    ConcurrentTask moveNote = new ConcurrentTaskMoveNote(threadPool, noteNum, notes, "/dev/project_%s/my_note%s");
    moveNote.exec();
    // Move folder concurrently
    ConcurrentTask moveFolder = new ConcurrentTaskMoveFolder(threadPool, noteNum, notes, "/staging/note_%s/my_note%s");
    moveFolder.exec();
    // Remove note concurrently
    ConcurrentTask removeNote = new ConcurrentTaskRemoveNote(threadPool, noteNum, notes, null);
    removeNote.exec();
    threadPool.shutdown();
  }

  @Test
  void testConcurrentReloadAndProcessNote() throws Exception {
    int noteNum = 50, readerNum = 4, reloadRounds = 30;
    Map<Integer, String> notes = new ConcurrentHashMap<>();
    for (int i = 0; i < noteNum; i++) {
      Note note = createNote(String.format("/prod/note_%s", i));
      noteManager.saveNote(note);
      notes.put(i, note.getId());
    }

    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    AtomicBoolean reloading = new AtomicBoolean(true);
    ExecutorService threadPool = Executors.newFixedThreadPool(readerNum + 1);
    CountDownLatch done = new CountDownLatch(readerNum + 1);

    // Reload the whole note tree repeatedly while other threads read the notes
    threadPool.execute(() -> {
      try {
        for (int i = 0; i < reloadRounds; i++) {
          noteManager.reloadNotes();
        }
      } catch (Throwable t) {
        failures.add(t);
      } finally {
        reloading.set(false);
        done.countDown();
      }
    });

    for (int i = 0; i < readerNum; i++) {
      threadPool.execute(() -> {
        try {
          while (reloading.get()) {
            for (String noteId : notes.values()) {
              assertNotNull(noteManager.processNote(noteId, note -> note),
                  "processNote() found no note for an existing noteId during reload");
            }
          }
        } catch (Throwable t) {
          failures.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    assertTrue(done.await(60, TimeUnit.SECONDS), "Concurrent reload did not finish in time");
    threadPool.shutdown();
    if (!failures.isEmpty()) {
      throw new AssertionError(failures.size()
          + " note operation(s) failed while the note tree was being reloaded", failures.get(0));
    }
  }

  abstract class ConcurrentTask {
    private ExecutorService threadPool;
    private int noteNum;
    private Map<Integer, String> notes;
    private String pathPattern;

    public ConcurrentTask(ExecutorService threadPool, int noteNum, Map<Integer, String> notes, String pathPattern) {
      this.threadPool = threadPool;
      this.noteNum = noteNum;
      this.notes = notes;
      this.pathPattern = pathPattern;
    }

    public abstract void run(int index) throws IOException;

    public void exec() throws Exception {
      // Simulate concurrent operation
      CountDownLatch latch = new CountDownLatch(noteNum);
      for (int i = 0; i < noteNum; i++) {
        int index = i;
        threadPool.execute(() -> {
          try {
            this.run(index);
            latch.countDown();
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
      }
      // wait till all tasks are completed with 5 seconds as timeout threshold
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      this.checkPathByPattern();
    }

    private void checkPathByPattern() throws IOException {
      assertEquals(this.notes.size(), noteManager.getNotesInfo().size());
      if (notes.isEmpty()) return;
      for (Integer key : this.notes.keySet()) {
        String expectPath = String.format(this.pathPattern, key, key);
        assertEquals(expectPath, noteManager.processNote(notes.get(key), n -> n).getPath());
      }
    }
  }

  class ConcurrentTaskSaveNote extends ConcurrentTask {
    public ConcurrentTaskSaveNote(ExecutorService threadPool, int noteNum, Map<Integer, String> notes, String pathPattern) {
      super(threadPool, noteNum, notes, pathPattern);
    }

    @Override
    public void run(int index) throws IOException {
      String tarPath = String.format(super.pathPattern, index, index);
      Note note = createNote(tarPath);
      noteManager.saveNote(note);
      super.notes.put(index, note.getId());
    }
  }

  class ConcurrentTaskMoveNote extends ConcurrentTask {
    public ConcurrentTaskMoveNote(ExecutorService threadPool, int noteNum, Map<Integer, String> notes, String pathPattern) {
      super(threadPool, noteNum, notes, pathPattern);
    }

    @Override
    public void run(int index) throws IOException {
      String tarPath = String.format(super.pathPattern, index, index);
      noteManager.moveNote(super.notes.get(index), tarPath, AuthenticationInfo.ANONYMOUS);
    }
  }

  class ConcurrentTaskMoveFolder extends ConcurrentTask {
    public ConcurrentTaskMoveFolder(ExecutorService threadPool, int noteNum, Map<Integer, String> notes, String pathPattern) {
      super(threadPool, noteNum, notes, pathPattern);
    }

    @Override
    public void run(int index) throws IOException {
      String curPath = "/dev/project_" + index, tarPath = "/staging/note_" + index;
      noteManager.moveFolder(curPath, tarPath, AuthenticationInfo.ANONYMOUS);
    }
  }

  class ConcurrentTaskRemoveNote extends ConcurrentTask {
    public ConcurrentTaskRemoveNote(ExecutorService threadPool, int noteNum, Map<Integer, String> notes, String pathPattern) {
      super(threadPool, noteNum, notes, pathPattern);
    }

    @Override
    public void run(int index) throws IOException {
      noteManager.removeNote(super.notes.get(index), AuthenticationInfo.ANONYMOUS);
      super.notes.remove(index);
    }
  }
}
