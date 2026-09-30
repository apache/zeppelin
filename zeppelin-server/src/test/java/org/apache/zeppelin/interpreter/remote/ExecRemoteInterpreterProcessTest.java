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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Launches a stand-in interpreter process that never registers with the server, so the launch
 * stays in LAUNCHED until it times out or is cancelled. It records a SIGTERM, which in a real
 * interpreter process would run the shutdown hook that unregisters its interpreter group id.
 */
@DisabledOnOs(OS.WINDOWS)
class ExecRemoteInterpreterProcessTest {

  @TempDir
  Path tempDir;

  @Test
  @Timeout(60)
  void launchTimeoutDestroysTheProcess() throws Exception {
    Path pidFile = tempDir.resolve("pid");
    ExecRemoteInterpreterProcess process = createProcess(neverRegisteringRunner(pidFile), 3000);

    assertThrows(IOException.class, () -> process.start("anonymous"));

    assertExits(readPid(pidFile));
    assertNoShutdownHookRan();
  }

  @Test
  @Timeout(60)
  void stopWhileLaunchingEndsTheLaunchAndDestroysTheProcess() throws Exception {
    Path pidFile = tempDir.resolve("pid");
    ExecRemoteInterpreterProcess process =
        createProcess(neverRegisteringRunner(pidFile), (int) TimeUnit.MINUTES.toMillis(10));

    CompletableFuture<Void> launch = CompletableFuture.runAsync(() -> {
      try {
        process.start("anonymous");
      } catch (IOException e) {
        throw new CompletionException(e);
      }
    });
    long pid = readPid(pidFile);

    // What ManagedInterpreterGroup.close() does when the group is closed during the launch.
    process.stop();

    ExecutionException launchFailure =
        assertThrows(ExecutionException.class, () -> launch.get(10, TimeUnit.SECONDS));
    assertTrue(launchFailure.getCause() instanceof IOException, launchFailure.toString());
    assertExits(pid);
    assertNoShutdownHookRan();
  }

  private Path neverRegisteringRunner(Path pidFile) throws IOException {
    Path runner = tempDir.resolve("interpreter.sh");
    Files.write(runner, ("#!/bin/sh\n"
        + "trap 'echo TERM > " + tempDir.resolve("sigterm") + "; exit 143' TERM\n"
        + "echo $$ > " + pidFile + "\n"
        + "while true; do sleep 1; done\n").getBytes(StandardCharsets.UTF_8));
    runner.toFile().setExecutable(true);
    return runner;
  }

  private ExecRemoteInterpreterProcess createProcess(Path runner, int connectTimeout) {
    return new ExecRemoteInterpreterProcess(
        0, "127.0.0.1", ":", tempDir.toString(), tempDir.toString(), new HashMap<>(),
        connectTimeout, 10, "test", "test-shared_process", false, runner.toString());
  }

  private static long readPid(Path pidFile) throws Exception {
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    while (System.currentTimeMillis() < deadline) {
      if (Files.exists(pidFile)) {
        String pid = new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8).trim();
        if (!pid.isEmpty()) {
          return Long.parseLong(pid);
        }
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("the runner did not write its pid");
  }

  private static void assertExits(long pid) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    while (isAlive(pid) && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertFalse(isAlive(pid), "the launched process " + pid + " is still running");
  }

  private void assertNoShutdownHookRan() {
    assertFalse(Files.exists(tempDir.resolve("sigterm")),
        "the process received SIGTERM, so a real interpreter process would run its shutdown hook");
  }

  private static boolean isAlive(long pid) {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }
}
