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

package org.apache.zeppelin.display;

import org.apache.zeppelin.interpreter.InterpreterContext;

/**
 *
 */
public abstract class AngularObjectWatcher {
  private InterpreterContext context;

  private String watcherId;

  public AngularObjectWatcher(InterpreterContext context, String watcherId) {
    this.context = context;
    this.watcherId = watcherId;
  }

  /**
   * @deprecated  use AngularObjectWatcher(context, watcherId) instead.
   *              watcherId should be specified.
   * @param context
   */
  @Deprecated
  public AngularObjectWatcher(InterpreterContext context) {
    this.context = context;
  }

  public String getWatcherId() {
    return watcherId;
  }

  public void setWatcherId(String watcherId) {
    this.watcherId = watcherId;
  }

  void watch(Object oldObject, Object newObject) {
    watch(oldObject, newObject, context);
  }

  public abstract void watch(Object oldObject, Object newObject, InterpreterContext context);
}
