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

/**
 * Provide reading comparing capability of spark version returned from SparkContext.version()
 */
public enum SparkVersion {
  SPARK_1_0_0,
  SPARK_1_0_1,
  SPARK_1_1_0,
  SPARK_1_1_1,
  SPARK_1_2_0,
  SPARK_1_2_1,
  SPARK_1_2_2,
  SPARK_1_3_0,
  SPARK_1_3_1,
  SPARK_1_4_0,
  SPARK_1_4_1,
  SPARK_1_5_0;

  private int version;

  SparkVersion() {
    version = Integer.parseInt(name().substring("SPARK_".length()).replaceAll("_", ""));
  }

  public int toNumber() {
    return version;
  }

  public String toString() {
    return name().substring("SPARK_".length()).replaceAll("_", ".");
  }

  public static SparkVersion fromVersionString(String versionString) {
    for (SparkVersion v : values()) {
      if (v.toString().equals(versionString)) {
        return v;
      }
    }
    throw new IllegalArgumentException();
  }

  public boolean isPysparkSupported() {
    return this.newerThanEquals(SPARK_1_2_0);
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
