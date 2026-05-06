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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotebookRepoPathValidationTest {

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
      // URL-encoded variants must be rejected after decoding.
      "/%2e%2e/etc/passwd",
      "/foo/%2E%2E/bar",
      "/%2e/foo",
      // Double-encoded.
      "/%252e%252e/etc/passwd",
  })
  void rejectTraversalSegments_rejects_traversal(String malicious) {
    assertThrows(IOException.class,
        () -> NotebookPathValidator.rejectTraversalSegments(malicious),
        "expected rejection for: " + malicious);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/MyNote",
      "/folder/MyNote",
      "/folder/sub-folder/My Note With Spaces",
      "/한글노트",
      "/foo.bar.baz",
      // ".." and "." are rejected only as exact segments — names containing
      // dots remain valid.
      "/...",
      "/foo/...",
      "/foo..bar",
  })
  void rejectTraversalSegments_accepts_normal_paths(String safe) throws IOException {
    NotebookPathValidator.rejectTraversalSegments(safe);
  }

  @Test
  void rejectTraversalSegments_rejects_null() {
    assertThrows(IOException.class, () -> NotebookPathValidator.rejectTraversalSegments(null));
  }

  @Test
  void rejectTraversalSegments_rejects_excessive_encoding_layers() {
    // Six layers of "..", past the 5-layer decode cap.
    String payload = "/%252525252e%252525252e/etc";
    assertThrows(IOException.class, () -> NotebookPathValidator.rejectTraversalSegments(payload));
  }

  @Test
  void decodeRepeatedly_returns_input_when_already_decoded() throws IOException {
    assertEquals("/foo bar", NotebookPathValidator.decodeRepeatedly("/foo bar"));
  }

  @Test
  void decodeRepeatedly_decodes_until_stable() throws IOException {
    assertEquals("/..", NotebookPathValidator.decodeRepeatedly("/%252e%252e"));
  }

  @Test
  void decodeRepeatedly_throws_on_too_many_layers() {
    String payload = "/%2525252525252525252e";
    assertThrows(IOException.class, () -> NotebookPathValidator.decodeRepeatedly(payload));
  }
}
