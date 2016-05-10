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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * todo: add description.
 * 
 * @author anthonyc
 *
 */
abstract class Retryable {
  private static final Logger LOG = LoggerFactory.getLogger(Retryable.class);
  protected abstract void attempt() throws Exception;

  /*
   * @delay: initial delay (ms)
   * 
   * @interval: interval between re-trials (ms)
   * 
   * @timeout: timeout after which re-trials are aborted (ms)
   */
  public void execute(long delay, long interval, long timeout) throws Exception {
    long start = System.currentTimeMillis();
    if (delay > 0L) {
      sleep(delay);
    }
    while (true) {
      try {
        attempt();
        return;
      } catch (Exception e) {
        if (System.currentTimeMillis() - start < timeout) {
          LOG.info("Retrying in " + interval + " ms");
          sleep(interval);
          // continue
        } else {
          throw e;
        }
      }
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interruptedException) {
      // continue
    }
  }

}
