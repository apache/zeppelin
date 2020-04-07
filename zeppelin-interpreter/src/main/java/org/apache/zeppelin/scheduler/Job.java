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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


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
public abstract class Job<T> {
  private static Logger LOGGER = LoggerFactory.getLogger(Job.class);
  private static SimpleDateFormat JOB_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");

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
  private String id;

  private Date dateCreated;
  private Date dateStarted;
  private Date dateFinished;
  protected volatile Status status;

  transient boolean aborted = false;
  private volatile String errorMessage;
  private transient volatile Throwable exception;
  private transient JobListener listener;

  public Job(String jobName, JobListener listener) {
    this.jobName = jobName;
    this.listener = listener;
    dateCreated = new Date();
    id = JOB_DATE_FORMAT.format(dateCreated) + "_" + jobName;
    setStatus(Status.READY);
  }

  public Job(String jobId, String jobName, JobListener listener) {
    this.jobName = jobName;
    this.listener = listener;
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
    return ((Job) o).id.equals(id);
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
    this.status = status;
    if (listener != null && before != null && before != after) {
      listener.onStatusChange(this, before, after);
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

  public void onJobStarted() {
    dateStarted = new Date();
  }

  public void onJobEnded() {
    dateFinished = new Date();
  }

  public void run() {
    try {
      onJobStarted();
      completeWithSuccess(jobRun());
    } catch (Throwable e) {
      LOGGER.error("Job failed", e);
      completeWithError(e);
    } finally {
      onJobEnded();
    }
  }

  private void completeWithSuccess(T result) {
    setResult(result);
    exception = null;
    errorMessage = null;
  }

  private void completeWithError(Throwable error) {
    setException(error);
    errorMessage = getJobExceptionStack(error);
  }

  private String getJobExceptionStack(Throwable e) {
    if (e == null) {
      return "";
    }
    Throwable cause = ExceptionUtils.getRootCause(e);
    if (cause != null) {
      return ExceptionUtils.getStackTrace(cause);
    } else {
      return ExceptionUtils.getStackTrace(e);
    }
  }

  public Throwable getException() {
    return exception;
  }

  protected void setException(Throwable t) {
    exception = t;
  }

  public abstract T getReturn();

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }

  public abstract int progress();

  public abstract Map<String, Object> info();

  protected abstract T jobRun() throws Throwable;

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

  public void setDateStarted(Date startedAt) {
    dateStarted = startedAt;
  }

  public Date getDateFinished() {
    return dateFinished;
  }

  public void setDateFinished(Date finishedAt) {
    dateFinished = finishedAt;
  }

  public abstract void setResult(T result);

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
