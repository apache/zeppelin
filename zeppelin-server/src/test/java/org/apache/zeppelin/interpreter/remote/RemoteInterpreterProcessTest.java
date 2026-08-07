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

package org.apache.zeppelin.interpreter.remote;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteInterpreterProcessTest {

  @Test
  void terminationListenerRunsOnceWhenInstalledAfterTermination() {
    TestRemoteInterpreterProcess process = new TestRemoteInterpreterProcess();
    AtomicInteger notifications = new AtomicInteger();

    process.terminate();
    process.setTerminationListener(notifications::incrementAndGet);
    process.terminate();

    assertEquals(1, notifications.get());
  }

  private static final class TestRemoteInterpreterProcess extends RemoteInterpreterProcess {

    private TestRemoteInterpreterProcess() {
      super(1, 1, "localhost", 1);
    }

    private void terminate() {
      notifyTermination();
    }

    @Override
    public String getInterpreterGroupId() {
      return "group";
    }

    @Override
    public String getInterpreterSettingName() {
      return "setting";
    }

    @Override
    public void start(String userName) {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getHost() {
      return "localhost";
    }

    @Override
    public int getPort() {
      return 1;
    }

    @Override
    public boolean isAlive() {
      return false;
    }

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public void processStarted(int port, String host) {
    }

    @Override
    public String getErrorMessage() {
      return null;
    }
  }
}
