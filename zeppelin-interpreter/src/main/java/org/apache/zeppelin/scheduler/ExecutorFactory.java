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
 *
 */
public class ExecutorFactory {
  private static ExecutorFactory _executor;
  private static Long _executorLock = new Long(0);

  Map<String, ExecutorService> executor = new HashMap<>();

  public ExecutorFactory() {

  }

  public static ExecutorFactory singleton() {
    if (_executor == null) {
      synchronized (_executorLock) {
        if (_executor == null) {
          _executor = new ExecutorFactory();
        }
      }
    }
    return _executor;
  }

  public ExecutorService getDefaultExecutor() {
    return createOrGet("default");
  }

  public ExecutorService createOrGet(String name) {
    return createOrGet(name, 100);
  }

  public ExecutorService createOrGet(String name, int numThread) {
    synchronized (executor) {
      if (!executor.containsKey(name)) {
        executor.put(name, Executors.newScheduledThreadPool(numThread));
      }
      return executor.get(name);
    }
  }

  public void shutdown(String name) {
    synchronized (executor) {
      if (executor.containsKey(name)) {
        ExecutorService e = executor.get(name);
        e.shutdown();
        executor.remove(name);
      }
    }
  }


  public void shutdownAll() {
    synchronized (executor) {
      for (String name : executor.keySet()){
        shutdown(name);
      }
    }
  }
}
