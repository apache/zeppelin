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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.junit.jupiter.api.Test;

class YarnProcessLaunchObserverTest {

  @Test
  void detectsSubmittedYarnApplication() {
    AtomicReference<ApplicationId> detectedApp = new AtomicReference<>();
    RemoteInterpreterManagedProcess process = mock(RemoteInterpreterManagedProcess.class);
    YarnProcessLaunchObserver observer =
        new YarnProcessLaunchObserver((appId, ignored) -> detectedApp.set(appId));

    observer.onProcessLaunch(
        "INFO Client: Submitted application application_1720000000000_0042", process);

    assertEquals("application_1720000000000_0042", detectedApp.get().toString());
  }

  @Test
  void ignoresLaunchOutputWithoutSubmittedApplication() {
    AtomicReference<ApplicationId> detectedApp = new AtomicReference<>();
    YarnProcessLaunchObserver observer =
        new YarnProcessLaunchObserver((appId, ignored) -> detectedApp.set(appId));

    observer.onProcessLaunch(
        "INFO Client: Application report for application_1720000000000_0042", null);

    assertNull(detectedApp.get());
  }
}
