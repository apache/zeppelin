package org.apache.zeppelin.interpreter.remote;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* This class is responsible for initializing
 * and ensuring that AppendOutputRunner is up
 * and running.
 */
public class CheckAppendOutputRunner implements Runnable {

  private static final Logger logger =
      LoggerFactory.getLogger(CheckAppendOutputRunner.class);
  private static Thread thread = null;
  private static final Boolean SYNCHRONIZER = false;
  private static ScheduledExecutorService SCHEDULED_SERVICE = null;

  /* Can only be initialized locally.*/
  private CheckAppendOutputRunner()
  {}

  @Override
  public void run() {
    synchronized (SYNCHRONIZER) {
      if (thread == null || !thread.isAlive()) {
        logger.info("Starting a AppendOutputRunner thread to buffer"
            + " and send paragraph append data.");
        thread = new Thread(new AppendOutputRunner());
        thread.start();
      }
    }
  }

  public static void startScheduler() {
    synchronized (SYNCHRONIZER) {
      if (SCHEDULED_SERVICE == null) {
        SCHEDULED_SERVICE = Executors.newSingleThreadScheduledExecutor();
        SCHEDULED_SERVICE.scheduleWithFixedDelay(
            new CheckAppendOutputRunner(), 0, 1, TimeUnit.SECONDS);
      }
    }
  }

  /* These functions are only used by unit-tests. */
  public static void stopRunnerForUnitTests() {
    thread.interrupt();
  }

  public static void startRunnerForUnitTests() {
    thread = new Thread(new AppendOutputRunner());
    thread.start();
  }
}
