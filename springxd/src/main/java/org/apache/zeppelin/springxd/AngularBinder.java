/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.springxd;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.interpreter.InterpreterContext;

/**
 * Helper class that leverages the IntepreterContext Angular support to register client side
 * controle with the back-end
 */
public class AngularBinder {

  /**
   * Defines the deployed streams status.
   */
  public enum ResourceStatus {
    DEPLOYED, DESTROYED
  };

  @SuppressWarnings("unchecked")
  public static void bind(InterpreterContext context, String name, Object value, String noteId,
      AngularObjectWatcher watcher) {

    AngularObjectRegistry registry = context.getAngularObjectRegistry();

    if (registry.get(name, noteId) == null) {
      registry.add(name, value, noteId);
    } else {
      registry.get(name, noteId).set(value);
    }

    if (registry.get(name, noteId) != null) {
      registry.get(name, noteId).addWatcher(watcher);
    }
  }
}
