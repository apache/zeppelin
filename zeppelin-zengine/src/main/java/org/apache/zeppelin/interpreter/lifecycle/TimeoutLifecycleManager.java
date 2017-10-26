package org.apache.zeppelin.interpreter.lifecycle;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.LifecycleManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This lifecycle manager would close interpreter after it is timeout. By default, it is timeout
 * after no using in 1 hour.
 *
 * For now, this class only manage the lifecycle of interpreter group (will close interpreter
 * process after timeout). Managing the lifecycle of interpreter session could be done in future
 * if necessary.
 */
public class TimeoutLifecycleManager implements LifecycleManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutLifecycleManager.class);

  // ManagerInterpreterGroup -> LastTimeUsing timestamp
  private Map<ManagedInterpreterGroup, Long> interpreterGroups = new ConcurrentHashMap<>();

  private long checkInterval;
  private long timeoutThreshold;

  private Timer checkTimer;

  public TimeoutLifecycleManager(ZeppelinConfiguration zConf) {
    this.checkInterval = zConf.getLong(ZeppelinConfiguration.ConfVars
            .ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL);
    this.timeoutThreshold = zConf.getLong(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD);
    this.checkTimer = new Timer(true);
    this.checkTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        long now = System.currentTimeMillis();
        for (Map.Entry<ManagedInterpreterGroup, Long> entry : interpreterGroups.entrySet()) {
          ManagedInterpreterGroup interpreterGroup = entry.getKey();
          Long lastTimeUsing = entry.getValue();
          if ((now - lastTimeUsing) > timeoutThreshold )  {
            LOGGER.info("InterpreterGroup {} is timeout.", interpreterGroup.getId());
            interpreterGroup.close();
            interpreterGroups.remove(entry.getKey());
          }
        }
      }
    }, checkInterval, checkInterval);
    LOGGER.info("TimeoutLifecycleManager is started with checkinterval: " + checkInterval
        + ", timeoutThreshold: " + timeoutThreshold);
  }

  @Override
  public void onInterpreterGroupCreated(ManagedInterpreterGroup interpreterGroup) {
    interpreterGroups.put(interpreterGroup, System.currentTimeMillis());
  }

  @Override
  public void onInterpreterSessionCreated(ManagedInterpreterGroup interpreterGroup,
                                          String sessionId) {

  }

  @Override
  public void onInterpreterUse(ManagedInterpreterGroup interpreterGroup, String sessionId) {
    interpreterGroups.put(interpreterGroup, System.currentTimeMillis());
  }
}
