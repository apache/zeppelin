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
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteManagerTest {
  private NoteManager noteManager;
  private ZeppelinConfiguration conf;

  @Rule
  public ExpectedException thrown = ExpectedException.none();


  @Before
  public void setUp() throws IOException {
    conf = ZeppelinConfiguration.create();
    this.noteManager = new NoteManager(new InMemoryNotebookRepo(), conf);
  }

  @Test
  public void testNoteOperations() throws IOException {
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
  public void testAddNoteRejectsDuplicatePath() throws IOException {
    thrown.expect(NotePathAlreadyExistsException.class);
    thrown.expectMessage("Note '/prod/note' existed");

    Note note1 = createNote("/prod/note");
    Note note2 = createNote("/prod/note");

    noteManager.addNote(note1, AuthenticationInfo.ANONYMOUS);
    noteManager.addNote(note2, AuthenticationInfo.ANONYMOUS);
  }

  @Test
  public void testMoveNoteRejectsDuplicatePath() throws IOException {
    thrown.expect(NotePathAlreadyExistsException.class);
    thrown.expectMessage("Note '/prod/note-1' existed");

    Note note1 = createNote("/prod/note-1");
    Note note2 = createNote("/prod/note-2");

    noteManager.addNote(note1, AuthenticationInfo.ANONYMOUS);
    noteManager.addNote(note2, AuthenticationInfo.ANONYMOUS);

    noteManager.moveNote(note2.getId(), "/prod/note-1", AuthenticationInfo.ANONYMOUS);
  }

  private Note createNote(String notePath) {
    return new Note(notePath, "test", null, null, null, null, null);
  }

  @Test
  public void testLruCache() throws IOException {

    int cacheThreshold = conf.getNoteCacheThreshold();

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

    // add cache + 1
    Note noteNew2 = createNote("/prod/notenew2");
    noteManager.addNote(noteNew2, AuthenticationInfo.ANONYMOUS);

    // since all notes in the cache are with a read lock, the cache grows
    assertEquals(cacheThreshold + 1, noteManager.getCacheSize());

    assertTrue(noteManager.containsNote(noteNew2.getPath()));
    noteManager.removeNote(noteNew2.getId(), AuthenticationInfo.ANONYMOUS);
    assertFalse(noteManager.containsNote(noteNew2.getPath()));
    assertEquals(cacheThreshold, noteManager.getCacheSize());
  }

  @Test
  public void testConcurrentOperation() throws Exception {
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
