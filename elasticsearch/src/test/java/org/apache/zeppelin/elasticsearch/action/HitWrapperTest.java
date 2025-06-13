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

package org.apache.zeppelin.elasticsearch.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HitWrapperTest {

  private final String validJson = "{\"foo\":\"bar\",\"num\":42}";

  @Test
  @DisplayName("should store index, type, id, and source correctly")
  void shouldStoreFieldsCorrectly() {
    // Given
    String index = "my-index";
    String type = "_doc";
    String id = "123";

    // When
    HitWrapper hit = new HitWrapper(index, type, id, validJson);

    // Then
    assertEquals(index, hit.getIndex());
    assertEquals(type, hit.getType());
    assertEquals(id, hit.getId());
    assertEquals(validJson, hit.getSourceAsString());
  }

  @Test
  @DisplayName("should parse valid JSON source into JsonObject")
  void shouldParseSourceAsJsonObject() {
    // Given
    HitWrapper hit = new HitWrapper(validJson);

    // When
    JsonObject json = hit.getSourceAsJsonObject();

    // Then
    assertEquals("bar", json.get("foo").getAsString());
    assertEquals(42, json.get("num").getAsInt());
  }

  @Test
  @DisplayName("should allow construction with source only (null index/type/id)")
  void shouldSupportConstructorWithSourceOnly() {
    // Given
    HitWrapper hit = new HitWrapper(validJson);

    // Then
    assertNull(hit.getIndex());
    assertNull(hit.getType());
    assertNull(hit.getId());
    assertEquals(validJson, hit.getSourceAsString());
  }

  @Test
  @DisplayName("should throw JsonSyntaxException for invalid JSON")
  void shouldThrowForInvalidJsonSource() {
    // Given
    String invalidJson = "{not_json}";
    HitWrapper hit = new HitWrapper(invalidJson);

    // Then
    assertThrows(JsonSyntaxException.class, hit::getSourceAsJsonObject,
        "Invalid JSON string should throw JsonSyntaxException");
  }
}
