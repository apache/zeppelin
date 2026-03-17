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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpreterConfigKeysTest {

  @Test
  void interpreterConnectionPoolSizeKey() {
    assertEquals("zeppelin.interpreter.connection.poolsize",
      InterpreterConfigKeys.INTERPRETER_CONNECTION_POOL_SIZE);
  }

  @Test
  void interpreterLifecycleManagerClassKey() {
    assertEquals("zeppelin.interpreter.lifecyclemanager.class",
      InterpreterConfigKeys.INTERPRETER_LIFECYCLE_MANAGER_CLASS);
  }

  @Test
  void interpreterLifecycleManagerTimeoutCheckIntervalKey() {
    assertEquals("zeppelin.interpreter.lifecyclemanager.timeout.checkinterval",
      InterpreterConfigKeys.INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_CHECK_INTERVAL);
  }

  @Test
  void interpreterLifecycleManagerTimeoutThresholdKey() {
    assertEquals("zeppelin.interpreter.lifecyclemanager.timeout.threshold",
      InterpreterConfigKeys.INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD);
  }

  @Test
  void proxyUrlKey() {
    assertEquals("zeppelin.proxy.url", InterpreterConfigKeys.PROXY_URL);
  }

  @Test
  void proxyUserKey() {
    assertEquals("zeppelin.proxy.user", InterpreterConfigKeys.PROXY_USER);
  }

  @Test
  void proxyPasswordKey() {
    assertEquals("zeppelin.proxy.password", InterpreterConfigKeys.PROXY_PASSWORD);
  }

  @Test
  void interpreterDepMvnRepoKey() {
    assertEquals("zeppelin.interpreter.dep.mvnRepo",
      InterpreterConfigKeys.INTERPRETER_DEP_MVNREPO);
  }

  @Test
  void schedulerThreadpoolSizeKey() {
    assertEquals("zeppelin.scheduler.threadpool.size",
      InterpreterConfigKeys.SCHEDULER_THREADPOOL_SIZE);
  }
}
