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

package org.apache.zeppelin.interpreter.launcher;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class KubectlPollingWait implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(KubectlPollingWait.class);
  private final Kubectl kubectl;
  private final int timeoutSec;
  private final int intervalSec;
  private final String[] args;
  private final ScheduledFuture<?> t;
  private Exception exception;
  private boolean stopConditionMatched = false;
  private Map<String, Object> lastResult;

  @VisibleForTesting
  KubectlPollingWait(Kubectl kubectl, String [] args, int intervalSec, int timeoutSec) {
    this.kubectl = kubectl;
    this.args = args;
    this.timeoutSec = timeoutSec;
    this.intervalSec = intervalSec;
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    t = executor.schedule(this, 0, TimeUnit.SECONDS);
  }

  @Override
  public void run() {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutSec) {
      try {
        Map<String, Object> ret = kubectl.execAndGetJson(args);
        lastResult = ret;
        if (shouldStop(ret)) {
          stopConditionMatched = true;
          break;
        } else {
          Thread.sleep(intervalSec * 1000);
        }
      } catch (Exception e) {
        logger.error("Error", e);
        exception = e;
        break;
      }
    }

    synchronized (this) {
      this.notify();
    }
  }

  protected abstract boolean shouldStop(Map<String, Object> ret);

  public boolean isDone() {
    return t != null && t.isDone() && stopConditionMatched;
  }

  public boolean isRunning() {
    return !isDone();
  }

  public Exception getException() {
    return exception;
  }

  public Map<String, Object> getLastResult() {
    return lastResult;
  }

  public void await() {
    while (isRunning()) {
      synchronized (this) {
        try {
          this.wait(1000);
        } catch (InterruptedException e) {
          // ignore
        }
      }
    }
  }
}
