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

package org.apache.zeppelin.common;

/**
 * Canonical string constants for interpreter-related configuration keys.
 * Use these instead of plain string literals to catch typos at compile time.
 */
public final class InterpreterConfigKeys {
  private InterpreterConfigKeys() {}

  public static final String INTERPRETER_CONNECTION_POOL_SIZE =
      "zeppelin.interpreter.connection.poolsize";
  public static final String INTERPRETER_LIFECYCLE_MANAGER_CLASS =
      "zeppelin.interpreter.lifecyclemanager.class";
  public static final String INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL =
      "zeppelin.interpreter.lifecyclemanager.timeout.checkinterval";
  public static final String INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD =
      "zeppelin.interpreter.lifecyclemanager.timeout.threshold";
  public static final String PROXY_URL = "zeppelin.proxy.url";
  public static final String PROXY_USER = "zeppelin.proxy.user";
  public static final String PROXY_PASSWORD = "zeppelin.proxy.password";
  public static final String INTERPRETER_DEP_MVNREPO = "zeppelin.interpreter.dep.mvnRepo";
  public static final String SCHEDULER_THREADPOOL_SIZE = "zeppelin.scheduler.threadpool.size";
}
