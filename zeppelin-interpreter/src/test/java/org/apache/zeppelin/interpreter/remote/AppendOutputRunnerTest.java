package org.apache.zeppelin.interpreter.remote;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

public class AppendOutputRunnerTest {

  private static final int NUM_EVENTS = 10000;
  private static final int NUM_CLUBBED_EVENTS = 100;

  @Test
  public void testSingleEvent() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner.startRunner(listener);
    AppendOutputRunner.appendBuffer("note", "para", "data\n");
    Thread.sleep(2000);
    verify(listener, times(1)).onOutputAppend(any(String.class), any(String.class), any(String.class));
    verify(listener, times(1)).onOutputAppend("note", "para", "data\n");
    AppendOutputRunner.stopRunnerForUnitTests();
  }

  @Test
  public void testMultipleEventsOfSameParagraph() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    String note1 = "note1";
    String para1 = "para1";
    AppendOutputRunner.appendBuffer(note1, para1, "data1\n");
    AppendOutputRunner.appendBuffer(note1, para1, "data2\n");
    AppendOutputRunner.appendBuffer(note1, para1, "data3\n");
    AppendOutputRunner.startRunner(listener);
    Thread.sleep(1000);
    verify(listener, times(1)).onOutputAppend(any(String.class), any(String.class), any(String.class));
    verify(listener, times(1)).onOutputAppend(note1, para1, "data1\ndata2\ndata3\n");
    AppendOutputRunner.stopRunnerForUnitTests();
  }

  @Test
  public void testMultipleEventsOfDifferentParagraphs() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    String note1 = "note1";
    String note2 = "note2";
    String para1 = "para1";
    String para2 = "para2";
    AppendOutputRunner.appendBuffer(note1, para1, "data1\n");
    AppendOutputRunner.appendBuffer(note1, para2, "data2\n");
    AppendOutputRunner.appendBuffer(note2, para1, "data3\n");
    AppendOutputRunner.appendBuffer(note2, para2, "data4\n");
    AppendOutputRunner.startRunner(listener);
    Thread.sleep(1000);
    verify(listener, times(4)).onOutputAppend(any(String.class), any(String.class), any(String.class));
    verify(listener, times(1)).onOutputAppend(note1, para1, "data1\n");
    verify(listener, times(1)).onOutputAppend(note1, para2, "data2\n");
    verify(listener, times(1)).onOutputAppend(note2, para1, "data3\n");
    verify(listener, times(1)).onOutputAppend(note2, para2, "data4\n");
    AppendOutputRunner.stopRunnerForUnitTests();
  }

  @Test
  public void testClubbedData() throws InterruptedException {
    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);

    AppendOutputRunner.startRunner(listener);
    Thread thread = new Thread(new BombardEvents());
    thread.start();
    thread.join();
    Thread.sleep(1000);
    /* NUM_CLUBBED_EVENTS is a heuristic number.
     * It has been observed that for 10,000 continuos event
     * calls, 30-40 Web-socket calls are made. Keeping
     * the unit-test to a pessimistic 100 web-socket calls.
     */
    verify(listener, atMost(NUM_CLUBBED_EVENTS)).onOutputAppend(any(String.class), any(String.class), any(String.class));
    AppendOutputRunner.stopRunnerForUnitTests();
  }

  @Test
  public void testWarnLoggerForLargeData() throws InterruptedException {
    String data = "data\n";
    int numEvents = 100000;
    for (int i=0; i<numEvents; i++) {
      AppendOutputRunner.appendBuffer("noteId", "paraId", data);
    }

    TestAppender appender = new TestAppender();
    Logger logger = Logger.getRootLogger();
    logger.addAppender(appender);
    Logger.getLogger(RemoteInterpreterEventPoller.class);

    RemoteInterpreterProcessListener listener = mock(RemoteInterpreterProcessListener.class);
    AppendOutputRunner.startRunner(listener);
    Thread.sleep(1000);

    List<LoggingEvent> log = appender.getLog();
    LoggingEvent sizeWarnLogEntry = null;
    for (LoggingEvent logEntry: log) {
      if (Level.WARN.equals(logEntry.getLevel())) {
        sizeWarnLogEntry = logEntry;
      }
    }
    String loggerString = "Processing size for buffered append-output is high: " +
        (data.length() * numEvents) + " characters.";
    assertTrue(loggerString.equals(sizeWarnLogEntry.getMessage()));
  }

  private class BombardEvents implements Runnable {

    @Override
    public void run() {
      String noteId = "noteId";
      String paraId = "paraId";
      for (int i=0; i<NUM_EVENTS; i++) {
        AppendOutputRunner.appendBuffer(noteId, paraId, "data\n");
      }
    }
  }

  private class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> log = new ArrayList<LoggingEvent>();

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
        return new ArrayList<LoggingEvent>(log);
    }
  }
}