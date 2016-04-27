package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

/**
 * Check and test if zeppelinhub connection is still open.
 * 
 * @author anthonyc
 *
 *
public class ZeppelinhubConntection extends Retryable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubConntection.class);
  private long delay;
  private long interval;
  private long timeout;
  private final TimeUnit timeUnit = TimeUnit.MILLISECONDS;
  
  private ZeppelinhubConntection(Session session, long delay, long interval, long timeout) {
    ZeppelinhubSession = session;
    this.delay = delay;
    this.interval = interval;
    this.timeout = timeout;
  }
  
  @Override
  public void run() {
    try {
      execute(delay, interval, timeout);
    } catch (Exception e) {
      LOG.error(
          "Failed to execute retryable ZeppelinHub connection task with interval {} after {} ms {}",
          interval, timeout, e);
    }
  }

  @Override
  protected void attempt() throws Exception {
    LOG.info("Connecting to ZeppelinHub");
  }

}
*/
