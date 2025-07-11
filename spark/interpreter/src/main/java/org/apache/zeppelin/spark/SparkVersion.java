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
package org.apache.zeppelin.spark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide reading comparing capability of spark version returned from SparkContext.version()
 */
public class SparkVersion {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparkVersion.class);

  public static final SparkVersion SPARK_3_3_0 = SparkVersion.fromVersionString("3.3.0");

  public static final SparkVersion SPARK_3_5_0 = SparkVersion.fromVersionString("3.5.0");

  public static final SparkVersion SPARK_4_1_0 = SparkVersion.fromVersionString("4.1.0");

  public static final SparkVersion MIN_SUPPORTED_VERSION =  SPARK_3_3_0;
  public static final SparkVersion UNSUPPORTED_FUTURE_VERSION = SPARK_4_1_0;

  private int version;
  private int majorVersion;
  private int minorVersion;
  private int patchVersion;
  private String versionString;

  SparkVersion(String versionString) {
    this.versionString = versionString;

    try {
      int pos = versionString.indexOf('-');

      String numberPart = versionString;
      if (pos > 0) {
        numberPart = versionString.substring(0, pos);
      }

      String versions[] = numberPart.split("\\.");
      this.majorVersion = Integer.parseInt(versions[0]);
      this.minorVersion = Integer.parseInt(versions[1]);
      this.patchVersion = Integer.parseInt(versions[2]);
      // version is always 5 digits. (e.g. 2.0.0 -> 20000, 1.6.2 -> 10602)
      version = Integer.parseInt(String.format("%d%02d%02d", majorVersion, minorVersion, patchVersion));
    } catch (Exception e) {
      LOGGER.error("Can not recognize Spark version " + versionString +
          ". Assume it's a future release", e);

      // assume it is future release
      version = 99999;
    }
  }

  public int toNumber() {
    return version;
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public String toString() {
    return versionString;
  }

  public boolean isUnsupportedVersion() {
    return olderThan(MIN_SUPPORTED_VERSION) || newerThanEquals(UNSUPPORTED_FUTURE_VERSION);
  }

  public static SparkVersion fromVersionString(String versionString) {
    return new SparkVersion(versionString);
  }

  public boolean equals(Object versionToCompare) {
    return version == ((SparkVersion) versionToCompare).version;
  }

  public boolean newerThan(SparkVersion versionToCompare) {
    return version > versionToCompare.version;
  }

  public boolean newerThanEquals(SparkVersion versionToCompare) {
    return version >= versionToCompare.version;
  }

  public boolean newerThanEqualsPatchVersion(SparkVersion versionToCompare) {
    return majorVersion == versionToCompare.majorVersion &&
            minorVersion == versionToCompare.minorVersion &&
            patchVersion >= versionToCompare.patchVersion;
  }

  public boolean olderThan(SparkVersion versionToCompare) {
    return version < versionToCompare.version;
  }

  public boolean olderThanEquals(SparkVersion versionToCompare) {
    return version <= versionToCompare.version;
  }
}
