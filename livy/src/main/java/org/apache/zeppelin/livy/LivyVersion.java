package org.apache.zeppelin.livy;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide reading comparing capability of livy version
 */
public class LivyVersion {
  private static final Logger logger = LoggerFactory.getLogger(LivyVersion.class);

  protected static final LivyVersion LIVY_0_2_0 = LivyVersion.fromVersionString("0.2.0");
  protected static final LivyVersion LIVY_0_3_0 = LivyVersion.fromVersionString("0.3.0");
  protected static final LivyVersion LIVY_0_4_0 = LivyVersion.fromVersionString("0.4.0");

  private int version;
  private String versionString;

  LivyVersion(String versionString) {
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
      logger.error("Can not recognize Livy version " + versionString +
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

  public static LivyVersion fromVersionString(String versionString) {
    return new LivyVersion(versionString);
  }

  public boolean isCancelSupported() {
    return this.newerThanEquals(LIVY_0_3_0);
  }

  public boolean isGetProgressSupported() {
    return this.newerThanEquals(LIVY_0_4_0);
  }

  public boolean equals(Object versionToCompare) {
    return version == ((LivyVersion) versionToCompare).version;
  }

  public boolean newerThan(LivyVersion versionToCompare) {
    return version > versionToCompare.version;
  }

  public boolean newerThanEquals(LivyVersion versionToCompare) {
    return version >= versionToCompare.version;
  }

  public boolean olderThan(LivyVersion versionToCompare) {
    return version < versionToCompare.version;
  }

  public boolean olderThanEquals(LivyVersion versionToCompare) {
    return version <= versionToCompare.version;
  }
}
