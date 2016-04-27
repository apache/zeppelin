package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * todo: add description.
 * 
 * @author anthonyc
 *
 */
abstract class Retryable {
  private static final Logger LOG = LoggerFactory.getLogger(Retryable.class);
  protected abstract void attempt() throws Exception;

  /*
   * @delay: initial delay (ms)
   * 
   * @interval: interval between re-trials (ms)
   * 
   * @timeout: timeout after which re-trials are aborted (ms)
   */
  public void execute(long delay, long interval, long timeout) throws Exception {
    long start = System.currentTimeMillis();
    if (delay > 0L) {
      sleep(delay);
    }
    while (true) {
      try {
        attempt();
        return;
      } catch (Exception e) {
        if (System.currentTimeMillis() - start < timeout) {
          LOG.info("Retrying in " + interval + " ms");
          sleep(interval);
          // continue
        } else {
          throw e;
        }
      }
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interruptedException) {
      // continue
    }
  }

}
