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

package org.apache.zeppelin.elasticsearch.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchClientTypeTest {

  @ParameterizedTest
  @EnumSource(value = ElasticsearchClientType.class, names = {"HTTP", "HTTPS"})
  @DisplayName("should be marked as HTTP-based when client type is HTTP or HTTPS")
  void shouldBeHttpWhenTypeIsHttpOrHttps(ElasticsearchClientType type) {
    assertTrue(type.isHttp(), type + " should be marked as HTTP-based");
  }

  @ParameterizedTest
  @EnumSource(value = ElasticsearchClientType.class, names = {"TRANSPORT", "UNKNOWN"})
  @DisplayName("should NOT be marked as HTTP-based when client type is TRANSPORT or UNKNOWN")
  void shouldNotBeHttpWhenTypeIsTransportOrUnknown(ElasticsearchClientType type) {
    assertFalse(type.isHttp(), type + " should NOT be marked as HTTP-based");
  }

  @Test
  @DisplayName("should contain all expected enum values in order")
  void shouldContainAllEnumValuesInOrder() {
    ElasticsearchClientType[] expected = {
        ElasticsearchClientType.HTTP,
        ElasticsearchClientType.HTTPS,
        ElasticsearchClientType.TRANSPORT,
        ElasticsearchClientType.UNKNOWN
    };
    assertArrayEquals(expected, ElasticsearchClientType.values(), "Enum values should match " +
        "declared order");
  }
}
