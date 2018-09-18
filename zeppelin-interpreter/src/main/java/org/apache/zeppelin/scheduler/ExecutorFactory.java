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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory class for Executor
 */
public class ExecutorFactory {
  private static ExecutorFactory instance;
  private static Long _executorLock = new Long(0);

  private Map<String, ExecutorService> executors = new HashMap<>();

  private ExecutorFactory() {

  }

  public static ExecutorFactory singleton() {
    if (instance == null) {
      synchronized (_executorLock) {
        if (instance == null) {
          instance = new ExecutorFactory();
        }
      }
    }
    return instance;
  }

  public ExecutorService createOrGet(String name, int numThread) {
    synchronized (executors) {
      if (!executors.containsKey(name)) {
        executors.put(name, Executors.newScheduledThreadPool(numThread,
            new SchedulerThreadFactory(name)));
      }
      return executors.get(name);
    }
  }

  public void shutdown(String name) {
    synchronized (executors) {
      if (executors.containsKey(name)) {
        ExecutorService e = executors.get(name);
        e.shutdown();
        executors.remove(name);
      }
    }
  }


  public void shutdownAll() {
    synchronized (executors) {
      for (String name : executors.keySet()) {
        shutdown(name);
      }
    }
  }
}
