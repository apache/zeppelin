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
package org.apache.zeppelin.notebook.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Validates that {@link NotebookRepo}'s default helpers reject path traversal
 * segments in user-controlled note / folder paths.
 *
 * <p>This is a backend-level defence: callers also normalize the path at the
 * service layer ({@code NotebookService.normalizeNotePath}) but every
 * {@link NotebookRepo} implementation now refuses to compose a filesystem path
 * or object-store key from a payload containing {@code ..} or {@code .}
 * segments.
 */
class NotebookRepoPathValidationTest {

  private final NotebookRepo repo = new InMemoryStub();

  @ParameterizedTest
  @ValueSource(strings = {
      "/../etc/passwd",
      "/foo/../../etc/passwd",
      "/foo/../bar",
      "/foo/./bar",
      "/./bar",
      "/foo/..",
      "/..",
      "/.",
  })
  void buildNoteFileName_rejects_traversal_segments(String malicious) {
    assertThrows(IOException.class,
        () -> repo.buildNoteFileName("noteid", malicious),
        "expected rejection for: " + malicious);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/MyNote",
      "/folder/MyNote",
      "/folder/sub-folder/My Note With Spaces",
      "/한글노트",
      "/foo.bar.baz",
      "/...",
      "/foo/...",
  })
  void buildNoteFileName_accepts_normal_paths(String safe) throws IOException {
    String fileName = repo.buildNoteFileName("noteid", safe);
    assertNotNull(fileName);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/../etc/passwd",
      "/foo/../bar",
      "/foo/./bar",
      "../etc/passwd",
      "./bar",
      // URL-encoded variants must be rejected after decoding.
      "/%2e%2e/etc/passwd",
      "/foo/%2E%2E/bar",
      "/%2e/foo",
      // Double-encoded.
      "/%252e%252e/etc/passwd",
  })
  void rejectTraversalSegments_rejects_traversal(String malicious) {
    assertThrows(IOException.class,
        () -> repo.rejectTraversalSegments(malicious),
        "expected rejection for: " + malicious);
  }

  @Test
  void rejectTraversalSegments_rejects_excessive_encoding_layers() {
    // 6 layers of "..", far past the 5-layer decode cap.
    String payload = "/%252525252e%252525252e/etc";
    assertThrows(IOException.class, () -> repo.rejectTraversalSegments(payload));
  }

  @Test
  void rejectTraversalSegments_rejects_null() {
    assertThrows(IOException.class, () -> repo.rejectTraversalSegments(null));
  }

  @Test
  void buildNoteFileName_format_is_unchanged_for_normal_input() throws IOException {
    assertEquals("MyNote_abc123.zpln", repo.buildNoteFileName("abc123", "/MyNote"));
    assertEquals("folder/sub/My Note_n1.zpln",
        repo.buildNoteFileName("n1", "/folder/sub/My Note"));
  }

  /**
   * Minimal {@link NotebookRepo} stub that exercises only the default
   * helpers under test.
   */
  private static final class InMemoryStub implements NotebookRepo {
    @Override
    public void init(ZeppelinConfiguration zConf, NoteParser noteParser) {
    }

    @Override
    public Map<String, NoteInfo> list(AuthenticationInfo subject) {
      return Collections.emptyMap();
    }

    @Override
    public Note get(String noteId, String notePath, AuthenticationInfo subject) {
      return null;
    }

    @Override
    public void save(Note note, AuthenticationInfo subject) {
    }

    @Override
    public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) {
    }

    @Override
    public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {
    }

    @Override
    public void remove(String noteId, String notePath, AuthenticationInfo subject) {
    }

    @Override
    public void remove(String folderPath, AuthenticationInfo subject) {
    }

    @Override
    public void close() {
    }

    @Override
    public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
      return Collections.emptyList();
    }

    @Override
    public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    }

    @Override
    public NoteParser getNoteParser() {
      return null;
    }
  }
}
