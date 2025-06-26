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

import org.apache.zeppelin.elasticsearch.action.AggWrapper.AggregationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AggWrapperTest {

  @Test
  @DisplayName("should store type and result correctly when type is SIMPLE")
  void shouldStoreSimpleAggregationCorrectly() {
    // Given
    AggregationType type = AggregationType.SIMPLE;
    String result = "{\"value\":42}";

    // When
    AggWrapper wrapper = new AggWrapper(type, result);

    // Then
    assertEquals(type, wrapper.getType());
    assertEquals(result, wrapper.getResult());
  }

  @Test
  @DisplayName("should store type and result correctly when type is MULTI_BUCKETS")
  void shouldStoreMultiBucketsAggregationCorrectly() {
    // Given
    String result = "[{\"key\":\"a\"},{\"key\":\"b\"}]";

    // When
    AggWrapper wrapper = new AggWrapper(AggregationType.MULTI_BUCKETS, result);

    // Then
    assertEquals(AggregationType.MULTI_BUCKETS, wrapper.getType());
    assertEquals(result, wrapper.getResult());
  }

  @Test
  @DisplayName("AggregationType should contain SIMPLE and MULTI_BUCKETS in order")
  void shouldContainExpectedAggregationTypes() {
    // Given
    AggregationType[] expected = {
        AggregationType.SIMPLE,
        AggregationType.MULTI_BUCKETS
    };

    // Then
    assertArrayEquals(expected, AggregationType.values());
  }
}
