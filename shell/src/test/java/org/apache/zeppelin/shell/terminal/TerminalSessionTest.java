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

package org.apache.zeppelin.shell.terminal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.pty4j.PtyProcess;
import jakarta.websocket.Session;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TerminalSessionTest {
  @Test
  void commandsUseOneWorkerInOrderAndAllWorkersStopOnClose() throws Exception {
    CountDownLatch reads = new CountDownLatch(2);
    Set<Thread> readerWorkers = ConcurrentHashMap.newKeySet();
    InputStream input = mock(InputStream.class);
    when(input.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
      readerWorkers.add(Thread.currentThread());
      reads.countDown();
      return -1;
    });
    CountDownLatch writes = new CountDownLatch(100);
    Set<Thread> workers = new HashSet<>();
    ByteArrayOutputStream output = new ByteArrayOutputStream() {
      @Override
      public synchronized void write(byte[] bytes, int offset, int length) {
        workers.add(Thread.currentThread());
        super.write(bytes, offset, length);
        writes.countDown();
      }
    };
    PtyProcess process = mock(PtyProcess.class);
    when(process.getInputStream()).thenReturn(input);
    when(process.getErrorStream()).thenReturn(input);
    when(process.getOutputStream()).thenReturn(output);
    when(process.waitFor(5L, TimeUnit.SECONDS)).thenReturn(true);

    StringBuilder expected = new StringBuilder();
    TerminalSession terminal = new TerminalSession(mock(Session.class), process);
    try {
      assertTrue(reads.await(5, TimeUnit.SECONDS));
      assertEquals(2, readerWorkers.size());
      terminal.onCommand(null);
      terminal.onCommand("");
      for (int i = 0; i < 100; i++) {
        String command = "command-" + i + "\r";
        expected.append(command);
        terminal.onCommand(command);
      }
      assertTrue(writes.await(5, TimeUnit.SECONDS));
      assertEquals(expected.toString(), output.toString());
      assertEquals(1, workers.size());
    } finally {
      terminal.close();
    }

    assertDoesNotThrow(() -> terminal.onCommand("after close"));
    terminal.close();
    verify(process).destroy();
    verify(process, never()).destroyForcibly();
    workers.addAll(readerWorkers);
    for (Thread worker : workers) {
      worker.join(5000);
      assertFalse(worker.isAlive());
    }
    assertEquals(expected.toString(), output.toString());
  }

  @Test
  void closeForciblyDestroysProcessThatDoesNotExit() throws Exception {
    PtyProcess process = mock(PtyProcess.class);
    when(process.getInputStream()).thenReturn(InputStream.nullInputStream());
    when(process.getErrorStream()).thenReturn(InputStream.nullInputStream());
    when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    TerminalSession terminal = new TerminalSession(mock(Session.class), process);

    terminal.close();
    terminal.close();

    verify(process).destroy();
    verify(process).destroyForcibly();
  }

  @Test
  void initializationFailureDestroysProcess() throws Exception {
    PtyProcess process = mock(PtyProcess.class);
    when(process.getInputStream()).thenThrow(new IllegalStateException("stream unavailable"));
    when(process.waitFor(5L, TimeUnit.SECONDS)).thenReturn(true);

    assertThrows(IllegalStateException.class,
        () -> new TerminalSession(mock(Session.class), process));

    verify(process).destroy();
  }

}
