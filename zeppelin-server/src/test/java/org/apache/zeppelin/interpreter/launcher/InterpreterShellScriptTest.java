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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterpreterShellScriptTest {

  @Test
  void doesNotEvaluateDownloaderArguments(
      @TempDir Path temporaryDirectory) throws Exception {
    Path zeppelinHome = Paths.get("..").toAbsolutePath().normalize();
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("java-home/bin"))
        .getParent();
    Path captureFile = temporaryDirectory.resolve("java-arguments");
    Path fakeJava = javaHome.resolve("bin/java");
    Files.writeString(fakeJava,
        "#!/bin/bash\n"
            + "if [[ \"$1\" == \"-version\" ]]; then\n"
            + "  echo 'openjdk version \"11.0.0\"' >&2\n"
            + "  exit 0\n"
            + "fi\n"
            + "printf 'BEGIN\\n' >> \"${CAPTURE_FILE}\"\n"
            + "printf '<%s>\\n' \"$@\" >> \"${CAPTURE_FILE}\"\n"
            + "printf 'END\\n' >> \"${CAPTURE_FILE}\"\n",
        StandardCharsets.UTF_8);
    fakeJava.toFile().setExecutable(true);

    Path classpathMarker = temporaryDirectory.resolve("classpath-injected");
    Path classpathOverridesMarker = temporaryDirectory.resolve("classpath-overrides-injected");
    Path javaOptionsMarker = temporaryDirectory.resolve("java-options-injected");
    Path memoryMarker = temporaryDirectory.resolve("memory-injected");
    Path wildcardDirectory = Files.createDirectory(temporaryDirectory.resolve("wildcard jars"));
    Files.write(wildcardDirectory.resolve("dependency.jar"), new byte[] {1});
    String maliciousClasspath = wildcardDirectory + "/*;touch " + classpathMarker + ";";
    String maliciousClasspathOverrides =
        "override;touch " + classpathOverridesMarker + ";";
    String maliciousJavaOptions = "-Dsafe=true $(touch " + javaOptionsMarker + ")";
    String maliciousMemory = "-Xmx128m;touch " + memoryMarker + ";";

    Path confDirectory = Files.createDirectory(temporaryDirectory.resolve("conf"));
    Path interpreterDirectory = Files.createDirectory(temporaryDirectory.resolve("test"));
    Path localRepository = Files.createDirectory(temporaryDirectory.resolve("local-repo"));
    Path logDirectory = Files.createDirectory(temporaryDirectory.resolve("logs"));
    Path pidDirectory = Files.createDirectory(temporaryDirectory.resolve("run"));

    ProcessBuilder processBuilder = new ProcessBuilder(
        zeppelinHome.resolve("bin/interpreter.sh").toString(),
        "-p", "12345",
        "-r", ":",
        "-i", "group-id",
        "-d", interpreterDirectory.toString(),
        "-l", localRepository.toString(),
        "-g", "test");
    processBuilder.redirectErrorStream(true);
    Map<String, String> environment = processBuilder.environment();
    environment.put("JAVA_HOME", javaHome.toString());
    environment.put("ZEPPELIN_HOME", zeppelinHome.toString());
    environment.put("ZEPPELIN_CONF_DIR", confDirectory.toString());
    environment.put("ZEPPELIN_LOG_DIR", logDirectory.toString());
    environment.put("ZEPPELIN_PID_DIR", pidDirectory.toString());
    environment.put("INTERPRETER_GROUP_ID", "group-id");
    environment.put("CAPTURE_FILE", captureFile.toString());
    environment.put("ZEPPELIN_INTP_CLASSPATH", maliciousClasspath);
    environment.put("ZEPPELIN_INTP_CLASSPATH_OVERRIDES", maliciousClasspathOverrides);
    environment.put("ZEPPELIN_INTP_JAVA_OPTS", maliciousJavaOptions);
    environment.put("ZEPPELIN_INTP_MEM", maliciousMemory);

    Process process = processBuilder.start();
    assertTrue(process.waitFor(30, TimeUnit.SECONDS));
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertEquals(0, process.exitValue(), output);
    assertFalse(Files.exists(classpathMarker), output);
    assertFalse(Files.exists(classpathOverridesMarker), output);
    assertFalse(Files.exists(javaOptionsMarker), output);
    assertFalse(Files.exists(memoryMarker), output);
    List<String> capturedArguments = Files.readAllLines(captureFile, StandardCharsets.UTF_8);
    assertEquals(2, capturedArguments.stream().filter("BEGIN"::equals).count(),
        capturedArguments.toString());
    assertEquals(2, capturedArguments.stream().filter("<-Dsafe=true>"::equals).count(),
        capturedArguments.toString());
    assertEquals(2, capturedArguments.stream().filter("<$(touch>"::equals).count(),
        capturedArguments.toString());
    assertEquals(2, capturedArguments.stream()
        .filter(argument -> argument.startsWith("<" + maliciousClasspathOverrides + ":"))
        .filter(argument -> argument.contains(maliciousClasspath))
        .count(), capturedArguments.toString());
    assertTrue(capturedArguments.contains("<-Xmx128m;touch>"), capturedArguments.toString());
  }
}
