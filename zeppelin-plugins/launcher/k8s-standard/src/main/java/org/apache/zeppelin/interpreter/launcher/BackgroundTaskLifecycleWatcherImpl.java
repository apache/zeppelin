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
package org.apache.zeppelin.interpreter.launcher;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.apache.zeppelin.background.BackgroundTaskLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BackgroundTaskLifecycleWatcherImpl<T> implements Watcher<T> {
  Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskLifecycleWatcherImpl.class);

  private BackgroundTaskLifecycleListener listener;

  public BackgroundTaskLifecycleWatcherImpl(BackgroundTaskLifecycleListener listener) {
    this.listener = listener;
  }

  @Override
  public void eventReceived(Action action, T t) {
    switch (action) {
      case ADDED:
        if (listener != null) {
          listener.onBackgroundTaskStart(getTaskId(t));
        }
        break;
      case DELETED:
        if (listener != null) {
          listener.onBackgroundTaskStop(getTaskId(t));
        }
        break;
      case MODIFIED:
        if (listener != null) {
          listener.onBackgroundTaskUpdate(getTaskId(t));
        }
        break;
      case ERROR:
        if (listener != null) {
          listener.onBackgroundTaskError(getTaskId(t));
        }
        break;
    }
  }

  @Override
  public void onClose(KubernetesClientException e) {
    LOGGER.info("Kubernetes watcher closed");
  }

  protected abstract String getTaskId(T t);

  public BackgroundTaskLifecycleListener getListener() {
    return listener;
  }

  public void setListener(BackgroundTaskLifecycleListener listener) {
    this.listener = listener;
  }
}
