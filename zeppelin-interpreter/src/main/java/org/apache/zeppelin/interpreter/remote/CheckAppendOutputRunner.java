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

package org.apache.zeppelin.interpreter.remote;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for initializing
 * and ensuring that AppendOutputRunner is up
 * and running.
 */
public class CheckAppendOutputRunner {

  private static final Logger logger =
      LoggerFactory.getLogger(CheckAppendOutputRunner.class);
  private static final Boolean SYNCHRONIZER = false;
  private static ScheduledExecutorService SCHEDULED_SERVICE = null;
  private static ScheduledFuture<?> futureObject = null;
  private static AppendOutputRunner runner = null;

  public static void startScheduler() {
    synchronized (SYNCHRONIZER) {
      if (SCHEDULED_SERVICE == null) {
        runner = new AppendOutputRunner();
        logger.info("Starting a AppendOutputRunner thread to buffer"
            + " and send paragraph append data.");
        SCHEDULED_SERVICE = Executors.newSingleThreadScheduledExecutor();
        futureObject = SCHEDULED_SERVICE.scheduleWithFixedDelay(
            runner, 0, AppendOutputRunner.BUFFER_TIME_MS, TimeUnit.MILLISECONDS);
      }
    }
  }

  /* This function is only used by unit-tests. */
  public static void stopSchedulerForUnitTests() {
    synchronized (SYNCHRONIZER) {
      if (futureObject != null) {
        futureObject.cancel(true);
      }
      SCHEDULED_SERVICE = null;
    }
  }
}
