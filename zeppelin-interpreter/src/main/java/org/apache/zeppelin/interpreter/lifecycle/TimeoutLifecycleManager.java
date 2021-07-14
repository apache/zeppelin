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

package org.apache.zeppelin.interpreter.lifecycle;

import org.apache.thrift.TException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.LifecycleManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.apache.zeppelin.scheduler.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;


/**
 * This lifecycle manager would close interpreter after it is timeout. By default, it is timeout
 * after no using in 1 hour.
 *
 * For now, this class only manage the lifecycle of interpreter group (will close interpreter
 * process after timeout). Managing the lifecycle of interpreter session could be done in future
 * if necessary.
 */
public class TimeoutLifecycleManager extends LifecycleManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutLifecycleManager.class);

  private long lastBusyTimeInMillis;

  public TimeoutLifecycleManager(ZeppelinConfiguration zConf,
                                 RemoteInterpreterServer remoteInterpreterServer) {
    super(zConf, remoteInterpreterServer);
    long checkInterval = zConf.getLong(ZeppelinConfiguration.ConfVars
            .ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL);
    long timeoutThreshold = zConf.getLong(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD);
    ScheduledExecutorService checkScheduler = ExecutorFactory.singleton()
        .createOrGetScheduled("TimeoutLifecycleManager", 1);
    checkScheduler.scheduleAtFixedRate(() -> {
      // check both lastBusyTimeInMillis & noJobsRunnings
      // In one corner case, lastBusyTimeInMilli won't be updated, that is when zeppelin server is dead
      // then RemoteInterpreterServer#getProgress and RemoteInterpreterServer#getStatus won't be called.
      if ((System.currentTimeMillis() - lastBusyTimeInMillis) > timeoutThreshold &&
              noJobsRunning()) {
        LOGGER.info("Interpreter process idle time exceed threshold, try to stop it");
        try {
          remoteInterpreterServer.shutdown();
        } catch (TException e) {
          LOGGER.error("Fail to shutdown RemoteInterpreterServer", e);
        }
      } else {
        LOGGER.debug("Check idle time of interpreter");
      }
    }, checkInterval, checkInterval, MILLISECONDS);
    LOGGER.info("TimeoutLifecycleManager is started with checkInterval: {}, timeoutThreshold: ¸{}", checkInterval,
        timeoutThreshold);
  }

  private boolean noJobsRunning() {
    InterpreterGroup interpreterGroup = this.remoteInterpreterServer.getInterpreterGroup();
    if (interpreterGroup == null) {
      return true;
    } else {
      for (List<Interpreter> session : interpreterGroup.values()) {
        for (Interpreter intp : session) {
          for (Job job : intp.getScheduler().getAllJobs()) {
            if (job.isRunning()) {
              return false;
            }
          }
        }
      }
      return true;
    }
  }

  @Override
  public void onInterpreterProcessStarted(String interpreterGroupId) {
    LOGGER.info("Interpreter process: {} is started", interpreterGroupId);
    lastBusyTimeInMillis = System.currentTimeMillis();
  }

  @Override
  public void onInterpreterUse(String interpreterGroupId) {
    LOGGER.debug("Interpreter process: {} is used", interpreterGroupId);
    lastBusyTimeInMillis = System.currentTimeMillis();
  }
}
