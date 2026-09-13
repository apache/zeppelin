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

package org.apache.zeppelin.interpreter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class InterpreterRuntimeArtifactIT {

  private static final String INTERPRETER_CLASS =
      "org/apache/zeppelin/interpreter/Interpreter.class";
  private static final String RELOCATED_THRIFT_CLASS =
      "shaded/org/apache/zeppelin/org/apache/thrift/TException.class";
  private static final String AETHER_REMOTE_REPOSITORY_CLASS =
      "org/eclipse/aether/repository/RemoteRepository.class";
  private static final String RELOCATED_AETHER_REMOTE_REPOSITORY_CLASS =
      "shaded/org/apache/zeppelin/org/eclipse/aether/repository/RemoteRepository.class";
  private static final String HADOOP_CONFIGURATION_CLASS =
      "org/apache/hadoop/conf/Configuration.class";
  private static final String RELOCATED_SNAPPY_CLASS =
      "shaded/org/apache/zeppelin/org/xerial/snappy/Snappy.class";
  private static final String RUNTIME_JAR_PREFIX = "zeppelin-interpreter-shaded-";

  private static Path buildDirectory;
  private static Path moduleDirectory;
  private static Path stagingDirectory;
  private static Path mainJar;
  private static Path runtimeJar;
  private static Path stagedRuntimeJar;
  private static String hadoopScope;

  @BeforeAll
  static void setUpArtifactPaths() {
    buildDirectory = Path.of(requiredProperty("runtimeArtifactBuildDirectory"))
        .toAbsolutePath()
        .normalize();
    moduleDirectory = Path.of(requiredProperty("runtimeArtifactModuleDirectory"))
        .toAbsolutePath()
        .normalize();
    stagingDirectory = Path.of(requiredProperty("runtimeArtifactStagingDirectory"))
        .toAbsolutePath()
        .normalize();

    String finalName = requiredProperty("runtimeArtifactFinalName");
    String version = requiredProperty("runtimeArtifactVersion");
    String runtimeJarName = RUNTIME_JAR_PREFIX + version + ".jar";

    mainJar = buildDirectory.resolve(finalName + ".jar");
    runtimeJar = buildDirectory.resolve(runtimeJarName);
    stagedRuntimeJar = stagingDirectory.resolve(runtimeJarName);
    hadoopScope = requiredProperty("runtimeArtifactHadoopScope");
  }

  @Test
  void publishesOnlyCurrentRuntimeJar() throws Exception {
    assertRegularFile(runtimeJar);
    assertRegularFile(stagedRuntimeJar);

    try (Stream<Path> entries = Files.list(stagingDirectory)) {
      List<Path> runtimeJars = entries
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith(RUNTIME_JAR_PREFIX) && name.endsWith(".jar");
          })
          .map(path -> path.toAbsolutePath().normalize())
          .sorted()
          .collect(Collectors.toList());

      assertEquals(
          Collections.singletonList(stagedRuntimeJar),
          runtimeJars,
          "interpreter/ must contain only the current runtime JAR");
    }

    assertArrayEquals(
        sha256(runtimeJar),
        sha256(stagedRuntimeJar),
        "The staged runtime JAR must be an exact copy of the packaged runtime JAR");
  }

  @Test
  void keepsMainArtifactUnshaded() throws Exception {
    try (JarFile jar = openJar(mainJar)) {
      assertNotNull(jar.getJarEntry(INTERPRETER_CLASS));
      assertFalse(hasEntryWithPrefix(jar, "shaded/"));
      assertNull(jar.getJarEntry(RELOCATED_THRIFT_CLASS));
    }
  }

  @Test
  void buildsRelocatedRuntimeJar() throws Exception {
    try (JarFile jar = openJar(runtimeJar)) {
      assertNotNull(jar.getJarEntry(INTERPRETER_CLASS));
      assertNotNull(jar.getJarEntry(RELOCATED_THRIFT_CLASS));
      assertNull(jar.getJarEntry("org/apache/thrift/TException.class"));
      assertNotNull(jar.getJarEntry(RELOCATED_AETHER_REMOTE_REPOSITORY_CLASS));
      assertNull(jar.getJarEntry(AETHER_REMOTE_REPOSITORY_CLASS));
      assertNotNull(jar.getJarEntry("META-INF/LICENSE"));
      assertNotNull(jar.getJarEntry("META-INF/NOTICE"));
    }
  }

  @Test
  void honorsHadoopPackagingProfile() throws Exception {
    try (JarFile jar = openJar(runtimeJar)) {
      assertNull(
          jar.getJarEntry(
              "shaded/org/apache/zeppelin/org/apache/hadoop/conf/Configuration.class"),
          "Hadoop classes must remain unrelocated");

      if ("provided".equals(hadoopScope)) {
        assertNull(jar.getJarEntry(HADOOP_CONFIGURATION_CLASS));
        assertFalse(hasEntryWithPrefix(jar, "org/apache/hadoop/"));
        assertNull(jar.getJarEntry(RELOCATED_SNAPPY_CLASS));
      } else if ("compile".equals(hadoopScope)) {
        assertNotNull(jar.getJarEntry(HADOOP_CONFIGURATION_CLASS));
        assertTrue(hasEntryWithPrefix(jar, "org/apache/hadoop/shaded/"));
        assertNotNull(jar.getJarEntry(RELOCATED_SNAPPY_CLASS));
      } else {
        throw new AssertionError("Unexpected hadoop.deps.scope: " + hadoopScope);
      }
    }
  }

  @Test
  void doesNotCreateDependencyReducedPom() {
    assertFalse(
        Files.exists(moduleDirectory.resolve("dependency-reduced-pom.xml")),
        "Shading must not replace the module's published POM");
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    assertNotNull(value, "Missing system property: " + name);
    assertFalse(value.trim().isEmpty(), "Empty system property: " + name);
    return value;
  }

  private static void assertRegularFile(Path path) {
    assertTrue(Files.isRegularFile(path), "Missing artifact: " + path);
  }

  private static JarFile openJar(Path path) throws IOException {
    assertRegularFile(path);
    return new JarFile(path.toFile());
  }

  private static boolean hasEntryWithPrefix(JarFile jar, String prefix) {
    return jar.stream().anyMatch(entry -> entry.getName().startsWith(prefix));
  }

  private static byte[] sha256(Path path) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[8192];
    try (InputStream input = Files.newInputStream(path)) {
      int bytesRead;
      while ((bytesRead = input.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }
    }
    return digest.digest();
  }
}
