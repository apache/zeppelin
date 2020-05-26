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

import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * RemoteScheduler runs in ZeppelinServer and proxies Scheduler running on RemoteInterpreter.
 * It is some kind of FIFOScheduler, but only run the next job after the current job is submitted
 * to remote.
 */
public class RemoteScheduler extends AbstractScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteScheduler.class);

  private RemoteInterpreter remoteInterpreter;
  private ExecutorService executor;

  public RemoteScheduler(String name,
                         ExecutorService executor,
                         RemoteInterpreter remoteInterpreter) {
    super(name);
    this.executor = executor;
    this.remoteInterpreter = remoteInterpreter;
  }

  @Override
  public void runJobInScheduler(Job job) {
    JobRunner jobRunner = new JobRunner(this, job);
    executor.execute(jobRunner);
    String executionMode =
            remoteInterpreter.getProperty(".execution.mode", "paragraph");
    if (executionMode.equals("paragraph")) {
      // wait until it is submitted to the remote
      while (!jobRunner.isJobSubmittedInRemote()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          LOGGER.error("Exception in RemoteScheduler while jobRunner.isJobSubmittedInRemote " +
                  "queue.wait", e);
        }
      }
    } else if (executionMode.equals("note")){
      // wait until it is finished
      while (!jobRunner.isJobExecuted()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          LOGGER.error("Exception in RemoteScheduler while jobRunner.isJobExecuted " +
                  "queue.wait", e);
        }
      }
    } else {
      throw new RuntimeException("Invalid job execution.mode: " + executionMode +
              ", only 'note' and 'paragraph' are valid");
    }
  }

  /**
   * Role of the class is getting status info from remote process from PENDING to
   * RUNNING status. This thread will exist after job is in RUNNING/FINISHED state.
   */
  private class JobStatusPoller extends Thread {
    private long checkIntervalMsec;
    private volatile boolean terminate;
    private JobListener listener;
    private Job job;
    private volatile Status lastStatus;

    public JobStatusPoller(Job job,
                           JobListener listener,
                           long checkIntervalMsec) {
      setName("JobStatusPoller-" + job.getId());
      this.checkIntervalMsec = checkIntervalMsec;
      this.job = job;
      this.listener = listener;
      this.terminate = false;
    }

    @Override
    public void run() {
      while (terminate == false) {
        synchronized (this) {
          try {
            this.wait(checkIntervalMsec);
          } catch (InterruptedException e) {
            LOGGER.error("Exception in RemoteScheduler while run this.wait", e);
          }
        }

        if (terminate) {
          // terminated by shutdown
          break;
        }

        Status newStatus = getStatus();
        if (newStatus == Status.RUNNING ||
                newStatus == Status.FINISHED ||
                newStatus == Status.ERROR ||
                newStatus == Status.ABORT) {
          // Exit this thread when job is in RUNNING/FINISHED/ERROR/ABORT state.
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

    public Status getStatus() {
      if (!remoteInterpreter.isOpened()) {
        if (lastStatus != null) {
          return lastStatus;
        } else {
          return job.getStatus();
        }
      }
      Status status = Status.valueOf(remoteInterpreter.getStatus(job.getId()));
      if (status == Status.UNKNOWN) {
        // not found this job in the remote schedulers.
        // maybe not submitted, maybe already finished
        return job.getStatus();
      }
      listener.onStatusChange(job, lastStatus, status);
      lastStatus = status;
      return status;
    }
  }

  private class JobRunner implements Runnable, JobListener {
    private final Logger logger = LoggerFactory.getLogger(JobRunner.class);
    private RemoteScheduler scheduler;
    private Job job;
    private volatile boolean jobExecuted;
    private volatile boolean jobSubmittedRemotely;

    public JobRunner(RemoteScheduler scheduler, Job job) {
      this.scheduler = scheduler;
      this.job = job;
      jobExecuted = false;
      jobSubmittedRemotely = false;
    }

    public boolean isJobSubmittedInRemote() {
      return jobSubmittedRemotely;
    }

    public boolean isJobExecuted() {
      return jobExecuted;
    }

    @Override
    public void run() {
      JobStatusPoller jobStatusPoller = new JobStatusPoller(job, this, 100);
      jobStatusPoller.start();
      scheduler.runJob(job);
      jobExecuted = true;
      jobSubmittedRemotely = true;
      jobStatusPoller.shutdown();
      try {
        jobStatusPoller.join();
      } catch (InterruptedException e) {
        logger.error("JobStatusPoller interrupted", e);
      }
    }

    @Override
    public void onProgressUpdate(Job job, int progress) {
    }

    // Call by JobStatusPoller thread, update status when JobStatusPoller get new status.
    @Override
    public void onStatusChange(Job job, Status before, Status after) {
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

      // only set status when the status fetched from JobStatusPoller is RUNNING,
      // the status of job itself is still in PENDING.
      // Because the status from JobStatusPoller may happen after the job is finished.
      synchronized (job) {
        if (after == Status.RUNNING && job.getStatus() == Status.PENDING) {
          job.setStatus(Status.RUNNING);
        }
      }
    }
  }
}
