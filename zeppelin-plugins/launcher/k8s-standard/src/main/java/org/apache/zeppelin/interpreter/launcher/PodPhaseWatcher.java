/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;

public class PodPhaseWatcher implements Watcher<Pod> {
  private static final Logger LOGGER = LoggerFactory.getLogger(PodPhaseWatcher.class);
  private final CountDownLatch countDownLatch;
  private final Predicate<String> predicate;

  public PodPhaseWatcher(Predicate<String> predicate) {
    this.countDownLatch = new CountDownLatch(1);
    this.predicate = predicate;
  }

  @Override
  public void eventReceived(Action action, Pod pod) {
    PodStatus status = pod.getStatus();
    if (status != null && predicate.test(status.getPhase())) {
      LOGGER.info("Pod {} meets phase {}", pod.getMetadata().getName(), status.getPhase());
      countDownLatch.countDown();
    }
  }

  @Override
  public void onClose(WatcherException cause) {
    if (cause != null) {
      LOGGER.error("PodWatcher exits abnormally", cause);
    }
    // always count down, so threads that are waiting will continue
    countDownLatch.countDown();
  }

  @Override
  public void onClose() {
    // always count down, so threads that are waiting will continue
    countDownLatch.countDown();
  }

  public CountDownLatch getCountDownLatch() {
    return countDownLatch;
  }

}
