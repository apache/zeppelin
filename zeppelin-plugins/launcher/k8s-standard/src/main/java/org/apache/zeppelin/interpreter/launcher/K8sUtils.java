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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.client.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

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

  /**
   * return the current namespace
   * @return the namespace in Config.KUBERNETES_NAMESPACE_PATH if it is running inside k8s, otherwise return null
   */
  public static String getCurrentK8sNamespace() {
    try {
      if (isRunningOnKubernetes()) {
        return readFile(Config.KUBERNETES_NAMESPACE_PATH, Charset.defaultCharset()).trim();
      } else {
        return null;
      }
    }
    catch (IOException e){
      return null;
    }
  }

  /**
   * Get the namespace of the interpreter.
   * Check Order: zeppelin.k8s.interpreter.namespace -> getCurrentK8sNamespace() -> zConf.getK8sNamepsace()
   * @param properties
   * @param zConf
   * @return the interpreter namespace
   * @throws IOException
   */
  public static String getInterpreterNamespace(Properties properties, ZeppelinConfiguration zConf) throws IOException {
    if(properties.containsKey("zeppelin.k8s.interpreter.namespace")){
      return properties.getProperty("zeppelin.k8s.interpreter.namespace");
    }

    if (isRunningOnKubernetes()) {
      return getCurrentK8sNamespace();
    } else {
      return zConf.getK8sNamepsace();
    }
  }

  /**
   * Check if i'm running inside of kubernetes or not.
   * It should return truth regardless of ZeppelinConfiguration.getRunMode().
   *
   * Normally, unless Zeppelin is running on Kubernetes, K8sStandardInterpreterLauncher shouldn't even have initialized.
   * However, when ZeppelinConfiguration.getRunMode() is force 'k8s', InterpreterSetting.getLauncherPlugin() will try
   * to use K8sStandardInterpreterLauncher. This is useful for development. It allows Zeppelin server running on your
   * IDE and creates your interpreters in Kubernetes. So any code changes on Zeppelin server or kubernetes yaml spec
   * can be applied without re-building docker image.
   * @return true, if running on K8s
   */
  public static boolean isRunningOnKubernetes() {
    return new File(Config.KUBERNETES_NAMESPACE_PATH).exists();
  }

  private static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }
}
