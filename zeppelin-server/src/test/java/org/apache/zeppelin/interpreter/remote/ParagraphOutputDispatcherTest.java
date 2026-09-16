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

package org.apache.zeppelin.interpreter.remote;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;

class ParagraphOutputDispatcherTest {
  @Test
  void boundariesFlushEarlierAppendsAndKeepLaterAppendsInOrder() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      dispatcher.appendOutput("note", "para", 0, "a");
      dispatcher.appendOutput("note", "para", 0, "b");
      verifyNoInteractions(listener);
      dispatcher.updateOutput("note", "para", 0, InterpreterResult.Type.TEXT, "replacement")
          .get(5, TimeUnit.SECONDS);
      dispatcher.appendOutput("note", "para", 0, "after");
      dispatcher.checkpointOutput("note", "para").get(5, TimeUnit.SECONDS);
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("note", "para", 0, "ab");
      order.verify(listener).onOutputUpdated(
          "note", "para", 0, InterpreterResult.Type.TEXT, "replacement");
      order.verify(listener).onOutputAppend("note", "para", 0, "after");
      order.verify(listener).checkpointOutput("note", "para");
      order.verifyNoMoreInteractions();
    }
  }

  @Test
  void scheduledFlushDeliversAppendsWithoutRpcBoundaries() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch delivered = new CountDownLatch(1);
    doAnswer(call -> {
      delivered.countDown();
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "periodic");
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      dispatcher.appendOutput("note", "para", 0, "periodic");
      timer.scheduleWithFixedDelay(dispatcher::flush, 0,
          AppendOutputRunner.BUFFER_TIME_MS, TimeUnit.MILLISECONDS);
      assertTrue(delivered.await(5, TimeUnit.SECONDS));
      dispatcher.checkpointOutput("note", "para").get(5, TimeUnit.SECONDS);
      verify(listener).onOutputAppend("note", "para", 0, "periodic");
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  void flushAndSubmissionDoNotWaitForCallbacksOrLoseAnInFlightFlushRequest() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch later = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "slow");
    doAnswer(call -> {
      later.countDown();
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "later");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      dispatcher.appendOutput("note", "para", 0, "slow");
      dispatcher.flush();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
        dispatcher.appendOutput("note", "para", 0, "later");
        dispatcher.flush();
      });
      release.countDown();
      // No further tick or boundary may rescue a lost flush request.
      assertTrue(later.await(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
    }
  }

  @Test
  void boundaryFailureIsReportedAndDoesNotDiscardLaterEvents() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    doThrow(new IllegalStateException("removed")).when(listener)
        .onOutputUpdated("note", "gone", 0, InterpreterResult.Type.TEXT, "bad");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      Future<Void> failed = dispatcher.updateOutput(
          "note", "gone", 0, InterpreterResult.Type.TEXT, "bad");
      ExecutionException failure = assertThrows(ExecutionException.class,
          () -> failed.get(5, TimeUnit.SECONDS));
      assertEquals("removed", failure.getCause().getMessage());
      dispatcher.appendOutput("note", "present", 0, "good");
      dispatcher.checkpointOutput("note", "present").get(5, TimeUnit.SECONDS);
      verify(listener).onOutputAppend("note", "present", 0, "good");
    }
  }

  @Test
  void largeNoteYieldsAndKeepsAllOutputAcrossWorkerBatches() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    StringBuilder received = new StringBuilder();
    AtomicInteger callbacks = new AtomicInteger();
    AtomicInteger sizeWhenSmallRan = new AtomicInteger();
    doAnswer(call -> {
      if ("large".equals(call.getArgument(0))) {
        received.append((String) call.getArgument(3));
        if (callbacks.incrementAndGet() == 1) {
          entered.countDown();
          awaitIgnoringInterrupt(release);
        }
      } else {
        sizeWhenSmallRan.set(received.length());
      }
      return null;
    }).when(listener).onOutputAppend(anyString(), anyString(), anyInt(), anyString());
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1, 2)) {
      for (int i = 0; i < 5; i++) {
        dispatcher.appendOutput("large", "para", 0, "x");
      }
      Future<Void> largeDone = dispatcher.checkpointOutput("large", "para");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      // Submit only after a large batch has been claimed, rather than ahead of its ready entry.
      dispatcher.appendOutput("small", "para", 0, "small");
      Future<Void> smallDone = dispatcher.checkpointOutput("small", "para");
      release.countDown();
      smallDone.get(5, TimeUnit.SECONDS);
      largeDone.get(5, TimeUnit.SECONDS);
      assertTrue(sizeWhenSmallRan.get() > 0);
      assertTrue(sizeWhenSmallRan.get() < 5, "Small note must run before all large output");
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("small", "para", 0, "small");
      order.verify(listener).checkpointOutput("large", "para");
      assertEquals("x".repeat(5), received.toString());
      assertTrue(callbacks.get() < 5, "Adjacent appends must still be batched");
    } finally {
      release.countDown();
    }
  }

  @Test
  void concurrentProducersKeepOneWriterAndDeliverEveryEventOnceInProducerOrder() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maximum = new AtomicInteger();
    List<String> received = Collections.synchronizedList(new ArrayList<>());
    doAnswer(call -> {
      maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
      try {
        String output = call.getArgument(3);
        Collections.addAll(received, output.split("\n"));
      } finally {
        active.decrementAndGet();
      }
      return null;
    }).when(listener).onOutputAppend(anyString(), anyString(), anyInt(), anyString());
    ExecutorService producers = Executors.newFixedThreadPool(4);
    ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      timer.scheduleWithFixedDelay(dispatcher::flush, 0, 1, TimeUnit.MILLISECONDS);
      List<Future<?>> futures = new ArrayList<>();
      for (int producer = 0; producer < 4; producer++) {
        final int id = producer;
        futures.add(producers.submit(() -> {
          for (int i = 0; i < 1000; i++) {
            dispatcher.appendOutput("note", "para", 0, id + ":" + i + "\n");
            if (i % 100 == 0) {
              dispatcher.checkpointOutput("note", "para").get(5, TimeUnit.SECONDS);
            }
          }
          return null;
        }));
      }
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
      dispatcher.checkpointOutput("note", "para").get(5, TimeUnit.SECONDS);
      assertEquals(1, maximum.get());
      assertEquals(4000, received.size());
      assertEquals(4000, new HashSet<>(received).size());
      int[] next = new int[4];
      for (String token : received) {
        String[] parts = token.split(":");
        int producer = Integer.parseInt(parts[0]);
        assertEquals(next[producer]++, Integer.parseInt(parts[1]));
      }
    } finally {
      timer.shutdownNow();
      producers.shutdownNow();
    }
  }

  @Test
  void readyNotesWaitForWorkerCapacityAndReuseReleasedWorkers() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch occupied = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maximum = new AtomicInteger();
    doAnswer(call -> {
      maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
      try {
        if (!"C".equals(call.getArgument(0))) {
          occupied.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS));
        }
      } finally {
        active.decrementAndGet();
      }
      return null;
    }).when(listener).checkpointOutput(anyString(), anyString());
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 2)) {
      Future<Void> first = dispatcher.checkpointOutput("A", "para");
      Future<Void> second = dispatcher.checkpointOutput("B", "para");
      assertTrue(occupied.await(5, TimeUnit.SECONDS));
      Future<Void> third = dispatcher.checkpointOutput("C", "para");
      assertThrows(TimeoutException.class, () -> third.get(100, TimeUnit.MILLISECONDS));
      release.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
      third.get(5, TimeUnit.SECONDS);
      assertEquals(2, maximum.get());
      verify(listener).checkpointOutput("A", "para");
      verify(listener).checkpointOutput("B", "para");
      verify(listener).checkpointOutput("C", "para");
    } finally {
      release.countDown();
    }
  }

  @Test
  void idleQueuesAreReclaimedAndTheSameNoteCanDeliverAgain() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener)) {
      for (int i = 0; i < 200; i++) {
        dispatcher.appendOutput("note" + i, "para", 0, "data");
        dispatcher.checkpointOutput("note" + i, "para").get(5, TimeUnit.SECONDS);
      }
      await().atMost(Duration.ofSeconds(5)).until(() -> dispatcher.pendingNoteCount() == 0);
      dispatcher.appendOutput("note0", "para", 0, "again");
      dispatcher.checkpointOutput("note0", "para").get(5, TimeUnit.SECONDS);
      verify(listener).onOutputAppend("note0", "para", 0, "data");
      verify(listener).onOutputAppend("note0", "para", 0, "again");
      await().atMost(Duration.ofSeconds(5)).until(() -> dispatcher.pendingNoteCount() == 0);
    }
  }

  @Test
  void closeCancelsABoundaryAlreadyDrainedBehindABlockedAppend() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    doAnswer(call -> {
      callbackThread.set(Thread.currentThread());
      entered.countDown();
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "blocked");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      dispatcher.appendOutput("note", "para", 0, "blocked");
      Future<Void> boundary = dispatcher.checkpointOutput("note", "para");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      dispatcher.close();
      assertStopped(boundary);
      release.countDown();
      callbackThread.get().join(5000);
      assertFalse(callbackThread.get().isAlive());
      verify(listener, never()).checkpointOutput("note", "para");
    } finally {
      release.countDown();
    }
  }

  @Test
  void closeFailsQueuedAndInFlightBoundariesAndRejectsNewEvents() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return null;
    }).when(listener).checkpointOutput("A", "para");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      Future<Void> inFlight = dispatcher.checkpointOutput("A", "para");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      Future<Void> queued = dispatcher.checkpointOutput("B", "para");
      dispatcher.close();
      assertStopped(inFlight);
      assertStopped(queued);
      assertEquals(0, dispatcher.pendingNoteCount());
      assertThrows(IllegalStateException.class, () ->
          dispatcher.appendOutput("A", "para", 0, "late"));
      assertThrows(IllegalStateException.class, () -> dispatcher.checkpointOutput("A", "para"));
      verify(listener, never()).checkpointOutput("B", "para");
    } finally {
      release.countDown();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shutdownStopsLaterAppendGroupsEvenWhenActiveCallbackIgnoresInterrupt(boolean withBoundary)
      throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Thread> worker = new AtomicReference<>();
    doAnswer(call -> {
      worker.set(Thread.currentThread());
      entered.countDown();
      awaitIgnoringInterrupt(release);
      return null;
    }).when(listener).onOutputAppend("note", "first", 0, "a");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      dispatcher.appendOutput("note", "first", 0, "a");
      dispatcher.appendOutput("note", "second", 0, "b");
      Future<Void> boundary = null;
      if (withBoundary) {
        boundary = dispatcher.checkpointOutput("note", "para");
      } else {
        dispatcher.flush();
      }
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      dispatcher.close();
      if (boundary != null) {
        assertStopped(boundary);
      }
      release.countDown();
      worker.get().join(5000);
      assertFalse(worker.get().isAlive());
      verify(listener).onOutputAppend("note", "first", 0, "a");
      verify(listener, never()).onOutputAppend("note", "second", 0, "b");
      verify(listener, never()).checkpointOutput("note", "para");
    } finally {
      release.countDown();
    }
  }

  @Test
  void closeReleasesAllBoundaryWaitersWithoutWaitingForAnActiveCallback() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    AtomicReference<Thread> worker = new AtomicReference<>();
    doAnswer(call -> {
      worker.set(Thread.currentThread());
      entered.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        interrupted.countDown();
        awaitIgnoringInterrupt(release);
      }
      return null;
    }).when(listener).checkpointOutput("note", "running");
    ExecutorService callers = Executors.newFixedThreadPool(4);
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      List<Future<Void>> boundaries = new ArrayList<>();
      boundaries.add(dispatcher.checkpointOutput("note", "running"));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      for (int i = 0; i < 20; i++) {
        boundaries.add(dispatcher.checkpointOutput("note", "queued" + i));
        boundaries.add(dispatcher.checkpointOutput("other", "queued" + i));
      }
      CountDownLatch waiting = new CountDownLatch(4);
      List<Future<?>> waiters = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        Future<Void> boundary = boundaries.get(i);
        waiters.add(callers.submit(() -> {
          waiting.countDown();
          assertStopped(boundary);
        }));
      }
      assertTrue(waiting.await(5, TimeUnit.SECONDS));
      assertTimeoutPreemptively(Duration.ofSeconds(1), dispatcher::close);
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      for (Future<Void> boundary : boundaries) {
        assertStopped(boundary);
      }
      for (Future<?> waiter : waiters) {
        waiter.get(5, TimeUnit.SECONDS);
      }
      assertEquals(1, release.getCount(), "Callback must still be blocked after waiters exit");
      assertThrows(IllegalStateException.class,
          () -> dispatcher.appendOutput("other", "para", 0, "late"));
      verify(listener, never()).checkpointOutput("note", "queued0");
      verify(listener, never()).checkpointOutput("other", "queued0");
      release.countDown();
      worker.get().join(5000);
      assertFalse(worker.get().isAlive(), "Shutdown must survive a callback clearing interruption");
    } finally {
      release.countDown();
      callers.shutdownNow();
    }
  }

  @Test
  void acceptedBoundaryCannotBeCancelledOrSkipped() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      awaitIgnoringInterrupt(release);
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "before");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      dispatcher.appendOutput("note", "para", 0, "before");
      Future<Void> boundary = dispatcher.checkpointOutput("note", "para");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertFalse(boundary.cancel(true));
      assertFalse(boundary.isCancelled());
      release.countDown();
      boundary.get(5, TimeUnit.SECONDS);
      verify(listener).checkpointOutput("note", "para");
    } finally {
      release.countDown();
    }
  }

  @Test
  void queuedUpdateAllUsesASnapshotOfItsReplacementList() throws Exception {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return null;
    }).when(listener).onOutputAppend("note", "para", 0, "old");
    try (ParagraphOutputDispatcher dispatcher = new ParagraphOutputDispatcher(listener, 1)) {
      dispatcher.appendOutput("note", "para", 0, "old");
      dispatcher.flush();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      List<InterpreterResultMessage> replacements = new ArrayList<>();
      replacements.add(new InterpreterResultMessage(InterpreterResult.Type.TEXT, "new"));
      Future<Void> updated = dispatcher.updateAllOutput("note", "para", replacements);
      replacements.clear();
      release.countDown();
      updated.get(5, TimeUnit.SECONDS);
      InOrder order = inOrder(listener);
      order.verify(listener).onOutputAppend("note", "para", 0, "old");
      order.verify(listener).onOutputClear("note", "para");
      order.verify(listener).onOutputUpdated("note", "para", 0, InterpreterResult.Type.TEXT, "new");
      order.verifyNoMoreInteractions();
    } finally {
      release.countDown();
    }
  }

  private void assertStopped(Future<Void> completion) {
    ExecutionException failure = assertThrows(ExecutionException.class,
        () -> completion.get(5, TimeUnit.SECONDS));
    assertTrue(failure.getCause() instanceof IllegalStateException);
  }

  private void awaitIgnoringInterrupt(CountDownLatch release) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          release.await();
          return;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
