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
package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Creates a thread pool that can schedule zeppelinhub commands.
 *
 */
public class SchedulerService {

  private final ScheduledExecutorService pool;
  private static SchedulerService instance = null;

  private SchedulerService(int numberOfThread) {
    pool = Executors.newScheduledThreadPool(numberOfThread);
  }

  public static SchedulerService create(int numberOfThread) {
    if (instance == null) {
      instance = new SchedulerService(numberOfThread);
    }
    return instance;
  }

  public static SchedulerService getInstance() {
    if (instance == null) {
      instance = new SchedulerService(2);
    }
    return instance;
  }

  public void add(Runnable service, int firstExecution, int period) {
    pool.scheduleAtFixedRate(service, firstExecution, period, TimeUnit.SECONDS);
  }

  public void addOnce(Runnable service, int firstExecution) {
    pool.schedule(service, firstExecution, TimeUnit.SECONDS);
  }

  public void close() {
    pool.shutdown();
  }

}
