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
 * Polls job progress with given interval
 *
 * @see Job#progress()
 * @see JobListener#onProgressUpdate(org.apache.zeppelin.scheduler.Job, int)
 *
 * TODO(moon) : add description.
 */
public class JobProgressPoller extends Thread {
  public static final long DEFAULT_INTERVAL_MSEC = 500;
  private static final Logger logger = LoggerFactory.getLogger(JobProgressPoller.class);

  private Job job;
  private long intervalMs;

  public JobProgressPoller(Job job, long intervalMs) {
    super("JobProgressPoller, jobId=" + job.getId());
    this.job = job;
    if (intervalMs < 0) {
      throw new IllegalArgumentException("polling interval can't be " + intervalMs);
    }
    this.intervalMs = intervalMs == 0 ? DEFAULT_INTERVAL_MSEC : intervalMs;
  }

  @Override
  public void run() {
    try {
      while (!Thread.interrupted()) {
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
        Thread.sleep(intervalMs);
      }
    } catch (InterruptedException ignored) {}
  }
}
