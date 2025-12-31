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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.util.ExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Factory class for creating schedulers except RemoteScheduler as RemoteScheduler runs in
 * zeppelin server process instead of interpreter process.
 *
 */
public class SchedulerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerFactory.class);
  private static final String SCHEDULER_EXECUTOR_NAME = "SchedulerFactory";

  protected ExecutorService executor;
  protected Map<String, Scheduler> schedulers = new HashMap<>();

  // Using the Initialization-on-demand holder idiom (https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom)
  private static final class InstanceHolder {
    private static final SchedulerFactory INSTANCE = new SchedulerFactory();
  }

  public static SchedulerFactory singleton() {
    return InstanceHolder.INSTANCE;
  }

  private SchedulerFactory() {
    int threadPoolSize = ZeppelinConfiguration
        .getStaticInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_SCHEDULER_POOL_SIZE);
    LOGGER.info("Scheduler Thread Pool Size: {}", threadPoolSize);
    executor = ExecutorFactory.singleton().createOrGet(SCHEDULER_EXECUTOR_NAME, threadPoolSize);
  }

  public void destroy() {
    LOGGER.info("Destroy all executors");
    synchronized (schedulers) {
      // stop all child thread of schedulers
      for (Entry<String, Scheduler> scheduler : schedulers.entrySet()) {
        LOGGER.info("Stopping Scheduler {}", scheduler.getKey());
        scheduler.getValue().stop();
      }
      schedulers.clear();
    }
    ExecutorUtil.softShutdown("SchedulerFactoryExecutor", executor, 60, TimeUnit.SECONDS);
  }

  public Scheduler createOrGetFIFOScheduler(final String name) {
    synchronized (schedulers) {
      return schedulers.computeIfAbsent(name, k -> {
        LOGGER.info("Create FIFOScheduler: {}", k);
        FIFOScheduler s = new FIFOScheduler(k);
        executor.execute(s);
        return s;
      });
    }
  }

  public Scheduler createOrGetParallelScheduler(final String name, final int maxConcurrency) {
    synchronized (schedulers) {
      return schedulers.computeIfAbsent(name, k -> {
        LOGGER.info("Create ParallelScheduler: {} with maxConcurrency: {}", k, maxConcurrency);
        ParallelScheduler s = new ParallelScheduler(k, maxConcurrency);
        executor.execute(s);
        return s;
      });
    }
  }


  public Scheduler createOrGetScheduler(final Scheduler scheduler) {
    synchronized (schedulers) {
      return schedulers.computeIfAbsent(scheduler.getName(), k -> {
        executor.execute(scheduler);
        return scheduler;
      });
    }
  }

  public void removeScheduler(String name) {
    synchronized (schedulers) {
      LOGGER.info("Remove scheduler: {}", name);
      Scheduler s = schedulers.remove(name);
      if (s != null) {
        s.stop();
      }
    }
  }

  public ExecutorService getExecutor() {
    return executor;
  }
}
