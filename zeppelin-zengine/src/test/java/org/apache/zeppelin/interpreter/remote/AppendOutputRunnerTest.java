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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AppendOutputRunnerTest {

  private static final int NUM_EVENTS = 10000;
  private static final int NUM_CLUBBED_EVENTS = 100;
  private static final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
  private static ScheduledFuture<?> future = null;
  /* It is being accessed by multiple threads.
   * While loop for 'loopForBufferCompletion' could
   * run for-ever.
   */
  private volatile static int numInvocations = 0;

  @After
  public void afterEach() {
    if (future != null) {
      future.cancel(true);
    }
  }

  @Test
  public void testSingleEvent() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    String[][] buffer = {{"note", "para", "data\n"}};

    loopForCompletingEvents(listener, 1, buffer);
    verify(listener, times(1)).onOutputAppend(any(String.class), any(String.class), anyInt(), any(String.class));
    verify(listener, times(1)).onOutputAppend("note", "para", 0, "data\n");
  }

  @Test
  public void testMultipleEventsOfSameParagraph() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    String note1 = "note1";
    String para1 = "para1";
    String[][] buffer = {
        {note1, para1, "data1\n"},
        {note1, para1, "data2\n"},
        {note1, para1, "data3\n"}
    };

    loopForCompletingEvents(listener, 1, buffer);
    verify(listener, times(1)).onOutputAppend(any(String.class), any(String.class), anyInt(), any(String.class));
    verify(listener, times(1)).onOutputAppend(note1, para1, 0, "data1\ndata2\ndata3\n");
  }

  @Test
  public void testMultipleEventsOfDifferentParagraphs() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    String note1 = "note1";
    String note2 = "note2";
    String para1 = "para1";
    String para2 = "para2";
    String[][] buffer = {
        {note1, para1, "data1\n"},
        {note1, para2, "data2\n"},
        {note2, para1, "data3\n"},
        {note2, para2, "data4\n"}
    };
    loopForCompletingEvents(listener, 4, buffer);

    verify(listener, times(4)).onOutputAppend(any(String.class), any(String.class), anyInt(), any(String.class));
    verify(listener, times(1)).onOutputAppend(note1, para1, 0, "data1\n");
    verify(listener, times(1)).onOutputAppend(note1, para2, 0, "data2\n");
    verify(listener, times(1)).onOutputAppend(note2, para1, 0, "data3\n");
    verify(listener, times(1)).onOutputAppend(note2, para2, 0, "data4\n");
  }

  @Test
  public void testClubbedData() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    future = service.scheduleWithFixedDelay(runner, 0,
        AppendOutputRunner.BUFFER_TIME_MS, TimeUnit.MILLISECONDS);
    Thread thread = new Thread(new BombardEvents(runner));
    thread.start();
    thread.join();
    Thread.sleep(1000);

    /* NUM_CLUBBED_EVENTS is a heuristic number.
     * It has been observed that for 10,000 continuos event
     * calls, 30-40 Web-socket calls are made. Keeping
     * the unit-test to a pessimistic 100 web-socket calls.
     */
    verify(listener, atMost(NUM_CLUBBED_EVENTS)).onOutputAppend(any(String.class), any(String.class), anyInt(), any(String.class));
  }

  @Test
  public void testWarnLoggerForLargeData() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    String data = "data\n";
    int numEvents = 100000;

    for (int i=0; i<numEvents; i++) {
      runner.appendBuffer("noteId", "paraId", 0, data);
    }

    TestAppender appender = new TestAppender();
    Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);
    Logger.getLogger(RemoteInterpreterEventPoller.class);

    runner.run();
    List<LoggingEvent> log;

    int warnLogCounter;
    LoggingEvent sizeWarnLogEntry = null;
    do {
      warnLogCounter = 0;
      log = appender.getLog();
      for (LoggingEvent logEntry: log) {
        if (Level.WARN.equals(logEntry.getLevel())) {
          sizeWarnLogEntry = logEntry;
          warnLogCounter += 1;
        }
      }
    } while(warnLogCounter != 2);

    String loggerString = "Processing size for buffered append-output is high: " +
        (data.length() * numEvents) + " characters.";
    assertTrue(loggerString.equals(sizeWarnLogEntry.getMessage()));
  }

  private class BombardEvents implements Runnable {

    private final AppendOutputRunner runner;

    private BombardEvents(AppendOutputRunner runner) {
      this.runner = runner;
    }

    @Override
    public void run() {
      String noteId = "noteId";
      String paraId = "paraId";
      for (int i=0; i<NUM_EVENTS; i++) {
        runner.appendBuffer(noteId, paraId, 0, "data\n");
      }
    }
  }

  private class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> log = new ArrayList<>();

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    protected void append(final LoggingEvent loggingEvent) {
        log.add(loggingEvent);
    }

    @Override
    public void close() {
    }

    public List<LoggingEvent> getLog() {
        return new ArrayList<>(log);
    }
  }

  private void prepareInvocationCounts(RemoteInterpreterProcessListener listener) {
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        numInvocations += 1;
        return null;
      }
    }).when(listener).onOutputAppend(any(String.class), any(String.class), anyInt(), any(String.class));
  }

  private void loopForCompletingEvents(RemoteInterpreterProcessListener listener,
      int numTimes, String[][] buffer) {
    numInvocations = 0;
    prepareInvocationCounts(listener);
    AppendOutputRunner runner = new AppendOutputRunner(listener);
    for (String[] bufferElement: buffer) {
      runner.appendBuffer(bufferElement[0], bufferElement[1], 0, bufferElement[2]);
    }
    future = service.scheduleWithFixedDelay(runner, 0,
        AppendOutputRunner.BUFFER_TIME_MS, TimeUnit.MILLISECONDS);
    long startTimeMs = System.currentTimeMillis();
    while(numInvocations != numTimes) {
      if (System.currentTimeMillis() - startTimeMs > 2000) {
        fail("Buffered events were not sent for 2 seconds");
      }
    }
  }
}