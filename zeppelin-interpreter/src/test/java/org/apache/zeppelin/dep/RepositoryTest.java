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

package org.apache.zeppelin.dep;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

  @Test
  void testToRemoteRepository() {
    // Test basic repository conversion
    Repository repo = new Repository.Builder("test-repo")
        .url("https://repo.maven.apache.org/maven2/")
        .build();
    
    RemoteRepository remoteRepo = repo.toRemoteRepository();
    
    assertEquals("test-repo", remoteRepo.getId());
    assertEquals("https://repo.maven.apache.org/maven2/", remoteRepo.getUrl());
    assertEquals("default", remoteRepo.getContentType());
    assertNotNull(remoteRepo.getPolicy(false));
    assertTrue(remoteRepo.getPolicy(false).isEnabled());
  }

  @Test
  void testToRemoteRepositoryWithSnapshot() {
    Repository repo = new Repository.Builder("snapshot-repo")
        .url("https://repo.maven.apache.org/maven2/")
        .snapshot()
        .build();
    
    RemoteRepository remoteRepo = repo.toRemoteRepository();
    
    assertEquals("snapshot-repo", remoteRepo.getId());
    assertTrue(remoteRepo.getPolicy(true).isEnabled());
  }

  @Test
  void testToRemoteRepositoryWithAuthentication() {
    Repository repo = new Repository.Builder("auth-repo")
        .url("https://private.repo/maven2/")
        .credentials("user", "pass")
        .build();
    
    RemoteRepository remoteRepo = repo.toRemoteRepository();
    
    assertNotNull(remoteRepo.getAuthentication());
  }

  @Test
  void testToRemoteRepositoryWithProxy() {
    Repository repo = new Repository.Builder("proxy-repo")
        .url("https://repo.maven.apache.org/maven2/")
        .proxy("HTTP", "proxy.host", 8080, "proxyUser", "proxyPass")
        .build();
    
    RemoteRepository remoteRepo = repo.toRemoteRepository();
    
    assertNotNull(remoteRepo.getProxy());
    assertEquals("proxy.host", remoteRepo.getProxy().getHost());
    assertEquals(8080, remoteRepo.getProxy().getPort());
  }

  @Test
  void testFromRemoteRepository() {
    RemoteRepository remoteRepo = new RemoteRepository.Builder("central", "default", 
        "https://repo.maven.apache.org/maven2/")
        .setReleasePolicy(new org.eclipse.aether.repository.RepositoryPolicy(true, null, null))
        .setSnapshotPolicy(new org.eclipse.aether.repository.RepositoryPolicy(false, null, null))
        .build();
    
    Repository repo = Repository.fromRemoteRepository(remoteRepo);
    
    assertEquals("central", repo.getId());
    assertEquals("https://repo.maven.apache.org/maven2/", repo.getUrl());
    assertFalse(repo.isSnapshot());
  }

  @Test
  void testFromRemoteRepositoryWithSnapshot() {
    RemoteRepository remoteRepo = new RemoteRepository.Builder("snapshots", "default", 
        "https://repo.maven.apache.org/maven2/")
        .setSnapshotPolicy(new org.eclipse.aether.repository.RepositoryPolicy(true, null, null))
        .build();
    
    Repository repo = Repository.fromRemoteRepository(remoteRepo);
    
    assertEquals("snapshots", repo.getId());
    assertTrue(repo.isSnapshot());
  }

  @Test
  void testRoundTripConversion() {
    // Test that conversion is consistent (with data loss for auth/proxy)
    Repository original = new Repository.Builder("test")
        .url("https://test.repo/maven2/")
        .snapshot()
        .build();
    
    RemoteRepository remote = original.toRemoteRepository();
    Repository converted = Repository.fromRemoteRepository(remote);
    
    assertEquals(original.getId(), converted.getId());
    assertEquals(original.getUrl(), converted.getUrl());
    assertEquals(original.isSnapshot(), converted.isSnapshot());
  }

  @Test
  void testJsonSerialization() {
    Repository repo = new Repository.Builder("json-test")
        .url("https://test.repo/")
        .credentials("user", "pass")
        .proxy("HTTP", "proxy", 8080, "puser", "ppass")
        .build();
    
    String json = repo.toJson();
    Repository deserialized = Repository.fromJson(json);
    
    assertEquals(repo.getId(), deserialized.getId());
    assertEquals(repo.getUrl(), deserialized.getUrl());
    // Test that credentials are preserved in JSON
    assertNotNull(deserialized.getAuthentication());
    assertNotNull(deserialized.getProxy());
  }

  // Input validation tests
  @Test
  void testInvalidRepositoryId() {
    // Test null ID
    assertThrows(RepositoryException.class, () -> new Repository.Builder(null));
    
    // Test empty ID
    assertThrows(RepositoryException.class, () -> new Repository.Builder(""));
    
    // Test invalid characters in ID
    assertThrows(RepositoryException.class, () -> new Repository.Builder("repo@invalid"));
    assertThrows(RepositoryException.class, () -> new Repository.Builder("repo with spaces"));
    assertThrows(RepositoryException.class, () -> new Repository.Builder("repo/with/slash"));
  }

  @Test
  void testValidRepositoryId() {
    // Test valid IDs
    assertDoesNotThrow(() -> new Repository.Builder("central").build());
    assertDoesNotThrow(() -> new Repository.Builder("my-repo").build());
    assertDoesNotThrow(() -> new Repository.Builder("repo_123").build());
    assertDoesNotThrow(() -> new Repository.Builder("repo.with.dots").build());
    assertDoesNotThrow(() -> new Repository.Builder("123-repo-456").build());
  }

  @Test
  void testInvalidUrl() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test null URL
    assertThrows(RepositoryException.class, () -> builder.url(null));
    
    // Test empty URL
    assertThrows(RepositoryException.class, () -> builder.url(""));
    
    // Test invalid URL format
    assertThrows(RepositoryException.class, () -> builder.url("not-a-url"));
    assertThrows(RepositoryException.class, () -> builder.url("ftp://invalid-protocol"));
  }

  @Test
  void testValidUrl() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test valid URLs
    assertDoesNotThrow(() -> builder.url("https://repo.maven.apache.org/maven2/").build());
    assertDoesNotThrow(() -> builder.url("http://localhost:8080/nexus/").build());
    assertDoesNotThrow(() -> builder.url("file:///home/user/.m2/repository/").build());
  }

  @Test
  void testInvalidCredentials() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test username without password
    assertThrows(RepositoryException.class, () -> builder.credentials("user", null));
    assertThrows(RepositoryException.class, () -> builder.credentials("user", ""));
    
    // Test password without username
    assertThrows(RepositoryException.class, () -> builder.credentials(null, "pass"));
    assertThrows(RepositoryException.class, () -> builder.credentials("", "pass"));
  }

  @Test
  void testValidCredentials() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test valid credentials
    assertDoesNotThrow(() -> builder.credentials("user", "pass").build());
    assertDoesNotThrow(() -> builder.credentials(null, null).build());
  }

  @Test
  void testInvalidProxy() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test invalid protocol
    assertThrows(RepositoryException.class, () -> builder.proxy("FTP", "proxy.host", 8080, null, null));
    assertThrows(RepositoryException.class, () -> builder.proxy(null, "proxy.host", 8080, null, null));
    assertThrows(RepositoryException.class, () -> builder.proxy("", "proxy.host", 8080, null, null));
    
    // Test invalid host
    assertThrows(RepositoryException.class, () -> builder.proxy("HTTP", null, 8080, null, null));
    assertThrows(RepositoryException.class, () -> builder.proxy("HTTP", "", 8080, null, null));
    
    // Test invalid port
    assertThrows(RepositoryException.class, () -> builder.proxy("HTTP", "proxy.host", 0, null, null));
    assertThrows(RepositoryException.class, () -> builder.proxy("HTTP", "proxy.host", -1, null, null));
    assertThrows(RepositoryException.class, () -> builder.proxy("HTTP", "proxy.host", 65536, null, null));
  }

  @Test
  void testValidProxy() {
    Repository.Builder builder = new Repository.Builder("test");
    
    // Test valid proxy configurations
    assertDoesNotThrow(() -> builder.proxy("HTTP", "proxy.host", 8080, null, null).build());
    assertDoesNotThrow(() -> new Repository.Builder("test2").proxy("HTTPS", "proxy.host", 443, "user", "pass").build());
    assertDoesNotThrow(() -> new Repository.Builder("test3").proxy("http", "proxy.host", 3128, null, null).build()); // case insensitive
  }

  @Test
  void testInvalidJsonDeserialization() {
    // Test null JSON
    assertThrows(RepositoryException.class, () -> Repository.fromJson(null));
    
    // Test empty JSON
    assertThrows(RepositoryException.class, () -> Repository.fromJson(""));
    
    // Test invalid JSON format
    assertThrows(RepositoryException.class, () -> Repository.fromJson("not-json"));
    assertThrows(RepositoryException.class, () -> Repository.fromJson("{invalid json}"));
    
    // Test JSON that results in null
    assertThrows(RepositoryException.class, () -> Repository.fromJson("null"));
  }

  @Test
  void testValidJsonDeserialization() {
    String validJson = "{\"id\":\"test\",\"url\":\"https://repo.maven.apache.org/maven2/\",\"snapshot\":false}";
    
    assertDoesNotThrow(() -> {
      Repository repo = Repository.fromJson(validJson);
      assertEquals("test", repo.getId());
      assertEquals("https://repo.maven.apache.org/maven2/", repo.getUrl());
      assertFalse(repo.isSnapshot());
    });
  }

  @Test
  void testToRemoteRepositoryValidation() {
    // Test with missing URL
    Repository repo = new Repository.Builder("test").build();
    assertThrows(RepositoryException.class, () -> repo.toRemoteRepository());
    
    // Test with valid repository
    Repository validRepo = new Repository.Builder("test")
        .url("https://repo.maven.apache.org/maven2/")
        .build();
    assertDoesNotThrow(() -> validRepo.toRemoteRepository());
  }

  @Test
  void testFromRemoteRepositoryValidation() {
    // Test with null RemoteRepository
    assertThrows(RepositoryException.class, () -> Repository.fromRemoteRepository(null));
  }

  @Test
  void testBackwardCompatibilityWithEmptyUrl() {
    // Test that repositories with empty URLs can be parsed (for backward compatibility)
    String jsonWithoutUrl = "{\"id\":\"test\",\"snapshot\":false}";
    
    assertDoesNotThrow(() -> {
      Repository repo = Repository.fromJson(jsonWithoutUrl);
      assertEquals("test", repo.getId());
      assertNull(repo.getUrl());
    });
  }
}