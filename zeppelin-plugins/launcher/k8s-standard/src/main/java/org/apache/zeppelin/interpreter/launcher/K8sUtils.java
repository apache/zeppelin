/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class K8sUtils {

  private static final long K = 1024;
  private static final long M = K * K;
  private static final long G = M * K;
  private static final long T = G * K;
  private static final long MINIMUM_OVERHEAD = 384;

  private static final Random rand = new Random();

  private K8sUtils() {
    // do nothing
  }

  public static String calculateMemoryWithDefaultOverhead(String memory) {
    long memoryMB = convertToBytes(memory) / M;
    long memoryOverheadMB = Math.max((long) (memoryMB * 0.1f), MINIMUM_OVERHEAD);
    return (memoryMB + memoryOverheadMB) + "Mi";
  }

  public static String calculateSparkMemory(String memory, String memoryOverhead) {
    long memoryMB = convertToBytes(memory) / M;
    long memoryOverheadMB = convertToBytes(memoryOverhead) / M;
    return (memoryMB + memoryOverheadMB) + "Mi";
  }

  private static long convertToBytes(String memory) {
    String lower = memory.toLowerCase().trim();
    Matcher m = Pattern.compile("([0-9]+)([a-z]+)?").matcher(lower);
    long value;
    String suffix;
    if (m.matches()) {
      value = Long.parseLong(m.group(1));
      suffix = m.group(2);
    } else {
      throw new NumberFormatException("Failed to parse string: " + memory);
    }

    long memoryAmountBytes = value;
    if (StringUtils.containsIgnoreCase(suffix, "k")) {
      memoryAmountBytes = value * K;
    } else if (StringUtils.containsIgnoreCase(suffix, "m")) {
      memoryAmountBytes = value * M;
    } else if (StringUtils.containsIgnoreCase(suffix, "g")) {
      memoryAmountBytes = value * G;
    } else if (StringUtils.containsIgnoreCase(suffix, "t")) {
      memoryAmountBytes = value * T;
    }
    if (0 > memoryAmountBytes) {
      throw new NumberFormatException("Conversion of " + memory + " exceeds Long.MAX_VALUE");
    }
    return memoryAmountBytes;
  }

  public static String getRandomPodSuffix(int length) {
    char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      char c = chars[rand.nextInt(chars.length)];
      sb.append(c);
    }
    return sb.toString();
  }
}
