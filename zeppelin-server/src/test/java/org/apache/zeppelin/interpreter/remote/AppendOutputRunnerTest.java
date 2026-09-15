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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.List;

class AppendOutputRunnerTest {
  @Test
  void batchesAdjacentAppendsAndHandlesEmptyBatches() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    batch.add(new AppendOutputBuffer("note", "para", 0, null, "a"));
    batch.add(new AppendOutputBuffer("note", "para", 0, null, "b"));
    verifyNoInteractions(listener);
    runner.run(batch);
    batch.clear();
    batch.add(new AppendOutputBuffer("note", "para", 0, null, "c"));
    runner.run(batch);
    batch.clear();
    runner.run(batch);
    InOrder order = inOrder(listener);
    order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "ab");
    order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "c");
    order.verifyNoMoreInteractions();
  }

  @Test
  void preservesOrderAndDoesNotMergeDifferentOutputKeys() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    batch.add(new AppendOutputBuffer("note:1", "p:1", 0, null, "first"));
    batch.add(new AppendOutputBuffer("note:1", "p:2", 0, null, "second"));
    batch.add(new AppendOutputBuffer("note:1", "p:1", 1, null, "third"));
    batch.add(new AppendOutputBuffer("note:2", "p:1", 1, null, "fourth"));
    batch.add(new AppendOutputBuffer("note:1", "p:1", 0, null, "fifth"));
    runner.run(batch);
    InOrder order = inOrder(listener);
    order.verify(listener).onParagraphOutputAppend("note:1", "p:1", 0, null, "first");
    order.verify(listener).onParagraphOutputAppend("note:1", "p:2", 0, null, "second");
    order.verify(listener).onParagraphOutputAppend("note:1", "p:1", 1, null, "third");
    order.verify(listener).onParagraphOutputAppend("note:2", "p:1", 1, null, "fourth");
    order.verify(listener).onParagraphOutputAppend("note:1", "p:1", 0, null, "fifth");
    order.verifyNoMoreInteractions();
  }

  @Test
  void staleAppendDoesNotDiscardLaterOutputOrLeakIntoItsBuffer() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    doThrow(new IllegalStateException("removed")).when(listener)
        .onParagraphOutputAppend("note", "gone", 0, null, "bad");
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    batch.add(new AppendOutputBuffer("note", "gone", 0, null, "bad"));
    batch.add(new AppendOutputBuffer("note", "present", 0, null, "good"));
    runner.run(batch);
    InOrder order = inOrder(listener);
    order.verify(listener).onParagraphOutputAppend("note", "gone", 0, null, "bad");
    order.verify(listener).onParagraphOutputAppend("note", "present", 0, null, "good");
    order.verifyNoMoreInteractions();
  }

  @Test
  void largeAppendStreamIsDeliveredAsOneBatchWithoutLosingData() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    List<String> received = new ArrayList<>();
    doAnswer(call -> {
      received.add(call.getArgument(3));
      return null;
    }).when(listener).onParagraphOutputAppend(anyString(), anyString(), anyInt(), null, anyString());
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      String token = i + "\n";
      expected.append(token);
      batch.add(new AppendOutputBuffer("note", "para", 0, null, token));
    }
    runner.run(batch);
    assertEquals(List.of(expected.toString()), received);
  }

  @ParameterizedTest
  @ValueSource(ints = {100000, 100001})
  void warnsOnlyWhenBufferedOutputExceedsTheSizeThreshold(int size) {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    String output = "a".repeat(size);
    batch.add(new AppendOutputBuffer("note", "para", 0, null, output));
    List<String> sizeWarnings = new ArrayList<>();
    AppenderSkeleton appender = new AppenderSkeleton() {
      @Override
      protected void append(LoggingEvent event) {
        String message = event.getRenderedMessage();
        if (Level.WARN.equals(event.getLevel())
            && message.startsWith("Processing size for buffered append-output is high:")) {
          sizeWarnings.add(message);
        }
      }

      @Override
      public void close() {
      }

      @Override
      public boolean requiresLayout() {
        return false;
      }
    };
    Logger logger = Logger.getLogger(AppendOutputRunner.class);
    Level previousLevel = logger.getLevel();
    boolean previousAdditivity = logger.getAdditivity();
    logger.setLevel(Level.DEBUG);
    logger.setAdditivity(false);
    logger.addAppender(appender);
    try {
      runner.run(batch);
      assertEquals(size > 100000 ? List.of(
          "Processing size for buffered append-output is high: " + size + " characters.")
          : List.of(), sizeWarnings);
      verify(listener).onParagraphOutputAppend("note", "para", 0, null, output);
    } finally {
      logger.removeAppender(appender);
      logger.setLevel(previousLevel);
      logger.setAdditivity(previousAdditivity);
      appender.close();
    }
  }

  @Test
  void disallowedDeliverySkipsTheBatchAndDoesNotAffectLaterBatches() {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    List<AppendOutputBuffer> batch = new ArrayList<>();
    batch.add(new AppendOutputBuffer("note", "para", 0, null, "discarded"));
    runner.run(batch, () -> false);
    batch.clear();
    verifyNoInteractions(listener);
    batch.add(new AppendOutputBuffer("note", "para", 0, null, "new"));
    runner.run(batch);
    InOrder order = inOrder(listener);
    order.verify(listener).onParagraphOutputAppend("note", "para", 0, null, "new");
    order.verifyNoMoreInteractions();
  }
}
