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

package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Test-only subclass of {@link VFSNotebookRepo} that parks the first {@code get()} call after
 * arming, once the note has already been read from disk. This reproduces the reload path taken
 * by {@code NoteManager#moveNote} when the re-save block (leaf name changed) misses the note
 * cache: {@code loadAndProcessNote} calls {@code NotebookRepo#get()} to reload the note before
 * re-saving it at the (possibly stale) target path passed into the outer {@code moveNote} call.
 * Parking here, after the disk read, lets a second, concurrent {@code moveNote} call for the
 * same note run to completion (including its own re-save skip, when the leaf name did not
 * change) before the parked call resumes and saves using its now-stale destination path.
 */
public class VFSNotebookRepoWithGetGate extends VFSNotebookRepo {

  private static final long GATE_SELF_TIMEOUT_SECONDS = 30;

  private final AtomicBoolean armed = new AtomicBoolean(false);
  private volatile CountDownLatch arrivedLatch;
  private volatile CountDownLatch releaseLatch;

  /**
   * Arm the gate. Only the next {@code get()} call parks; every call afterwards passes
   * through untouched, so filler note loads and repeated reads do not get caught by mistake.
   */
  public void armGate() {
    arrivedLatch = new CountDownLatch(1);
    releaseLatch = new CountDownLatch(1);
    armed.set(true);
  }

  /**
   * Wait for the gated {@code get()} call to arrive and park. Returns false, instead of
   * blocking forever, if it never arrives within the timeout so the caller can fail the test
   * with a clear message rather than hang.
   */
  public boolean awaitArrival(long timeout, TimeUnit unit) throws InterruptedException {
    return arrivedLatch.await(timeout, unit);
  }

  /**
   * Let the parked {@code get()} call resume and return to its caller.
   */
  public void release() {
    releaseLatch.countDown();
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    Note note = super.get(noteId, notePath, subject);
    if (armed.compareAndSet(true, false)) {
      arrivedLatch.countDown();
      try {
        // Self-timeout so a test bug (forgetting to call release()) fails fast instead of
        // hanging the build forever.
        releaseLatch.await(GATE_SELF_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return note;
  }
}
