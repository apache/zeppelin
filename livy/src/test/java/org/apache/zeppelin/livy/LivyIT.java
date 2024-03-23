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
import org.apache.zeppelin.livy.cluster.MiniClusterUtils;
import org.apache.zeppelin.livy.cluster.MiniLivyMain;
import org.apache.zeppelin.livy.cluster.ProcessInfo;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "LIVY_HOME", matches = ".+", disabledReason = "SPARK_HOME is not set")
@EnabledIfEnvironmentVariable(named = "SPARK_HOME", matches = ".+", disabledReason = "LIVY_HOME is not set")
public class LivyIT {
    private static final Logger LOG = LoggerFactory.getLogger(LivyIT.class);

    private static Optional<Process> livy = Optional.empty();

    private static final File tmp = new File(System.getProperty("java.io.tmpdir"), "livy-it");
    private static final File livyConfDir = new File(tmp, "conf");
    private static final File livyLogDir = new File(tmp, "logs");

    private static String _livyEndpoint;

    @BeforeAll
    public static void setUp() throws IOException {
        assertFalse(livy.isPresent());
        if (tmp.exists()) {
            FileUtils.deleteQuietly(tmp);
        }
        assertTrue(livyConfDir.mkdirs());
        assertTrue(livyLogDir.mkdirs());

        File logFile = new File(tmp, "output.log");
        assertTrue(logFile.createNewFile());
        ProcessBuilder pb = new ProcessBuilder(System.getenv("LIVY_HOME") + "/bin/livy-server")
                .directory(tmp)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        pb.environment().put("LIVY_CONF_DIR", livyConfDir.getAbsolutePath());
        pb.environment().put("LIVY_LOG_DIR", livyLogDir.getAbsolutePath());
        pb.environment().put("SPARK_LOCAL_IP", "127.0.0.1");
        Process livyProc = pb.start();

        Awaitility.await().atMost(30, SECONDS).pollInterval(2, SECONDS).until(() -> {
            try {
                int exitCode = livyProc.exitValue();
                throw new IOException("Child process exited unexpectedly (exit code " + exitCode + ")");
            } catch (IllegalThreadStateException ignore) {
                // Process does not exit, try again.
            }
            try (Stream<String> lines = Files.lines(logFile.toPath(), StandardCharsets.UTF_8)) {
                // An example of bootstrap log:
                //   23/10/28 12:28:34 INFO WebServer: Starting server on http://my-laptop:8998
                Optional<String> started = lines.filter(line -> line.contains("Starting server on ")).findFirst();
                started.ifPresent(line -> _livyEndpoint = StringUtils.substringAfter(line, "Starting server on ").trim());
                return started.isPresent();
            }
        });

        livy = Optional.of(livyProc);
    }

    @AfterAll
    public static void tearDown() {
        livy.filter(Process::isAlive).ifPresent(proc -> {
            try {
                proc.destroy();
                if (!proc.waitFor(10, SECONDS)) {
                    proc.destroyForcibly();
                    assertFalse(proc.isAlive());
                }
            } catch (InterruptedException ignore) {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                    assertFalse(proc.isAlive());
                }
            }
        });
    }

    @Test
    void testSparkInterpreter() {
        LOG.info("Starting livy at {}", _livyEndpoint);
    }
}
