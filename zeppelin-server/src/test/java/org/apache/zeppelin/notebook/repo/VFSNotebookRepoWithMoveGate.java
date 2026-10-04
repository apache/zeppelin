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
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Test-only subclass of {@link VFSNotebookRepo} that parks the first note
 * {@code move(noteId, ...)} call after arming. {@code NoteManager#moveNote} calls this method
 * while holding the NoteManager monitor, so a parked call pins the monitor in the held state
 * and lets a test check whether other mutations wait for it.
 */
public class VFSNotebookRepoWithMoveGate extends VFSNotebookRepo {

  private static final long GATE_SELF_TIMEOUT_SECONDS = 30;

  private final AtomicBoolean armed = new AtomicBoolean(false);
  private volatile CountDownLatch arrivedLatch;
  private volatile CountDownLatch releaseLatch;

  /**
   * Arm the gate. Only the next note {@code move()} call parks; later calls pass through.
   */
  public void armGate() {
    arrivedLatch = new CountDownLatch(1);
    releaseLatch = new CountDownLatch(1);
    armed.set(true);
  }

  /**
   * Wait for the gated {@code move()} call to arrive and park. Returns false if it does not
   * arrive within the timeout so the caller can fail with a clear message instead of hanging.
   */
  public boolean awaitArrival(long timeout, TimeUnit unit) throws InterruptedException {
    return arrivedLatch.await(timeout, unit);
  }

  /**
   * Let the parked {@code move()} call resume.
   */
  public void release() {
    releaseLatch.countDown();
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    if (armed.compareAndSet(true, false)) {
      arrivedLatch.countDown();
      try {
        // Self-timeout so a test that forgets release() fails fast instead of hanging.
        releaseLatch.await(GATE_SELF_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    super.move(noteId, notePath, newNotePath, subject);
  }
}
