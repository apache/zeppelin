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

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Each user use one FIFOScheduler
 */
public class FIFOPerUserScheduler implements Scheduler {

  private static Logger LOGGER = LoggerFactory.getLogger(FIFOScheduler.class);

  private ExecutorService executor;
  private SchedulerListener listener;
  boolean terminate = false;
  private String name;
  private int maxUser = 10;

  private Map<String, FIFOScheduler> schedulerMap = new HashMap<>();

  public FIFOPerUserScheduler(String name, ExecutorService executor, SchedulerListener listener) {
    this.name = name;
    this.executor = executor;
    this.listener = listener;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Collection<Job> getJobsWaiting() {
    synchronized (schedulerMap) {
      List<Job> waitingJobs = new ArrayList();
      for (FIFOScheduler scheduler : schedulerMap.values()) {
        waitingJobs.addAll(scheduler.getJobsWaiting());
      }
      return waitingJobs;
    }
  }

  @Override
  public Collection<Job> getJobsRunning() {
//    return new ArrayList<>();
    synchronized (schedulerMap) {
      List<Job> runningJobs = new ArrayList();
      for (FIFOScheduler scheduler : schedulerMap.values()) {
        runningJobs.addAll(scheduler.getJobsRunning());
      }
      return runningJobs;
    }
  }

  @Override
  public void submit(Job job) {
    synchronized (schedulerMap) {
      FIFOScheduler scheduler = schedulerMap.get(job.getUser());
      if (scheduler == null) {
        scheduler = (FIFOScheduler) SchedulerFactory.singleton()
                .createOrGetFIFOScheduler(this.name + "_" + job.getUser());
        executor.execute(scheduler);
      }
      LOGGER.debug("Submitting job for owner:" + job.getUser());
      scheduler.submit(job);
      schedulerMap.put(job.getUser(), scheduler);
    }
  }

  @Override
  public Job removeFromWaitingQueue(String jobId) {
    synchronized (schedulerMap) {
      for (FIFOScheduler scheduler : schedulerMap.values()) {
        Job job = scheduler.removeFromWaitingQueue(jobId);
        if (job != null) {
          schedulerMap.notify();
          return job;
        }
      }
      return null;
    }
  }

  @Override
  public void stop() {
    synchronized (schedulerMap) {
      for (FIFOScheduler scheduler : schedulerMap.values()) {
        scheduler.stop();
      }
    }
  }

  @Override
  public void run() {
    // Do nothing, all the work is delegated to FIFOScheduler in schedulerMap
  }
}
