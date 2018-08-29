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


public abstract class JobWithProgressPoller<T> extends Job<T> {

  private transient JobProgressPoller progressPoller;
  private long progressUpdateIntervalMs;


  public JobWithProgressPoller(String jobId, String jobName, JobListener listener,
                               long progressUpdateIntervalMs) {
    super(jobId, jobName, listener);
    this.progressUpdateIntervalMs = progressUpdateIntervalMs;
  }

  public JobWithProgressPoller(String jobId, String jobName, JobListener listener) {
    this(jobId, jobName, listener, JobProgressPoller.DEFAULT_INTERVAL_MSEC);
  }

  public JobWithProgressPoller(String jobId, JobListener listener) {
    this(jobId, jobId, listener);
  }

  @Override
  public void onJobStarted() {
    super.onJobStarted();
    progressPoller = new JobProgressPoller(this, progressUpdateIntervalMs);
    progressPoller.start();
  }

  @Override
  public void onJobEnded() {
    super.onJobEnded();
    if (this.progressPoller != null) {
      this.progressPoller.interrupt();
    }
  }
}
