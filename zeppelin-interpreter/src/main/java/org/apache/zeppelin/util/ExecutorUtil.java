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

package org.apache.zeppelin.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutorUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorUtil.class);

  private ExecutorUtil() {
    // Util class
  }

  // This softshutdown is based on https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
  public static void softShutdown(String name, ExecutorService executor, int stopTimeoutVal, TimeUnit stopTimeoutUnit) {
    executor.shutdown(); // Disable new tasks from being submitted
    try {
      // Wait a while for existing tasks to terminate
      if (!executor.awaitTermination(stopTimeoutVal, stopTimeoutUnit)) {
        executor.shutdownNow(); // Cancel currently executing tasks
        // Wait a while for tasks to respond to being cancelled
        if (!executor.awaitTermination(stopTimeoutVal, stopTimeoutUnit)) {
          LOGGER.error("executor {} did not terminate", name);
        }
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      executor.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }
}
