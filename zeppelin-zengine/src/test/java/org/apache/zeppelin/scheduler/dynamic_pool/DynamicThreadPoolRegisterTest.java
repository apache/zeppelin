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
package org.apache.zeppelin.scheduler.dynamic_pool;

import org.apache.zeppelin.scheduler.dynamic_pool.impl.ExecutorServiceThreadPool;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DynamicThreadPoolRegisterTest {
  private static final String SCHED_NAME = "SchedTest";

  @Test
  public void testBind() {
    bind();
  }

  private void bind() {
    DynamicThreadPoolRepository.getInstance().bind(SCHED_NAME, new ExecutorServiceThreadPool());
  }

  @Test
  public void testLookupThreadPool() {
    bind();

    DynamicThreadPool threadPool = DynamicThreadPoolRepository.getInstance().lookup(SCHED_NAME);
    Assert.assertNotNull(threadPool);
  }

  @Test
  public void testLookupWrongThreadPool() {
    DynamicThreadPool threadPool =
        DynamicThreadPoolRepository.getInstance().lookup("WrongSchedTest");
    Assert.assertNull(threadPool);
  }
}
