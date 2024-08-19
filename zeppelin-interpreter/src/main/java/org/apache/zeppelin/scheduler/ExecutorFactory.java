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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.util.ExecutorUtil;

/**
 * Factory class for Executor
 */
public class ExecutorFactory {

  private Map<String, ExecutorService> executors = new HashMap<>();
  private Map<String, ScheduledExecutorService> scheduledExecutors = new HashMap<>();

  private ExecutorFactory() {

  }

  //Using the Initialization-on-demand holder idiom (https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom)
  private static final class InstanceHolder {
    private static final ExecutorFactory INSTANCE = new ExecutorFactory();
  }

  public static ExecutorFactory singleton() {
    return InstanceHolder.INSTANCE;
  }

  public ExecutorService createOrGet(final String name, final int numThread) {
    synchronized (executors) {
      return executors.computeIfAbsent(name, k -> (Executors.newScheduledThreadPool(
          numThread,
          new NamedThreadFactory(k))));
    }
  }

  public ScheduledExecutorService createOrGetScheduled(final String name, final int numThread) {
    synchronized (scheduledExecutors) {
      return scheduledExecutors.computeIfAbsent(name, k -> (Executors.newScheduledThreadPool(
          numThread,
          new NamedThreadFactory(k))));
    }
  }

  /**
   * ThreadPool created for running note via rest api.
   * TODO(zjffdu) Should use property to configure the thread pool size.
   * @return
   */
  public ExecutorService getNoteJobExecutor() {
    return createOrGet("NoteJobThread", 50);
  }

  public void shutdown(String name) {
    synchronized (executors) {
      ExecutorService e = executors.remove(name);
      if (e != null) {
        ExecutorUtil.softShutdown(name, e, 1, TimeUnit.MINUTES);
      }
    }
    synchronized (scheduledExecutors) {
      ExecutorService e = scheduledExecutors.remove(name);
      if (e != null) {
        ExecutorUtil.softShutdown(name, e, 1, TimeUnit.MINUTES);
      }
    }
  }

  public void shutdownAll() {
    synchronized (executors) {
      for (Entry<String, ExecutorService> executor : executors.entrySet()) {
        ExecutorUtil.softShutdown(executor.getKey(), executor.getValue(), 1, TimeUnit.MINUTES);
      }
      executors.clear();
    }
    synchronized (scheduledExecutors) {
      for (Entry<String, ScheduledExecutorService> scheduledExecutor : scheduledExecutors.entrySet()) {
        ExecutorUtil.softShutdown(scheduledExecutor.getKey(), scheduledExecutor.getValue(), 1, TimeUnit.MINUTES);
      }
      scheduledExecutors.clear();
    }
  }
}
