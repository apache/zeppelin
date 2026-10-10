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
package org.apache.zeppelin.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.InMemoryNotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ZEPPELIN-6412: {@link EmbeddingSearch} persistence sharded by note.
 *
 * <p>Unlike {@link EmbeddingSearchTest}, these use the package-private {@code skipModel}
 * constructor, so they don't need the ONNX model and aren't gated behind
 * {@code ZEPPELIN_EMBEDDING_TEST} — {@code embed()} falls back to a zero vector when
 * there's no model, which doesn't matter to the persistence format under test here.
 */
class EmbeddingSearchShardingTest {

  private static final String NOTES_DIR = "notes";
  private static final String SHARD_SUFFIX = ".bin";
  private static final String LEGACY_FILE = "embedding_index.bin";

  private File indexDir;
  private ZeppelinConfiguration zConf;
  private Notebook notebook;

  @BeforeEach
  void startUp() throws IOException {
    indexDir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
    zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SEARCH_INDEX_PATH.getVarName(),
        indexDir.getAbsolutePath());

    NoteManager noteManager = new NoteManager(new InMemoryNotebookRepo(), zConf);
    InterpreterSettingManager interpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterSetting defaultInterpreterSetting = mock(InterpreterSetting.class);
    when(defaultInterpreterSetting.getName()).thenReturn("test");
    when(interpreterSettingManager.getDefaultInterpreterSetting())
        .thenReturn(defaultInterpreterSetting);
    notebook = new Notebook(zConf, mock(AuthorizationService.class),
        mock(NotebookRepo.class), noteManager,
        mock(InterpreterFactory.class), interpreterSettingManager,
        mock(Credentials.class), null);
  }

  @AfterEach
  void shutDown() throws IOException {
    FileUtils.deleteDirectory(indexDir);
  }

  private EmbeddingSearch openSearch() throws IOException {
    return new EmbeddingSearch(zConf, notebook, true);
  }

  private File shardFile(String noteId) {
    return new File(new File(indexDir, NOTES_DIR), noteId + SHARD_SUFFIX);
  }

  private String newNoteWithParagraph(String noteName, String text) throws IOException {
    String noteId = notebook.createNote(noteName, AuthenticationInfo.ANONYMOUS);
    notebook.processNote(noteId, note -> {
      Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
      p.setText(text);
      return null;
    });
    return noteId;
  }

  private String lastParagraphId(String noteId) throws IOException {
    AtomicReference<String> id = new AtomicReference<>();
    notebook.processNote(noteId, note -> {
      id.set(note.getLastParagraph().getId());
      return null;
    });
    return id.get();
  }

  @Test
  void savesOneShardFilePerNote() throws IOException {
    String noteA = newNoteWithParagraph("NoteA", "select * from a");
    String noteB = newNoteWithParagraph("NoteB", "select * from b");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteA);
    search.addNoteIndex(noteB);
    search.close();

    assertTrue(shardFile(noteA).exists(), "note A should have its own shard file");
    assertTrue(shardFile(noteB).exists(), "note B should have its own shard file");
    File[] shards = new File(indexDir, NOTES_DIR).listFiles((d, n) -> n.endsWith(SHARD_SUFFIX));
    assertEquals(2, shards.length, "exactly one shard per note, no shared file");
  }

  @Test
  void editingOneNoteDoesNotTouchOtherNotesShardFile() throws IOException {
    String noteA = newNoteWithParagraph("NoteA", "select * from a");
    String noteB = newNoteWithParagraph("NoteB", "select * from b");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteA);
    search.addNoteIndex(noteB);
    search.close(); // first flush: both shards written

    byte[] noteBBytesBefore = Files.readAllBytes(shardFile(noteB).toPath());
    byte[] noteABytesBefore = Files.readAllBytes(shardFile(noteA).toPath());

    // Reopen (loads both shards) and edit only note A.
    EmbeddingSearch search2 = openSearch();
    String paragraphId = lastParagraphId(noteA);
    notebook.processNote(noteA, note -> {
      note.getLastParagraph().setText("select * from a_renamed_table");
      return null;
    });
    search2.updateParagraphIndex(noteA, paragraphId);
    search2.close(); // second flush: only note A changed

    byte[] noteABytesAfter = Files.readAllBytes(shardFile(noteA).toPath());
    byte[] noteBBytesAfter = Files.readAllBytes(shardFile(noteB).toPath());

    assertArrayEquals(noteBBytesBefore, noteBBytesAfter,
        "untouched note's shard must be byte-for-byte identical — this is the ZEPPELIN-6412 fix");
    assertFalse(java.util.Arrays.equals(noteABytesBefore, noteABytesAfter),
        "edited note's shard should have actually changed");
  }

  @Test
  void multipleParagraphEditsInSameNoteAreReflectedAfterOneFlush() throws IOException {
    String noteId = notebook.createNote("MultiPara", AuthenticationInfo.ANONYMOUS);
    List<String> paragraphIds = new ArrayList<>();
    notebook.processNote(noteId, note -> {
      for (int i = 0; i < 2; i++) {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("original text " + i);
        paragraphIds.add(p.getId());
      }
      return null;
    });

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteId);
    search.close();

    // Edit both paragraphs before the next flush — should collapse into one shard write.
    EmbeddingSearch search2 = openSearch();
    notebook.processNote(noteId, note -> {
      note.getParagraph(paragraphIds.get(0)).setText("updated alpha content");
      note.getParagraph(paragraphIds.get(1)).setText("updated beta content");
      return null;
    });
    search2.updateParagraphIndex(noteId, paragraphIds.get(0));
    search2.updateParagraphIndex(noteId, paragraphIds.get(1));
    search2.close();

    EmbeddingSearch search3 = openSearch();
    List<Map<String, String>> alphaResults = search3.query("alpha content", id -> true);
    List<Map<String, String>> betaResults = search3.query("beta content", id -> true);
    search3.close();

    assertTrue(alphaResults.stream().anyMatch(r -> r.get("text").contains("alpha")),
        "first paragraph's edit should be in the single flushed shard");
    assertTrue(betaResults.stream().anyMatch(r -> r.get("text").contains("beta")),
        "second paragraph's edit should be in the same single flushed shard");
  }

  @Test
  void deletingNoteRemovesItsShardButNotOthers() throws IOException {
    String noteA = newNoteWithParagraph("NoteA", "select * from a");
    String noteB = newNoteWithParagraph("NoteB", "select * from b");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteA);
    search.addNoteIndex(noteB);
    search.close();
    assertTrue(shardFile(noteA).exists());
    assertTrue(shardFile(noteB).exists());

    EmbeddingSearch search2 = openSearch();
    search2.deleteNoteIndex(noteA);
    // deleteNoteIndex() only mutates memory and marks the note dirty now — the shard file
    // removal happens on the next flush, under this note's lock, so it can never race with
    // an in-flight save that captured a snapshot before the delete (see saveNoteShard's
    // javadoc). So the file is still there until that flush happens.
    assertTrue(shardFile(noteA).exists(),
        "shard removal is deferred to the next flush, not done inline");
    search2.close(); // the deferred flush actually removes it

    assertFalse(shardFile(noteA).exists(), "deleted note's shard should be gone after a flush");
    assertTrue(shardFile(noteB).exists(), "other note's shard must survive");
  }

  @Test
  void deletingParagraphsShrinksThenRemovesTheShard() throws IOException {
    String noteId = notebook.createNote("TwoParas", AuthenticationInfo.ANONYMOUS);
    List<String> paragraphIds = new ArrayList<>();
    notebook.processNote(noteId, note -> {
      for (int i = 0; i < 2; i++) {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("paragraph number " + i);
        paragraphIds.add(p.getId());
      }
      return null;
    });

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteId);
    search.close();
    assertTrue(shardFile(noteId).exists());

    // Delete one of two paragraphs: shard should remain, with only the other entry.
    EmbeddingSearch search2 = openSearch();
    search2.deleteParagraphIndex(noteId, paragraphIds.get(0));
    search2.close();
    assertTrue(shardFile(noteId).exists(), "shard should remain while one paragraph is left");

    EmbeddingSearch search3 = openSearch();
    List<Map<String, String>> remaining = search3.query("paragraph number 1", id -> true);
    assertTrue(remaining.stream().anyMatch(r -> r.get("text").contains("paragraph number 1")));

    // Delete the last paragraph: the shard file should disappear entirely.
    search3.deleteParagraphIndex(noteId, paragraphIds.get(1));
    search3.close();
    assertFalse(shardFile(noteId).exists(),
        "shard with no remaining entries should be deleted, not left empty");
  }

  @Test
  void migrationFromLegacyIndexFileProducesShardsAndDeletesLegacyFile() throws IOException {
    String noteId = newNoteWithParagraph("Legacy Title", "current notebook content");
    String docId = noteId + "/paragraph/p1";
    writeLegacyIndexFile(new File(indexDir, LEGACY_FILE),
        new LegacyEntry(docId, noteId, "legacy migrated content about quarterly revenue",
            "Legacy Title"));

    EmbeddingSearch search = openSearch(); // constructor runs migration before loading shards

    assertFalse(new File(indexDir, LEGACY_FILE).exists(),
        "legacy single-file index should be deleted after migration");
    assertTrue(shardFile(noteId).exists(), "migration should have written a shard for the note");

    List<Map<String, String>> results = search.query("quarterly revenue", id -> true);
    assertTrue(results.stream().anyMatch(r -> r.get("text").contains("quarterly revenue")),
        "migrated content should be queryable without any further indexing");
    search.close();
  }

  @Test
  void existingShardsTakePrecedenceOverStaleLegacyIndex() throws IOException {
    String noteId = newNoteWithParagraph("CurrentNote", "current shard content");

    EmbeddingSearch initial = openSearch();
    initial.addNoteIndex(noteId);
    initial.close();
    byte[] shardBefore = Files.readAllBytes(shardFile(noteId).toPath());

    File legacyFile = new File(indexDir, LEGACY_FILE);
    writeLegacyIndexFile(legacyFile,
        new LegacyEntry(noteId + "/paragraph/legacy", "CurrentNote",
            "stale legacy content", "Stale"));

    EmbeddingSearch reopened = openSearch();

    assertFalse(legacyFile.exists(),
        "a stale legacy file should be discarded when published shards already exist");
    assertArrayEquals(shardBefore, Files.readAllBytes(shardFile(noteId).toPath()),
        "reopening must not replace newer shards with the stale legacy snapshot");
    assertTrue(reopened.query("current shard content", id -> true).stream()
        .anyMatch(r -> r.get("text").contains("current shard content")));
    assertTrue(reopened.query("stale legacy content", id -> true).isEmpty(),
        "stale legacy entries must not become searchable again");
    reopened.close();
  }

  @Test
  void concurrentCloseFlushesLeaveReadableShards() throws Exception {
    String noteA = newNoteWithParagraph("ConcurrentFlushA", "concurrent flush alpha");
    String noteB = newNoteWithParagraph("ConcurrentFlushB", "concurrent flush beta");
    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteA);
    search.addNoteIndex(noteB);

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Runnable close = () -> {
      try {
        start.await();
        search.close();
      } catch (Throwable t) {
        failure.compareAndSet(null, t);
      } finally {
        done.countDown();
      }
    };
    new Thread(close).start();
    new Thread(close).start();
    start.countDown();
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertNull(failure.get(), "concurrent lifecycle flushes must not race on shard temp files");

    EmbeddingSearch verify = openSearch();
    assertTrue(verify.query("concurrent flush alpha", id -> true).stream()
        .anyMatch(r -> r.get("text").contains("concurrent flush alpha")));
    assertTrue(verify.query("concurrent flush beta", id -> true).stream()
        .anyMatch(r -> r.get("text").contains("concurrent flush beta")));
    verify.close();
  }

  @Test
  void bootstrapsExistingNotesWhenNoIndexExists() throws IOException, InterruptedException {
    // No legacy file, no notes/ directory, no shards at all — just a Notebook with content
    // that was never indexed. shouldBootstrapIndex() alone used to only check
    // zeppelin.search.index.rebuild, so this notebook would silently stay unsearchable.
    String noteId = newNoteWithParagraph("NeverIndexed",
        "select * from completely_unindexed_table");

    EmbeddingSearch search = openSearch(); // constructor should register a full bootstrap

    notebook.initNotebook();
    assertTrue(notebook.waitForFinishInit(10, TimeUnit.SECONDS));

    List<Map<String, String>> results = search.query("completely_unindexed_table", id -> true);
    assertTrue(results.stream().anyMatch(r -> r.get("text").contains("completely_unindexed_table")),
        "an existing note should be indexed on startup when there's no persisted index at all");
    search.close(); // force the flush that actually persists what the bootstrap indexed
    assertTrue(shardFile(noteId).exists(), "the bootstrap should have flushed a shard for it");
  }

  @Test
  void corruptLegacyIndexFileAlsoTriggersFullBootstrap() throws IOException, InterruptedException {
    // A legacy file that exists but fails to load (bad version) should be treated the same
    // as "no index at all" once migration deletes it — not silently leave the notebook
    // unindexed just because something used to be there.
    String noteId = newNoteWithParagraph("WasNeverMigrated",
        "select * from table_behind_corrupt_legacy_index");
    File legacyFile = new File(indexDir, LEGACY_FILE);
    Files.write(legacyFile.toPath(), new byte[] {0, 0, 0, 99, 1, 2, 3}); // bad version header

    EmbeddingSearch search = openSearch();
    assertFalse(legacyFile.exists(), "unreadable legacy file should be deleted during migration");

    notebook.initNotebook();
    assertTrue(notebook.waitForFinishInit(10, TimeUnit.SECONDS));

    List<Map<String, String>> results =
        search.query("table_behind_corrupt_legacy_index", id -> true);
    assertTrue(results.stream()
            .anyMatch(r -> r.get("text").contains("table_behind_corrupt_legacy_index")),
        "a corrupt legacy file must not leave the notebook unindexed");
    search.close();
  }

  @Test
  void truncatedShardDoesNotLeavePartiallyLoadedEntries() throws IOException {
    String noteId = newNoteWithParagraph("TruncatedNote", "real notebook content");
    String survivingText = "truncated shard entry alpha content that must not leak";
    File notesDir = new File(indexDir, NOTES_DIR);
    assertTrue(notesDir.mkdirs() || notesDir.isDirectory());
    writeTruncatedShard(shardFile(noteId), noteId, survivingText);

    EmbeddingSearch search = openSearch(); // loadNoteShard() should fail cleanly on this shard

    List<Map<String, String>> results = search.query(survivingText, id -> true);
    assertTrue(results.isEmpty(),
        "a shard that fails partway through must not leave its earlier, successfully-read "
            + "entries live in the index");
    assertFalse(shardFile(noteId).exists(), "the corrupt shard file should be deleted");
    search.close();
  }

  @Test
  void concurrentFlushCannotRecreateDeletedNoteShard() throws Exception {
    // Create every note up front, before any EmbeddingSearch (and so any notebook event
    // listener) exists. Each iteration below opens and closes its own instances, and a
    // note-create event fired *after* an earlier iteration's closed instance is still
    // registered as a listener would hit a RejectedExecutionException unrelated to the
    // race this test targets — so all note creation happens first.
    List<String> noteIds = new ArrayList<>();
    for (int iter = 0; iter < 20; iter++) {
      noteIds.add(newNoteWithParagraph("RaceNote" + iter, "race content " + iter));
    }

    for (int iter = 0; iter < 20; iter++) {
      String noteId = noteIds.get(iter);
      EmbeddingSearch search = openSearch();
      search.addNoteIndex(noteId);

      CountDownLatch startLatch = new CountDownLatch(2);
      CountDownLatch doneLatch = new CountDownLatch(2);
      Thread flushThread = new Thread(() -> {
        awaitLatch(startLatch);
        search.close(); // triggers flushIfDirty() -> saveNoteShard(noteId) synchronously
        doneLatch.countDown();
      });
      Thread deleteThread = new Thread(() -> {
        awaitLatch(startLatch);
        search.deleteNoteIndex(noteId);
        doneLatch.countDown();
      });
      flushThread.start();
      deleteThread.start();
      assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
          "iteration " + iter + ": both threads should finish");

      // Whichever of the two ran first, settle any pending dirty mark the other one left
      // behind (e.g. a delete that landed after the first flush already ran) the same
      // deterministic way the rest of the suite does.
      search.close();

      EmbeddingSearch verify = openSearch(); // reload strictly from what's on disk
      boolean resurrected = !verify.query("race content " + iter, id -> true).isEmpty();
      verify.close();
      assertFalse(resurrected,
          "iteration " + iter + ": a concurrent flush must never recreate a deleted note's "
              + "shard with stale, pre-delete content");
    }
  }

  @Test
  void failedMigrationDoesNotExposePartialShardSet() throws IOException {
    File legacyFile = new File(indexDir, LEGACY_FILE);
    String goodNoteId = "legacy-note-good";
    // 300 characters comfortably exceeds every common filesystem's per-component filename
    // limit (255 bytes on ext4/APFS/etc.), so writing this note's shard fails with a real
    // IOException partway through migration's staging loop — regardless of which of the
    // two notes happens to be processed first (Set iteration order is unspecified), the
    // whole migration must still fail cleanly rather than publish whatever got staged.
    String badNoteId = "x".repeat(300);
    writeLegacyIndexFile(legacyFile,
        new LegacyEntry(goodNoteId + "/paragraph/p1", goodNoteId, "alpha content", "Alpha"),
        new LegacyEntry(badNoteId + "/paragraph/p1", badNoteId, "beta content", "Beta"));

    EmbeddingSearch search = openSearch(); // migration attempt fails internally, swallowed

    File notesDir = new File(indexDir, NOTES_DIR);
    File[] published = notesDir.exists() ? notesDir.listFiles() : null;
    assertTrue(published == null || published.length == 0,
        "a failed migration must never publish a partial shard set to notes/");
    search.close();
  }

  @Test
  void migrationStopsWhenStaleStagingDirectoryCannotBeCleared() throws IOException {
    File legacyFile = new File(indexDir, LEGACY_FILE);
    writeLegacyIndexFile(legacyFile,
        new LegacyEntry("legacy-note/paragraph/p1", "legacy-note", "legacy content", "Legacy"));

    Path stagingDir = indexDir.toPath().resolve("notes.migrating");
    Files.createDirectories(stagingDir);
    Files.writeString(stagingDir.resolve("stale.bin"), "debris from an earlier migration");
    assumeTrue(Files.getFileStore(stagingDir).supportsFileAttributeView("posix"));

    Files.setPosixFilePermissions(stagingDir, PosixFilePermissions.fromString("r-x------"));
    try {
      assumeFalse(Files.isWritable(stagingDir),
          "the test needs a staging directory whose children cannot be deleted");

      EmbeddingSearch search = openSearch();

      assertTrue(legacyFile.exists(),
          "legacy index must remain when stale migration debris cannot be cleared");
      assertFalse(new File(indexDir, NOTES_DIR).exists(),
          "uncleared staging contents must never be published as the live shard directory");
      search.close();
    } finally {
      Files.setPosixFilePermissions(stagingDir, PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Test
  void legacyFileIsDeletedOnlyAfterCompleteMigration() throws IOException {
    File legacyFile = new File(indexDir, LEGACY_FILE);
    String noteId = newNoteWithParagraph("Delta", "current notebook content");

    // Failing case: legacy file must survive so the next restart can retry.
    String badNoteId = "y".repeat(300);
    writeLegacyIndexFile(legacyFile,
        new LegacyEntry(badNoteId + "/paragraph/p1", badNoteId, "gamma content", "Gamma"));
    EmbeddingSearch failedAttempt = openSearch();
    assertTrue(legacyFile.exists(), "legacy file must not be deleted when migration fails");
    failedAttempt.close();

    // Replace it with migratable data and confirm success deletes it.
    assertTrue(legacyFile.delete());
    writeLegacyIndexFile(legacyFile,
        new LegacyEntry(noteId + "/paragraph/p1", "Delta", "delta content", "Delta"));
    EmbeddingSearch succeeded = openSearch();
    assertFalse(legacyFile.exists(), "legacy file must be deleted once migration fully succeeds");
    succeeded.close();
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      latch.countDown();
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** One entry for {@link #writeLegacyIndexFile}. */
  private static final class LegacyEntry {
    final String docId;
    final String noteName;
    final String text;
    final String title;

    LegacyEntry(String docId, String noteName, String text, String title) {
      this.docId = docId;
      this.noteName = noteName;
      this.text = text;
      this.title = title;
    }
  }

  /** Writes a shard whose header promises {@code 2} entries but only fully provides one —
   *  the first entry (containing {@code survivingText}) is written in full, then the
   *  second entry is cut off partway through its fields, so reading it throws. Reproduces
   *  "one or more entries read successfully, then a failure" rather than failing on the
   *  shard header itself. */
  private static void writeTruncatedShard(File file, String noteId, String survivingText)
      throws IOException {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(baos)) {
      out.writeInt(1); // SHARD_VERSION
      out.writeUTF(noteId);
      out.writeInt(2); // claims two entries
      // Entry 1: fully valid.
      out.writeUTF(noteId + "/paragraph/first-id");
      out.writeUTF("SomeNote");
      out.writeUTF(survivingText);
      out.writeUTF("");
      out.writeUTF("");
      out.writeUTF("");
      for (int i = 0; i < 384; i++) {
        out.writeFloat(0f);
      }
      // Entry 2: only the docId is written, then the stream ends — reading noteName next
      // hits EOF partway through the entry.
      out.writeUTF(noteId + "/paragraph/second-id");
    }
    Files.write(file.toPath(), baos.toByteArray());
  }

  @Test
  void corruptSingleShardRebuildsOnlyThatNoteOnNextStartup()
      throws IOException, InterruptedException {
    String goodNote = newNoteWithParagraph("GoodNote", "select * from good_table");
    String badNote = newNoteWithParagraph("BadNote", "select * from bad_table");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(goodNote);
    search.addNoteIndex(badNote);
    search.close();

    byte[] goodShardBefore = Files.readAllBytes(shardFile(goodNote).toPath());
    // Corrupt the bad note's shard: valid file, garbage version header.
    Files.write(shardFile(badNote).toPath(), new byte[] {0, 0, 0, 99, 1, 2, 3});

    EmbeddingSearch search2 = openSearch(); // constructor: loadAllShards() finds the corruption
    assertFalse(shardFile(badNote).exists(),
        "corrupt shard should be deleted rather than left on disk");

    // Corrupt-shard rebuilds are scheduled via notebook.addInitConsumer, same mechanism as a
    // normal server startup bootstrap — drive it the same way the server would.
    notebook.initNotebook();
    assertTrue(notebook.waitForFinishInit(10, TimeUnit.SECONDS));

    byte[] goodShardAfter = Files.readAllBytes(shardFile(goodNote).toPath());
    assertArrayEquals(goodShardBefore, goodShardAfter,
        "the untouched note's shard must never be rewritten just because another note's "
            + "shard was corrupt — that's the point of sharding by note");

    List<Map<String, String>> results = search2.query("bad_table", id -> true);
    assertTrue(results.stream().anyMatch(r -> r.get("text").contains("bad_table")),
        "the corrupted note's content should have been rebuilt from the notebook");
    search2.close();
  }

  @Test
  void missingSingleShardRebuildsOnlyThatNoteOnNextStartup()
      throws IOException, InterruptedException {
    String presentNote = newNoteWithParagraph("PresentNote", "select * from present_table");
    String missingNote = newNoteWithParagraph("MissingNote", "select * from missing_table");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(presentNote);
    search.addNoteIndex(missingNote);
    search.close();

    byte[] presentShardBefore = Files.readAllBytes(shardFile(presentNote).toPath());
    assertTrue(shardFile(missingNote).delete());

    EmbeddingSearch reopened = openSearch();
    notebook.initNotebook();
    assertTrue(notebook.waitForFinishInit(10, TimeUnit.SECONDS));

    assertArrayEquals(presentShardBefore, Files.readAllBytes(shardFile(presentNote).toPath()),
        "an existing shard must remain unchanged when a different note's shard is missing");
    assertTrue(reopened.query("missing_table", id -> true).stream()
        .anyMatch(r -> r.get("text").contains("missing_table")),
        "the note whose shard vanished should be rebuilt from the notebook");

    reopened.close();
    assertTrue(shardFile(missingNote).exists(),
        "the rebuilt note should be persisted again on flush");
  }

  @Test
  void orphanShardIsDeletedAndNotLoadedOnStartup() throws IOException {
    String noteId = newNoteWithParagraph("RemovedNote", "content from a removed note");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteId);
    search.close();
    assertTrue(shardFile(noteId).exists());

    // removeCorruptedNote deliberately has no NoteRemoveEvent. This also models a process
    // stopping after notebook deletion was persisted but before the asynchronous search
    // event or dirty-shard flush completed.
    notebook.removeCorruptedNote(noteId, AuthenticationInfo.ANONYMOUS);

    EmbeddingSearch reopened = openSearch();

    assertFalse(shardFile(noteId).exists(),
        "a shard whose note no longer exists should be removed during startup");
    assertTrue(reopened.query("content from a removed note", id -> true).isEmpty(),
        "orphan shard entries must not be loaded into the in-memory search index");
    reopened.close();
  }

  @Test
  void concurrentParagraphMutationsOnSameNoteStayConsistent() throws Exception {
    String noteId = notebook.createNote("Concurrent", AuthenticationInfo.ANONYMOUS);
    int paragraphCount = 20;
    List<String> paragraphIds = new ArrayList<>();
    notebook.processNote(noteId, note -> {
      for (int i = 0; i < paragraphCount; i++) {
        Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p.setText("paragraph " + i);
        paragraphIds.add(p.getId());
      }
      return null;
    });

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteId);

    // Half the paragraphs get deleted, half get re-indexed, all concurrently on one note.
    // deleteParagraphIndex previously took no lock at all — this is the regression test for
    // that fix.
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch done = new CountDownLatch(paragraphCount);
    for (int i = 0; i < paragraphCount; i++) {
      final String pid = paragraphIds.get(i);
      final boolean delete = i % 2 == 0;
      pool.submit(() -> {
        try {
          if (delete) {
            search.deleteParagraphIndex(noteId, pid);
          } else {
            search.updateParagraphIndex(noteId, pid);
          }
        } finally {
          done.countDown();
        }
      });
    }
    assertTrue(done.await(30, TimeUnit.SECONDS), "all concurrent mutations should complete");
    pool.shutdown();

    search.close(); // must not throw, and must persist a consistent shard

    EmbeddingSearch search2 = openSearch();
    int remaining = 0;
    for (int i = 1; i < paragraphCount; i += 2) {
      final String expectedText = "paragraph " + i;
      List<Map<String, String>> r = search2.query(expectedText, id -> true);
      if (r.stream().anyMatch(res -> res.get("text").contains(expectedText))) {
        remaining++;
      }
    }
    assertEquals(paragraphCount / 2, remaining,
        "every non-deleted paragraph should have survived the concurrent mutations");
    search2.close();
  }

  @Test
  void flushOnlyWritesDirtyNotes() throws IOException {
    String noteA = newNoteWithParagraph("NoteA", "select * from a");
    String noteB = newNoteWithParagraph("NoteB", "select * from b");

    EmbeddingSearch search = openSearch();
    search.addNoteIndex(noteA);
    search.addNoteIndex(noteB);
    search.close();

    byte[] noteBBefore = Files.readAllBytes(shardFile(noteB).toPath());

    EmbeddingSearch search2 = openSearch();
    String paragraphId = lastParagraphId(noteA);
    notebook.processNote(noteA, note -> {
      note.getLastParagraph().setText("changed only in note A");
      return null;
    });
    search2.updateParagraphIndex(noteA, paragraphId);
    // Note B was never touched, so it was never added to dirtyNoteIds.
    search2.close();

    byte[] noteBAfter = Files.readAllBytes(shardFile(noteB).toPath());
    assertArrayEquals(noteBBefore, noteBAfter, "non-dirty note must not be rewritten on flush");
  }

  /** Writes a legacy (pre-ZEPPELIN-6412) single-file index with one or more entries,
   *  matching the format {@code EmbeddingSearch.loadLegacyIndex} reads:
   *  [int version=3][int count] then per entry [utf docId][utf noteName][utf text]
   *  [utf title][utf tables][utf output][float[384] embedding]. */
  private static void writeLegacyIndexFile(File file, LegacyEntry... entries) throws IOException {
    try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
      out.writeInt(3); // legacy INDEX_VERSION
      out.writeInt(entries.length);
      for (LegacyEntry e : entries) {
        out.writeUTF(e.docId);
        out.writeUTF(e.noteName);
        out.writeUTF(e.text);
        out.writeUTF(e.title);
        out.writeUTF(""); // tables
        out.writeUTF(""); // output
        for (int i = 0; i < 384; i++) {
          out.writeFloat(0f);
        }
      }
    }
  }
}
