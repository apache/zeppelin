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
package org.apache.zeppelin.scheduler.dynamic_pool.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.zeppelin.scheduler.dynamic_pool.DynamicThreadPool;
import org.apache.zeppelin.scheduler.dynamic_pool.DynamicThreadPoolRepository;
import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;

public class ExecutorServiceThreadPool implements ThreadPool, DynamicThreadPool {
  private final ExecutorService executor = Executors.newFixedThreadPool(1);
  private int threadCount;
  private String instanceId;
  private String instanceName;

  @Override
  public boolean runInThread(Runnable runnable) {
    if (runnable == null) {
      return false;
    }
    executor.submit(runnable);
    return true;
  }

  @Override
  public int blockForAvailableThreads() {
    ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
    return threadPoolExecutor.getCorePoolSize() - threadPoolExecutor.getActiveCount();
  }

  @Override
  public void initialize() throws SchedulerConfigException {
    if (threadCount <= 0) {
      throw new SchedulerConfigException("Thread count must be > 0");
    }
  }

  public int getThreadCount() {
    return threadCount;
  }

  public void setThreadCount(int threadCount) {
    doResize(threadCount);
  }

  @Override
  public void shutdown(boolean b) {
    if (b) {
      executor.shutdown();
    } else {
      executor.shutdownNow();
    }
  }

  @Override
  public int getPoolSize() {
    return getThreadCount();
  }

  @Override
  public void setInstanceId(String s) {
    this.instanceId = s;
  }

  @Override
  public void setInstanceName(String s) {
    this.instanceName = s;
    DynamicThreadPoolRepository.getInstance().bind(this.instanceName, this);
  }

  @Override
  public void doResize(Integer threadPoolSize) {
    this.threadCount = threadPoolSize;
    ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
    threadPoolExecutor.setCorePoolSize(threadCount);
  }
}
