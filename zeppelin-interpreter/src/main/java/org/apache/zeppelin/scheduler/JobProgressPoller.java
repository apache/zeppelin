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

package org.apache.zeppelin.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO(moon) : add description.
 */
public class JobProgressPoller extends Thread {
  public static final long DEFAULT_INTERVAL_MSEC = 500;
  Logger logger = LoggerFactory.getLogger(JobProgressPoller.class);
  private Job job;
  private long intervalMs;
  boolean terminate = false;

  public JobProgressPoller(Job job, long intervalMs) {
    this.job = job;
    this.intervalMs = intervalMs;
  }

  @Override
  public void run() {
    if (intervalMs < 0) {
      return;
    } else if (intervalMs == 0) {
      intervalMs = DEFAULT_INTERVAL_MSEC;
    }

    while (terminate == false) {
      JobListener listener = job.getListener();
      if (listener != null) {
        try {
          if (job.isRunning()) {
            listener.onProgressUpdate(job, job.progress());
          }
        } catch (Exception e) {
          logger.error("Can not get or update progress", e);
        }
      }
      try {
        Thread.sleep(intervalMs);
      } catch (InterruptedException e) {
        logger.error("Exception in JobProgressPoller while run Thread.sleep", e);
      }
    }
  }

  public void terminate() {
    terminate = true;
  }
}
