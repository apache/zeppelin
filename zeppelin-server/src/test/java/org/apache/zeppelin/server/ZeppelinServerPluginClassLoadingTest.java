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
package org.apache.zeppelin.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class ZeppelinServerPluginClassLoadingTest {
  @TempDir
  Path tempDir;

  @Test
  void loadsAllPluginJarsWhileKeepingSlf4jOnTheParentClassLoader() throws Exception {
    Path firstJar = createJar(
        "first.jar", Map.of("first-plugin-resource.txt", "first".getBytes(UTF_8)));
    Path secondJar = createJar(
        "second.jar",
        Map.of(
            "second-plugin-resource.txt", "second".getBytes(UTF_8),
            "org/slf4j/Logger.class", readClassBytes(Logger.class)));
    WebAppContext webapp = new WebAppContext();
    LinkedHashSet<java.io.File> pluginClasspath = new LinkedHashSet<>();
    pluginClasspath.add(firstJar.toFile());
    pluginClasspath.add(secondJar.toFile());

    ZeppelinServer.configurePluginClassLoading(webapp, pluginClasspath);

    ClassLoader parent = Thread.currentThread().getContextClassLoader();
    try (WebAppClassLoader classLoader = new WebAppClassLoader(parent, webapp)) {
      assertEquals(2, classLoader.getURLs().length);
      assertEquals("first", readResource(classLoader, "first-plugin-resource.txt"));
      assertEquals("second", readResource(classLoader, "second-plugin-resource.txt"));
      assertNotNull(classLoader.findResource("org/slf4j/Logger.class"));
      assertSame(Logger.class, classLoader.loadClass(Logger.class.getName()));
    }
  }

  @Test
  void onlyTreatsShiroObjectDeclarationsAsClassAssignments() {
    Map<String, String> assignments = ZeppelinServer.findShiroClassAssignments(
        "[users]\n"
            + "alice = secret.example\n"
            + "[main]\n"
            + "krbRealm = org.apache.zeppelin.realm.kerberos.KerberosRealm\n"
            + "krbRealm.cookieDomain = domain.com\n"
            + "knox.groupResolverClass = example.CustomGroupResolver\n"
            + "[roles]\n"
            + "analyst = example.Role\n");

    assertEquals(1, assignments.size());
    assertEquals(
        "org.apache.zeppelin.realm.kerberos.KerberosRealm", assignments.get("krbRealm"));
    assertTrue(assignments.values().stream().noneMatch("domain.com"::equals));
    assertEquals(
        "example.CustomGroupResolver",
        ZeppelinServer.findNonBlankAssignment(
            "[users]\n"
                + "knox.groupResolverClass = ignored.UserValue\n"
                + "[main]\n"
                + "knox.groupResolverClass = example.CustomGroupResolver\n"
                + "[roles]\n"
                + "knox.groupResolverClass = ignored.RoleValue\n",
            "knox.groupResolverClass"));
  }

  private Path createJar(String fileName, Map<String, byte[]> entries) throws IOException {
    Path jar = tempDir.resolve(fileName);
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        output.putNextEntry(new JarEntry(entry.getKey()));
        output.write(entry.getValue());
        output.closeEntry();
      }
    }
    return jar;
  }

  private static byte[] readClassBytes(Class<?> clazz) throws IOException {
    String resource = "/" + clazz.getName().replace('.', '/') + ".class";
    try (InputStream input = clazz.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Class resource is missing: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String readResource(ClassLoader classLoader, String resource) throws IOException {
    try (InputStream input = classLoader.getResourceAsStream(resource)) {
      assertNotNull(input, "Resource is missing: " + resource);
      return new String(input.readAllBytes(), UTF_8);
    }
  }
}
