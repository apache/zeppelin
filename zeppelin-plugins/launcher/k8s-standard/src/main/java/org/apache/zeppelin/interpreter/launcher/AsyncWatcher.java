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

import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncWatcher<T> implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncWatcher.class);
  private final WatchStopCondition<T> stopCondition;
  private final int timeoutSec;
  private final WatchCall call;
  private final Thread t;
  boolean stopConditionMatched = false;
  private long startTime;
  private Exception e;

  public AsyncWatcher(WatchCall call, WatchStopCondition<T> stopCondition, int timeoutSec) throws ApiException {
    this.call = call;
    this.stopCondition = stopCondition;
    this.timeoutSec = timeoutSec;
    this.t = new Thread(this);
    t.start();
  }

  public void run() {
    startTime = System.currentTimeMillis();
    String resourceVersion = "0";

    while (System.currentTimeMillis() - startTime < timeoutSec * 1000) {
      try {
        resourceVersion = watchVersion(resourceVersion);
        if (stopConditionMatched) {
          break;
        }
      } catch (RuntimeException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
          // see https://github.com/kubernetes-client/java/issues/259
          continue;
        } else {
          this.e = e;
          LOGGER.error("Runtime exception on watch resource", e);
          break;
        }
      } catch (Exception e) {
        this.e = e;
        LOGGER.error("Exception on watch resource", e);
        break;
      }
    }
  }

  private String watchVersion(String resourceVersion) throws ApiException {
    String newResourceVersion = resourceVersion;
    Watch<T> watch = Watch.createWatch(
            K8sStandardInterpreterLauncher.client,
            call.list(resourceVersion),
            new TypeToken<Watch.Response<T>>() {
            }.getType());

    try {
      for (Watch.Response<T> item : watch) {
        newResourceVersion = getResourceVersion(item);
        if (stopCondition.shouldStop(item)) {
          stopConditionMatched = true;
          break;
        }
      }
    } finally {
      try {
        watch.close();
      } catch (IOException e) {
        // error on close watch.
      }
      return newResourceVersion;
    }
  }


  private String getResourceVersion(Watch.Response<T> item) {
    if (item instanceof Map) {
      Object metadata = ((Map) item).get("metadata");
      if (metadata != null && metadata instanceof Map) {
        return (String) ((Map) metadata).get("resourceVersion");
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public boolean isStopConditionMatched() {
    return stopConditionMatched;
  }

  public boolean isWatching() {
    return t.isAlive();
  }

  public void await() {
    try {
      t.join();
    } catch (InterruptedException e) {
      LOGGER.error("Watcher interrupted", e);
    }
  }
}
