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
package org.apache.zeppelin.socket;

import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import org.apache.http.annotation.GuardedBy;
import org.eclipse.jetty.websocket.api.StatusCode;

/**
 * Notebook Socket Task
 * @param <T>
 */
public abstract class NotebookSocketTask<T> implements CancellableTask<T> {
  @GuardedBy("this") private NotebookSocket socket;

  private static final String errMsg = "Timeout and force it to close";

  protected synchronized void setSocket(NotebookSocket s) {
    socket = s;
  }

  public synchronized void cancel() {
    if (socket.isConnected()) {
      socket.getSession().close(StatusCode.NORMAL, errMsg);
    }
  }

  public RunnableFuture<T> newTask() {
    return new FutureTask<T>(this) {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        NotebookSocketTask.this.cancel();
        return super.cancel(mayInterruptIfRunning);
      }
    };
  }
}
