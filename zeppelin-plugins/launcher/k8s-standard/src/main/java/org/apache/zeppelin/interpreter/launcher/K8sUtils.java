package org.apache.zeppelin.interpreter.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class K8sUtils {

  private static final long K = 1024;
  private static final long M = K * K;
  private static final long G = M * K;
  private static final long T = G * K;
  private static final long MINIMUM_OVERHEAD = 384;

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
}
