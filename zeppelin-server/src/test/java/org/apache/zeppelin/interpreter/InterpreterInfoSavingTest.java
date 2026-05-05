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

import org.apache.zeppelin.dep.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterInfoSavingTest {

  @TempDir
  Path tempDir;

  @Test
  void testSaveAndLoad() throws IOException {
    // Create a new InterpreterInfoSaving
    InterpreterInfoSaving infoSaving = new InterpreterInfoSaving();
    
    // Add some repositories
    Repository repo1 = new Repository.Builder("central")
        .url("https://repo.maven.apache.org/maven2/")
        .build();
    
    Repository repo2 = new Repository.Builder("local")
        .url("file:///home/user/.m2/repository/")
        .snapshot()
        .build();
    
    infoSaving.interpreterRepositories.add(repo1);
    infoSaving.interpreterRepositories.add(repo2);
    
    // Save to file
    Path file = tempDir.resolve("interpreter.json");
    infoSaving.saveToFile(file);
    
    // Load from file
    InterpreterInfoSaving loaded = InterpreterInfoSaving.loadFromFile(file);
    
    // Verify
    assertNotNull(loaded);
    assertEquals(2, loaded.interpreterRepositories.size());
    
    Repository loadedRepo1 = loaded.interpreterRepositories.get(0);
    assertEquals("central", loadedRepo1.getId());
    assertEquals("https://repo.maven.apache.org/maven2/", loadedRepo1.getUrl());
    assertFalse(loadedRepo1.isSnapshot());
    
    Repository loadedRepo2 = loaded.interpreterRepositories.get(1);
    assertEquals("local", loadedRepo2.getId());
    assertEquals("file:///home/user/.m2/repository/", loadedRepo2.getUrl());
    assertTrue(loadedRepo2.isSnapshot());
  }

  @Test
  void testJsonSerialization() {
    InterpreterInfoSaving infoSaving = new InterpreterInfoSaving();
    
    Repository repo = new Repository.Builder("test-repo")
        .url("https://test.repo/maven2/")
        .credentials("user", "pass")
        .build();
    
    infoSaving.interpreterRepositories.add(repo);
    infoSaving.interpreterSettings = new HashMap<>();
    
    // Serialize to JSON
    String json = infoSaving.toJson();
    
    // Deserialize from JSON
    InterpreterInfoSaving deserialized = InterpreterInfoSaving.fromJson(json);
    
    assertNotNull(deserialized);
    assertEquals(1, deserialized.interpreterRepositories.size());
    assertEquals("test-repo", deserialized.interpreterRepositories.get(0).getId());
  }

  @Test
  void testBackwardCompatibility() throws IOException {
    // This test simulates loading an older format interpreter.json
    // that might have RemoteRepository serialized with InterfaceAdapter
    
    String oldFormatJson = "{\n" +
        "  \"interpreterSettings\": {},\n" +
        "  \"interpreterRepositories\": [\n" +
        "    {\n" +
        "      \"snapshot\": false,\n" +
        "      \"id\": \"central\",\n" +
        "      \"url\": \"https://repo.maven.apache.org/maven2/\"\n" +
        "    },\n" +
        "    {\n" +
        "      \"snapshot\": true,\n" +
        "      \"id\": \"apache-snapshots\",\n" +
        "      \"url\": \"https://repository.apache.org/snapshots/\"\n" +
        "    }\n" +
        "  ]\n" +
        "}";
    
    Path file = tempDir.resolve("old-interpreter.json");
    Files.write(file, oldFormatJson.getBytes(StandardCharsets.UTF_8));
    
    // This should successfully load the old format
    InterpreterInfoSaving loaded = InterpreterInfoSaving.loadFromFile(file);
    
    assertNotNull(loaded);
    assertEquals(2, loaded.interpreterRepositories.size());
    
    // Verify the repositories were loaded correctly
    Repository repo1 = loaded.interpreterRepositories.get(0);
    assertEquals("central", repo1.getId());
    assertEquals("https://repo.maven.apache.org/maven2/", repo1.getUrl());
    assertFalse(repo1.isSnapshot());
    
    Repository repo2 = loaded.interpreterRepositories.get(1);
    assertEquals("apache-snapshots", repo2.getId());
    assertEquals("https://repository.apache.org/snapshots/", repo2.getUrl());
    assertTrue(repo2.isSnapshot());
  }
}