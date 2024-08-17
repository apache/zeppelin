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

package org.apache.zeppelin.livy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class WithLivyServer {
  private static final Logger LOGGER = LoggerFactory.getLogger(WithLivyServer.class);

  private static Optional<Process> livy = Optional.empty();

  protected static final String LIVY_HOME = System.getenv("LIVY_HOME");
  protected static final String SPARK_HOME = System.getenv("SPARK_HOME");

  protected static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "livy-it");
  protected static final File LIVY_CONF_DIR = new File(TMP_DIR, "conf");
  protected static final File LIVY_LOG_DIR = new File(TMP_DIR, "logs");

  protected static String LIVY_ENDPOINT;

  public static boolean checkPreCondition() {
    if (System.getenv("LIVY_HOME") == null) {
      LOGGER.warn(("livy integration is skipped because LIVY_HOME is not set"));
      return false;
    }
    if (System.getenv("SPARK_HOME") == null) {
      LOGGER.warn(("livy integration is skipped because SPARK_HOME is not set"));
      return false;
    }
    return true;
  }

  @BeforeAll
  public static void beforeAll() throws IOException {
    if (!checkPreCondition()) {
      return;
    }
    assertFalse(livy.isPresent());
    if (TMP_DIR.exists()) {
      FileUtils.deleteQuietly(TMP_DIR);
    }
    assertTrue(LIVY_CONF_DIR.mkdirs());
    assertTrue(LIVY_LOG_DIR.mkdirs());
    Files.copy(
        Paths.get(LIVY_HOME, "conf", "log4j.properties.template"),
        LIVY_CONF_DIR.toPath().resolve("log4j.properties"));

    LOGGER.info("SPARK_HOME: {}", SPARK_HOME);
    LOGGER.info("LIVY_HOME: {}", LIVY_HOME);
    LOGGER.info("LIVY_CONF_DIR: {}", LIVY_CONF_DIR.getAbsolutePath());
    LOGGER.info("LIVY_LOG_DIR: {}", LIVY_LOG_DIR.getAbsolutePath());

    File logFile = new File(TMP_DIR, "output.log");
    assertTrue(logFile.createNewFile());
    LOGGER.info("Redirect Livy's log to {}", logFile.getAbsolutePath());

    ProcessBuilder pb = new ProcessBuilder(LIVY_HOME + "/bin/livy-server")
        .directory(TMP_DIR)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

    pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
    pb.environment().put("LIVY_CONF_DIR", LIVY_CONF_DIR.getAbsolutePath());
    pb.environment().put("LIVY_LOG_DIR", LIVY_LOG_DIR.getAbsolutePath());
    pb.environment().put("SPARK_LOCAL_IP", "127.0.0.1");
    Process livyProc = pb.start();

    await().atMost(30, SECONDS).pollInterval(2, SECONDS).until(() -> {
      try {
        int exitCode = livyProc.exitValue();
        throw new IOException("Child process exited unexpectedly (exit code " + exitCode + ")");
      } catch (IllegalThreadStateException ignore) {
        // Process does not exit, try again.
      }
      try (Stream<String> lines = Files.lines(logFile.toPath(), StandardCharsets.UTF_8)) {
        // An example of bootstrap log:
        //   24/03/24 05:51:38 INFO WebServer: Starting server on http://cheng-pan-mbp.lan:8998
        Optional<String> started =
            lines.filter(line -> line.contains("Starting server on ")).findFirst();
        started.ifPresent(line ->
            LIVY_ENDPOINT = StringUtils.substringAfter(line, "Starting server on ").trim());
        return started.isPresent();
      }
    });

    LOGGER.info("Livy Server is started at {}", LIVY_ENDPOINT);
    livy = Optional.of(livyProc);
  }

  @AfterAll
  public static void afterAll() {
    livy.filter(Process::isAlive).ifPresent(proc -> {
      try {
        LOGGER.info("Stopping the Livy Server running at {}", LIVY_ENDPOINT);
        proc.destroy();
        if (!proc.waitFor(10, SECONDS)) {
          LOGGER.warn("Forcibly stopping the Livy Server running at {}", LIVY_ENDPOINT);
          proc.destroyForcibly();
          assertFalse(proc.isAlive());
        }
      } catch (InterruptedException ignore) {
        if (proc.isAlive()) {
          LOGGER.warn("Forcibly stopping the Livy Server running at {}", LIVY_ENDPOINT);
          proc.destroyForcibly();
          assertFalse(proc.isAlive());
        }
      }
    });
  }
}
