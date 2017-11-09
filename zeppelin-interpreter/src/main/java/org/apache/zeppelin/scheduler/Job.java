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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Skeletal implementation of the Job concept.
 * - designed for inheritance
 * - should be run on a separate thread
 * - maintains internal state: it's status
 * - supports listeners who are updated on status change
 *
 * Job class is serialized/deserialized and used server<->client communication
 * and saving/loading jobs from disk.
 * Changing/adding/deleting non transitive field name need consideration of that.
 */
public abstract class Job {
  /**
   * Job status.
   *
   * UNKNOWN - Job is not found in remote
   * READY - Job is not running, ready to run.
   * PENDING - Job is submitted to scheduler. but not running yet
   * RUNNING - Job is running.
   * FINISHED - Job finished run. with success
   * ERROR - Job finished run. with error
   * ABORT - Job finished by abort
   */
  public enum Status {
    UNKNOWN, READY, PENDING, RUNNING, FINISHED, ERROR, ABORT;

    public boolean isReady() {
      return this == READY;
    }

    public boolean isRunning() {
      return this == RUNNING;
    }

    public boolean isPending() {
      return this == PENDING;
    }

    public boolean isCompleted() {
      return this == FINISHED || this == ERROR || this == ABORT;
    }
  }

  private String jobName;
  String id;

  Date dateCreated;
  Date dateStarted;
  Date dateFinished;
  volatile Status status;

  static Logger LOGGER = LoggerFactory.getLogger(Job.class);

  transient boolean aborted = false;

  private volatile String errorMessage;
  private transient volatile Throwable exception;
  private transient JobListener listener;
  private long progressUpdateIntervalMs;

  public Job(String jobName, JobListener listener, long progressUpdateIntervalMs) {
    this.jobName = jobName;
    this.listener = listener;
    this.progressUpdateIntervalMs = progressUpdateIntervalMs;

    dateCreated = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
    id = dateFormat.format(dateCreated) + "_" + super.hashCode();

    setStatus(Status.READY);
  }

  public Job(String jobName, JobListener listener) {
    this(jobName, listener, JobProgressPoller.DEFAULT_INTERVAL_MSEC);
  }

  public Job(String jobId, String jobName, JobListener listener) {
    this(jobId, jobName, listener, JobProgressPoller.DEFAULT_INTERVAL_MSEC);
  }

  public Job(String jobId, String jobName, JobListener listener, long progressUpdateIntervalMs) {
    this.jobName = jobName;
    this.listener = listener;
    this.progressUpdateIntervalMs = progressUpdateIntervalMs;

    dateCreated = new Date();
    id = jobId;

    setStatus(Status.READY);
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return ((Job) o).hashCode() == hashCode();
  }

  public Status getStatus() {
    return status;
  }

  /**
   * just set status without notifying to listeners for spell.
   */
  public void setStatusWithoutNotification(Status status) {
    this.status = status;
  }

  public void setStatus(Status status) {
    if (this.status == status) {
      return;
    }
    Status before = this.status;
    Status after = status;
    if (listener != null) {
      listener.beforeStatusChange(this, before, after);
    }
    this.status = status;
    if (listener != null) {
      listener.afterStatusChange(this, before, after);
    }
  }

  public void setListener(JobListener listener) {
    this.listener = listener;
  }

  public JobListener getListener() {
    return listener;
  }

  public boolean isTerminated() {
    return !this.status.isReady() && !this.status.isRunning() && !this.status.isPending();
  }

  public boolean isRunning() {
    return this.status.isRunning();
  }

  public void run() {
    JobProgressPoller progressUpdator = null;
    dateStarted = new Date();
    try {
      progressUpdator = new JobProgressPoller(this, progressUpdateIntervalMs);
      progressUpdator.start();
      completeWithSuccess(jobRun());
    } catch (Throwable e) {
      LOGGER.error("Job failed", e);
      completeWithError(e);
    } finally {
      if (progressUpdator != null) {
        progressUpdator.interrupt();
      }
      //aborted = false;
    }
  }

  private synchronized void completeWithSuccess(Object result) {
    setResult(result);
    exception = null;
    errorMessage = null;
    dateFinished = new Date();
  }

  private synchronized void completeWithError(Throwable error) {
    setResult(error.getMessage());
    setException(error);
    dateFinished = new Date();
  }

  public static String getStack(Throwable e) {
    if (e == null) {
      return "";
    }

    Throwable cause = ExceptionUtils.getRootCause(e);
    if (cause != null) {
      return ExceptionUtils.getFullStackTrace(cause);
    } else {
      return ExceptionUtils.getFullStackTrace(e);
    }
  }

  public synchronized Throwable getException() {
    return exception;
  }

  protected synchronized void setException(Throwable t) {
    exception = t;
    errorMessage = getStack(t);
  }

  public abstract Object getReturn();

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }

  public abstract int progress();

  public abstract Map<String, Object> info();

  protected abstract Object jobRun() throws Throwable;

  protected abstract boolean jobAbort();

  public void abort() {
    aborted = jobAbort();
  }

  public boolean isAborted() {
    return aborted;
  }

  public Date getDateCreated() {
    return dateCreated;
  }

  public Date getDateStarted() {
    return dateStarted;
  }

  public synchronized void setDateStarted(Date startedAt) {
    dateStarted = startedAt;
  }

  public synchronized Date getDateFinished() {
    return dateFinished;
  }

  public synchronized void setDateFinished(Date finishedAt) {
    dateFinished = finishedAt;
  }

  public abstract void setResult(Object results);

  public synchronized String getErrorMessage() {
    return errorMessage;
  }

  public synchronized void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
