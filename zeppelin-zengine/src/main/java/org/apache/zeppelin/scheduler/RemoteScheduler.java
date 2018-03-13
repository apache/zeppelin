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

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * RemoteScheduler runs in ZeppelinServer and proxies Scheduler running on RemoteInterpreter
 *
 */
public class RemoteScheduler implements Scheduler {
  Logger logger = LoggerFactory.getLogger(RemoteScheduler.class);

  List<Job> queue = new LinkedList<>();
  List<Job> running = new LinkedList<>();
  private ExecutorService executor;
  private SchedulerListener listener;
  boolean terminate = false;
  private String name;
  private int maxConcurrency;
  private final String sessionId;
  private RemoteInterpreter remoteInterpreter;

  public RemoteScheduler(String name, ExecutorService executor, String sessionId,
                         RemoteInterpreter remoteInterpreter, SchedulerListener listener,
                         int maxConcurrency) {
    this.name = name;
    this.executor = executor;
    this.listener = listener;
    this.sessionId = sessionId;
    this.remoteInterpreter = remoteInterpreter;
    this.maxConcurrency = maxConcurrency;
  }

  @Override
  public void run() {
    while (terminate == false) {
      Job job = null;

      synchronized (queue) {
        if (running.size() >= maxConcurrency || queue.isEmpty() == true) {
          try {
            queue.wait(500);
          } catch (InterruptedException e) {
            logger.error("Exception in RemoteScheduler while run queue.wait", e);
          }
          continue;
        }

        job = queue.remove(0);
        running.add(job);
      }

      // run
      Scheduler scheduler = this;
      JobRunner jobRunner = new JobRunner(scheduler, job);
      executor.execute(jobRunner);

      // wait until it is submitted to the remote
      while (!jobRunner.isJobSubmittedInRemote()) {
        synchronized (queue) {
          try {
            queue.wait(500);
          } catch (InterruptedException e) {
            logger.error("Exception in RemoteScheduler while jobRunner.isJobSubmittedInRemote " +
                "queue.wait", e);
          }
        }
      }
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<Job> getJobsWaiting() {
    List<Job> ret = new LinkedList<>();
    synchronized (queue) {
      for (Job job : queue) {
        ret.add(job);
      }
    }
    return ret;
  }

  @Override
  public Job removeFromWaitingQueue(String jobId) {
    synchronized (queue) {
      Iterator<Job> it = queue.iterator();
      while (it.hasNext()) {
        Job job = it.next();
        if (job.getId().equals(jobId)) {
          it.remove();
          return job;
        }
      }
    }
    return null;
  }

  @Override
  public Collection<Job> getJobsRunning() {
    List<Job> ret = new LinkedList<>();
    synchronized (queue) {
      for (Job job : running) {
        ret.add(job);
      }
    }
    return ret;
  }

  @Override
  public void submit(Job job) {
    if (terminate) {
      throw new RuntimeException("Scheduler already terminated");
    }
    job.setStatus(Status.PENDING);

    synchronized (queue) {
      queue.add(job);
      queue.notify();
    }
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    synchronized (queue) {
      queue.notify();
    }
  }

  /**
   * Role of the class is get status info from remote process from PENDING to
   * RUNNING status.
   */
  private class JobStatusPoller extends Thread {
    private long initialPeriodMsec;
    private long initialPeriodCheckIntervalMsec;
    private long checkIntervalMsec;
    private volatile boolean terminate;
    private JobListener listener;
    private Job job;
    volatile Status lastStatus;

    public JobStatusPoller(long initialPeriodMsec,
        long initialPeriodCheckIntervalMsec, long checkIntervalMsec, Job job,
        JobListener listener) {
      setName("JobStatusPoller-" + job.getId());
      this.initialPeriodMsec = initialPeriodMsec;
      this.initialPeriodCheckIntervalMsec = initialPeriodCheckIntervalMsec;
      this.checkIntervalMsec = checkIntervalMsec;
      this.job = job;
      this.listener = listener;
      this.terminate = false;
    }

    @Override
    public void run() {
      long started = System.currentTimeMillis();
      while (terminate == false) {
        long current = System.currentTimeMillis();
        long interval;
        if (current - started < initialPeriodMsec) {
          interval = initialPeriodCheckIntervalMsec;
        } else {
          interval = checkIntervalMsec;
        }

        synchronized (this) {
          try {
            this.wait(interval);
          } catch (InterruptedException e) {
            logger.error("Exception in RemoteScheduler while run this.wait", e);
          }
        }

        if (terminate) {
          // terminated by shutdown
          break;
        }

        Status newStatus = getStatus();
        if (newStatus == Status.UNKNOWN) { // unknown
          continue;
        }

        if (newStatus != Status.READY && newStatus != Status.PENDING) {
          // we don't need more
          break;
        }
      }
      terminate = true;
    }

    public void shutdown() {
      terminate = true;
      synchronized (this) {
        this.notify();
      }
    }


    private Status getLastStatus() {
      if (terminate == true) {
        if (job.getErrorMessage() != null) {
          return Status.ERROR;
        } else if (lastStatus != Status.FINISHED &&
            lastStatus != Status.ERROR &&
            lastStatus != Status.ABORT) {
          return Status.FINISHED;
        } else {
          return (lastStatus == null) ? Status.FINISHED : lastStatus;
        }
      } else {
        return (lastStatus == null) ? Status.UNKNOWN : lastStatus;
      }
    }

    public synchronized Status getStatus() {
      if (!remoteInterpreter.isOpened()) {
        return getLastStatus();
      }
      Status status = Status.valueOf(remoteInterpreter.getStatus(job.getId()));
      if (status == Status.UNKNOWN) {
        // not found this job in the remote schedulers.
        // maybe not submitted, maybe already finished
        //Status status = getLastStatus();
        listener.afterStatusChange(job, null, null);
        return job.getStatus();
      }
      lastStatus = status;
      listener.afterStatusChange(job, null, status);
      return status;
    }
  }

  //TODO(zjffdu) need to refactor the schdule module which is too complicated
  private class JobRunner implements Runnable, JobListener {
    private final Logger logger = LoggerFactory.getLogger(JobRunner.class);
    private Scheduler scheduler;
    private Job job;
    private volatile boolean jobExecuted;
    volatile boolean jobSubmittedRemotely;

    public JobRunner(Scheduler scheduler, Job job) {
      this.scheduler = scheduler;
      this.job = job;
      jobExecuted = false;
      jobSubmittedRemotely = false;
    }

    public boolean isJobSubmittedInRemote() {
      return jobSubmittedRemotely;
    }

    @Override
    public void run() {
      if (job.isAborted()) {
        synchronized (queue) {
          job.setStatus(Status.ABORT);
          job.aborted = false;

          running.remove(job);
          queue.notify();
        }
        jobSubmittedRemotely = true;

        return;
      }

      JobStatusPoller jobStatusPoller = new JobStatusPoller(1500, 100, 500,
          job, this);
      jobStatusPoller.start();

      if (listener != null) {
        listener.jobStarted(scheduler, job);
      }
      job.run();

      jobExecuted = true;
      jobSubmittedRemotely = true;

      jobStatusPoller.shutdown();
      try {
        jobStatusPoller.join();
      } catch (InterruptedException e) {
        logger.error("JobStatusPoller interrupted", e);
      }

      // set job status based on result.
      Object jobResult = job.getReturn();
      if (job.isAborted()) {
        job.setStatus(Status.ABORT);
      } else if (job.getException() != null) {
        logger.debug("Job ABORT, " + job.getId() + ", " + job.getErrorMessage());
        job.setStatus(Status.ERROR);
      } else if (jobResult != null && jobResult instanceof InterpreterResult
          && ((InterpreterResult) jobResult).code() == Code.ERROR) {
        logger.debug("Job Error, " + job.getId() + ", " + job.getErrorMessage());
        job.setStatus(Status.ERROR);
      } else {
        logger.debug("Job Finished, " + job.getId() + ", Result: " + job.getReturn());
        job.setStatus(Status.FINISHED);
      }

      synchronized (queue) {
        if (listener != null) {
          listener.jobFinished(scheduler, job);
        }

        // reset aborted flag to allow retry
        job.aborted = false;

        running.remove(job);
        queue.notify();
      }
    }

    @Override
    public void onProgressUpdate(Job job, int progress) {
    }

    @Override
    public void beforeStatusChange(Job job, Status before, Status after) {
    }

    @Override
    public void afterStatusChange(Job job, Status before, Status after) {
      // Update remoteStatus
      if (jobExecuted == false) {
        if (after == Status.FINISHED || after == Status.ABORT
            || after == Status.ERROR) {
          // it can be status of last run.
          // so not updating the remoteStatus
          return;
        } else if (after == Status.RUNNING) {
          jobSubmittedRemotely = true;
          job.setStatus(Status.RUNNING);
        }
      } else {
        jobSubmittedRemotely = true;
      }

      // only set status when it is RUNNING
      // We would set other status based on the interpret result
      if (after == Status.RUNNING) {
        job.setStatus(Status.RUNNING);
      }
    }
  }

  @Override
  public void stop() {
    terminate = true;
    synchronized (queue) {
      queue.notify();
    }

  }

}
