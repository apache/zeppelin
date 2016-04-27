package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Creates a thread pool that can schedule zeppelinhub commands.
 * 
 * @author anthonyc
 *
 */
public class SchedulerService {
  
  private final ScheduledExecutorService pool;
  
  private SchedulerService(int numberOfThread) {
    pool = Executors.newScheduledThreadPool(numberOfThread);
  }
  
  public static SchedulerService create(int numberOfThread) {
    return new SchedulerService(numberOfThread);
  }
  
  public void add(Runnable service, int firstExecution, int period) {
    pool.scheduleAtFixedRate(service, firstExecution, period, TimeUnit.SECONDS);
  }
  
  public void close() {
    pool.shutdown();
  }

}
