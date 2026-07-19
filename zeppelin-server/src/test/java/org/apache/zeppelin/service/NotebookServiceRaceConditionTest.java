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

package org.apache.zeppelin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepoWithDelay;
import org.apache.zeppelin.notebook.scheduler.NoSchedulerService;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduction test for ZEPPELIN-5858: a concurrent 'move' (rename) and 'save' (insert
 * paragraph) on the same note can race in the notebook repo, leaving two {@code .zpln} files
 * for the same noteId (old path + new path) behind. {@link VFSNotebookRepoWithDelay} injects
 * an artificial delay to widen the race window.
 */
class NotebookServiceRaceConditionTest {

  private static NotebookService notebookService;

  private File notebookDir;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  @BeforeEach
  void setUp() throws Exception {
    notebookDir = Files.createTempDirectory("notebookDir").toAbsolutePath().toFile();
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(),
        notebookDir.getAbsolutePath());
    NoteParser noteParser = new GsonNoteParser(zConf);
    ConfigStorage storage = ConfigStorage.createConfigStorage(zConf);
    notebookRepo = new VFSNotebookRepoWithDelay(5000L);
    notebookRepo.init(zConf, noteParser);

    InterpreterSettingManager mockInterpreterSettingManager = mock(InterpreterSettingManager.class);
    InterpreterFactory mockInterpreterFactory = mock(InterpreterFactory.class);
    Credentials credentials = new Credentials();
    NoteManager noteManager = new NoteManager(notebookRepo, zConf);
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
    notebookService =
        new NotebookService(
            notebook, authorizationService, zConf, new NoSchedulerService());
  }

  @AfterEach
  void tearDown() {
    notebookDir.delete();
  }

  /**
   * Concurrent 'insertParagraph' (save) and 'renameNote' (move) on the same note. The delayed
   * repo widens the window between reading a note's path and writing to it, so both operations
   * can write a {@code .zpln} file for the same noteId: one at the old path, one at the new
   * path. Thread 2 starts the move first (delay simulates a slow remote write); thread 1 saves
   * shortly after, while the move is still in flight.
   */
  @Test
  void testConcurrentMoveAndSave() throws IOException, InterruptedException {
    // given a note
    String noteId = notebookService.createNote("/folder_1/note", "test", true, context, callback);

    // when executing 'move' (renameNote) and 'save' (insertParagraph) concurrently
    CountDownLatch latch = new CountDownLatch(2);
    ExecutorService threadPool = Executors.newFixedThreadPool(2);
    threadPool.execute(() -> {
      try {
        // ensure we 'save' after 'move' has started processing, but before 'move' has finished
        Thread.sleep(1000L);
        notebookService.insertParagraph(noteId, 1, Collections.emptyMap(), context, callback);
        latch.countDown();
      } catch (IOException | InterruptedException ex) {
        // ignore
      }
    });
    threadPool.execute(() -> {
      try {
        notebookService.renameNote(noteId, "/folder_2/note", false, context, callback);
        latch.countDown();
      } catch (IOException ex) {
        // ignore
      }
    });
    assertTrue(latch.await(100, TimeUnit.SECONDS));
    threadPool.shutdown();

    // then only a single .zpln file exists for this note under notebookDir
    List<String> zplnFiles = findZplnFiles();
    assertEquals(1, zplnFiles.size(),
        () -> "Expected exactly one .zpln file, but found: " + zplnFiles);
  }

  private List<String> findZplnFiles() throws IOException {
    Path notebookPath = notebookDir.toPath();
    try (Stream<Path> paths = Files.walk(notebookPath)) {
      return paths
          .filter(p -> p.toString().endsWith(".zpln"))
          .map(p -> notebookPath.relativize(p).toString())
          .collect(Collectors.toList());
    }
  }
}
