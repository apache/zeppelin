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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating schedulers
 *
 */
public class SchedulerFactory implements SchedulerListener {
  private static final Logger logger = LoggerFactory.getLogger(SchedulerFactory.class);
  protected ExecutorService executor;
  protected Map<String, Scheduler> schedulers = new LinkedHashMap<>();

  private static SchedulerFactory singleton;
  private static Long singletonLock = new Long(0);

  public static SchedulerFactory singleton() {
    if (singleton == null) {
      synchronized (singletonLock) {
        if (singleton == null) {
          try {
            singleton = new SchedulerFactory();
          } catch (Exception e) {
            logger.error(e.toString(), e);
          }
        }
      }
    }
    return singleton;
  }

  SchedulerFactory() throws Exception {
    executor = ExecutorFactory.singleton().createOrGet("SchedulerFactory", 100);
  }

  public void destroy() {
    ExecutorFactory.singleton().shutdown("SchedulerFactory");
  }

  public Scheduler createOrGetFIFOScheduler(String name) {
    synchronized (schedulers) {
      if (!schedulers.containsKey(name)) {
        Scheduler s = new FIFOScheduler(name, executor, this);
        schedulers.put(name, s);
        executor.execute(s);
      }
      return schedulers.get(name);
    }
  }

  public Scheduler createOrGetParallelScheduler(String name, int maxConcurrency) {
    synchronized (schedulers) {
      if (!schedulers.containsKey(name)) {
        Scheduler s = new ParallelScheduler(name, executor, this, maxConcurrency);
        schedulers.put(name, s);
        executor.execute(s);
      }
      return schedulers.get(name);
    }
  }

  public Scheduler createOrGetScheduler(Scheduler scheduler) {
    synchronized (schedulers) {
      if (!schedulers.containsKey(scheduler.getName())) {
        schedulers.put(scheduler.getName(), scheduler);
        executor.execute(scheduler);
      }
      return schedulers.get(scheduler.getName());
    }
  }

  public void removeScheduler(String name) {
    synchronized (schedulers) {
      Scheduler s = schedulers.remove(name);
      if (s != null) {
        s.stop();
      }
    }
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  @Override
  public void jobStarted(Scheduler scheduler, Job job) {
    logger.info("Job " + job.getId() + " started by scheduler " + scheduler.getName());

  }

  @Override
  public void jobFinished(Scheduler scheduler, Job job) {
    logger.info("Job " + job.getId() + " finished by scheduler " + scheduler.getName());

  }
}
