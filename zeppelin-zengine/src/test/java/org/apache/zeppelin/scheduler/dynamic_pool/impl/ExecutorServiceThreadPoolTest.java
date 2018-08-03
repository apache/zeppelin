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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.zeppelin.scheduler.dynamic_pool.DynamicThreadPool;
import org.apache.zeppelin.scheduler.dynamic_pool.DynamicThreadPoolRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

@RunWith(JUnit4.class)
public class ExecutorServiceThreadPoolTest {

  private static final String QUARTZ_SCHEDULER = "QuartzScheduler";
  private static final int POOL_SIZE = 5;
  private static final int DECREASE_POOL_SIZE = 2;

  private Scheduler scheduler;
  private AtomicInteger jobSequence = new AtomicInteger(1);

  /**
   * Setup {@link ExecutorServiceThreadPool}.
   *
   * @throws SchedulerException
   */
  @Before
  public void beforeTests() throws SchedulerException {
    Properties properties = new Properties();
    properties.setProperty(
        StdSchedulerFactory.PROP_THREAD_POOL_CLASS, ExecutorServiceThreadPool.class.getName());
    properties.setProperty("org.quartz.threadPool.threadCount", String.valueOf(POOL_SIZE));

    scheduler = new StdSchedulerFactory(properties).getScheduler();
    scheduler.start();
  }

  /**
   * Close and wait for finish.
   *
   * @throws SchedulerException
   */
  @After
  public void afterTests() throws SchedulerException {
    scheduler.shutdown(true);
  }


  @Test
  public void changeThreadPoolSize() throws SchedulerException {
    DynamicThreadPool threadPool =
        DynamicThreadPoolRepository.getInstance().lookup(QUARTZ_SCHEDULER);
    threadPool.doResize(DECREASE_POOL_SIZE);

    Assert.assertEquals(DECREASE_POOL_SIZE, scheduler.getMetaData().getThreadPoolSize());
    threadPool.doResize(POOL_SIZE);
    Assert.assertEquals(POOL_SIZE, scheduler.getMetaData().getThreadPoolSize());
  }
}
