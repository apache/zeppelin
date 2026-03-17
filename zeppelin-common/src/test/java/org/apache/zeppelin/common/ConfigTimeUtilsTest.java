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

package org.apache.zeppelin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTimeUtilsTest {

  @Test
  void plainNumberReturnedAsMillis() {
    assertEquals(60000L, ConfigTimeUtils.parseTimeValueToMillis("60000"));
  }

  @Test
  void msSuffixParsed() {
    assertEquals(500L, ConfigTimeUtils.parseTimeValueToMillis("500ms"));
  }

  @Test
  void hourUnitParsed() {
    assertEquals(3600000L, ConfigTimeUtils.parseTimeValueToMillis("1H"));
  }

  @Test
  void minuteUnitParsed() {
    assertEquals(1800000L, ConfigTimeUtils.parseTimeValueToMillis("30M"));
  }

  @Test
  void secondUnitParsed() {
    assertEquals(10000L, ConfigTimeUtils.parseTimeValueToMillis("10S"));
  }

  @Test
  void compoundDurationParsed() {
    assertEquals(5400000L, ConfigTimeUtils.parseTimeValueToMillis("1H30M"));
  }

  @Test
  void defaultCheckIntervalCompatible() {
    assertEquals(60000L, ConfigTimeUtils.parseTimeValueToMillis("60000"));
  }

  @Test
  void defaultThresholdCompatible() {
    assertEquals(3600000L, ConfigTimeUtils.parseTimeValueToMillis("3600000"));
  }

  @Test
  void nullInputThrows() {
    assertThrows(IllegalArgumentException.class,
      () -> ConfigTimeUtils.parseTimeValueToMillis(null));
  }

  @Test
  void emptyInputThrows() {
    assertThrows(IllegalArgumentException.class,
      () -> ConfigTimeUtils.parseTimeValueToMillis(""));
  }

  @Test
  void invalidFormatThrows() {
    assertThrows(Exception.class,
      () -> ConfigTimeUtils.parseTimeValueToMillis("abc"));
  }
}
