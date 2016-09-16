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
  Logger logger = LoggerFactory.getLogger(SparkVersion.class);

  public static final SparkVersion SPARK_1_0_0 = SparkVersion.fromVersionString("1.0.0");
  public static final SparkVersion SPARK_1_1_0 = SparkVersion.fromVersionString("1.1.0");
  public static final SparkVersion SPARK_1_2_0 = SparkVersion.fromVersionString("1.2.0");
  public static final SparkVersion SPARK_1_3_0 = SparkVersion.fromVersionString("1.3.0");
  public static final SparkVersion SPARK_1_4_0 = SparkVersion.fromVersionString("1.4.0");
  public static final SparkVersion SPARK_1_5_0 = SparkVersion.fromVersionString("1.5.0");
  public static final SparkVersion SPARK_1_6_0 = SparkVersion.fromVersionString("1.6.0");

  public static final SparkVersion SPARK_2_0_0 = SparkVersion.fromVersionString("2.0.0");
  public static final SparkVersion SPARK_2_1_0 = SparkVersion.fromVersionString("2.1.0");

  public static final SparkVersion MIN_SUPPORTED_VERSION =  SPARK_1_0_0;
  public static final SparkVersion UNSUPPORTED_FUTURE_VERSION = SPARK_2_1_0;

  private int version;
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
      int major = Integer.parseInt(versions[0]);
      int minor = Integer.parseInt(versions[1]);
      int patch = Integer.parseInt(versions[2]);
      // version is always 5 digits. (e.g. 2.0.0 -> 20000, 1.6.2 -> 10602)
      version = Integer.parseInt(String.format("%d%02d%02d", major, minor, patch));
    } catch (Exception e) {
      logger.error("Can not recognize Spark version " + versionString +
          ". Assume it's a future release", e);

      // assume it is future release
      version = 99999;
    }
  }

  public int toNumber() {
    return version;
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

  public boolean isPysparkSupported() {
    return this.newerThanEquals(SPARK_1_2_0);
  }

  public boolean isSparkRSupported() {
    return this.newerThanEquals(SPARK_1_4_0);
  }

  public boolean hasDataFrame() {
    return this.newerThanEquals(SPARK_1_4_0);
  }

  public boolean getProgress1_0() {
    return this.olderThan(SPARK_1_1_0);
  }

  public boolean oldLoadFilesMethodName() {
    return this.olderThan(SPARK_1_3_0);
  }

  public boolean oldSqlContextImplicits() {
    return this.olderThan(SPARK_1_3_0);
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

  public boolean olderThan(SparkVersion versionToCompare) {
    return version < versionToCompare.version;
  }

  public boolean olderThanEquals(SparkVersion versionToCompare) {
    return version <= versionToCompare.version;
  }
}
