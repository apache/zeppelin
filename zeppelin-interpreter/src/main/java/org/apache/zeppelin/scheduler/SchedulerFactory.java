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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO(moon) : add description.
 */
public class SchedulerFactory implements SchedulerListener {
  private static final Logger logger = LoggerFactory.getLogger(SchedulerFactory.class);
  ExecutorService executor;
  Map<String, Scheduler> schedulers = new LinkedHashMap<>();

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

  public SchedulerFactory() throws Exception {
    executor = ExecutorFactory.singleton().createOrGet("schedulerFactory", 100);
  }

  public void destroy() {
    ExecutorFactory.singleton().shutdown("schedulerFactory");
  }

  public Scheduler createOrGetFIFOScheduler(String name) {
    synchronized (schedulers) {
      if (schedulers.containsKey(name) == false) {
        Scheduler s = new FIFOScheduler(name, executor, this);
        schedulers.put(name, s);
        executor.execute(s);
      }
      return schedulers.get(name);
    }
  }

  public Scheduler createOrGetParallelScheduler(String name, int maxConcurrency) {
    synchronized (schedulers) {
      if (schedulers.containsKey(name) == false) {
        Scheduler s = new ParallelScheduler(name, executor, this, maxConcurrency);
        schedulers.put(name, s);
        executor.execute(s);
      }
      return schedulers.get(name);
    }
  }

  public Scheduler createOrGetRemoteScheduler(
      String name,
      String noteId,
      RemoteInterpreterProcess interpreterProcess,
      int maxConcurrency) {

    synchronized (schedulers) {
      if (schedulers.containsKey(name) == false) {
        Scheduler s = new RemoteScheduler(
            name,
            executor,
            noteId,
            interpreterProcess,
            this,
            maxConcurrency);
        schedulers.put(name, s);
        executor.execute(s);
      }
      return schedulers.get(name);
    }
  }

  public Scheduler removeScheduler(String name) {
    synchronized (schedulers) {
      Scheduler s = schedulers.remove(name);
      if (s != null) {
        s.stop();
      }
    }
    return null;
  }

  public Collection<Scheduler> listScheduler(String name) {
    List<Scheduler> s = new LinkedList<>();
    synchronized (schedulers) {
      for (Scheduler ss : schedulers.values()) {
        s.add(ss);
      }
    }
    return s;
  }

  @Override
  public void jobStarted(Scheduler scheduler, Job job) {
    logger.info("Job " + job.getJobName() + " started by scheduler " + scheduler.getName());

  }

  @Override
  public void jobFinished(Scheduler scheduler, Job job) {
    logger.info("Job " + job.getJobName() + " finished by scheduler " + scheduler.getName());

  }



}
