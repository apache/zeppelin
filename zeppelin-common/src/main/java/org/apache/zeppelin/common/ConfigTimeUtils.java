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

import java.time.Duration;

/**
 * Utility for parsing time values from configuration strings.
 * Consolidates the logic previously duplicated in
 * {@code ZeppelinConfiguration.timeUnitToMill()} and
 * {@code TimeoutLifecycleManager.parseTimeValue()}.
 */
public final class ConfigTimeUtils {
  private ConfigTimeUtils() {}

  /**
   * Parses a configuration time string to milliseconds.
   *
   * <p>Supported formats:
   * <ul>
   *   <li>Plain integer: treated as milliseconds (e.g. {@code "60000"})</li>
   *   <li>{@code "ms"} suffix: parsed as milliseconds (e.g. {@code "500ms"})</li>
   *   <li>ISO 8601 duration component: H, M, S or combinations (e.g. {@code "1H"}, {@code "30M"},
   *       {@code "1H30M"})</li>
   * </ul>
   *
   * @param value the time string from a configuration property
   * @return the equivalent duration in milliseconds
   * @throws IllegalArgumentException if {@code value} is null or empty
   * @throws java.time.format.DateTimeParseException if the value is not a recognised format
   */
  public static long parseTimeValueToMillis(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Time value must not be null or empty");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      if (value.endsWith("ms")) {
        return Long.parseLong(value.substring(0, value.length() - 2));
      }
      return Duration.parse("PT" + value).toMillis();
    }
  }
}
