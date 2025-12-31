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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticsearchClientTypeBuilderTest {

  private ElasticsearchClientType buildFrom(String propertyValue) {
    return ElasticsearchClientTypeBuilder.withPropertyValue(propertyValue).build();
  }

  @ParameterizedTest(name = "property = \"{0}\" → expected = {1}")
  @CsvSource({
      "'', TRANSPORT",
      "null, TRANSPORT",
      "https, HTTPS",
      "hTtP, HTTP",
      "an_unknown_value, UNKNOWN"
  })
  @DisplayName("should resolve correct ElasticsearchClientType from property string")
  void shouldResolveClientTypeFromProperty(String property, ElasticsearchClientType expected) {
    String resolved = "null".equals(property) ? null : property;
    assertEquals(expected, buildFrom(resolved),
        String.format("Expected %s for input '%s'", expected, property));
  }
}
