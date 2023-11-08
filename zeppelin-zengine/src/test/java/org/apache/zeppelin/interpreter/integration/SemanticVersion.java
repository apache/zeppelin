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

package org.apache.zeppelin.interpreter.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide reading comparing capability of semantic version which is used widely in Apache projects
 */
public class SemanticVersion {
  private static final Logger LOG = LoggerFactory.getLogger(SemanticVersion.class);

  public static SemanticVersion of(String versionString) {
    return new SemanticVersion(versionString);
  }

  private final String versionString;
  private int version;
  private int majorVersion;
  private int minorVersion;
  private int patchVersion;

  private SemanticVersion(String versionString) {
    this.versionString = versionString;

    try {
      int pos = versionString.indexOf('-');

      String numberPart = versionString;
      if (pos > 0) {
        numberPart = versionString.substring(0, pos);
      }

      String[] versions = numberPart.split("\\.");
      this.majorVersion = Integer.parseInt(versions[0]);
      this.minorVersion = Integer.parseInt(versions[1]);
      this.patchVersion = Integer.parseInt(versions[2]);
      // version is always 5 digits. (e.g. 2.0.0 -> 20000, 1.6.2 -> 10602)
      version = Integer.parseInt(String.format("%d%02d%02d", majorVersion, minorVersion, patchVersion));
    } catch (Exception e) {
      LOG.error("Can not recognize Spark version {}. Assume it's a future release", versionString, e);
      // assume it is future release
      version = 99999;
    }
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public int getPatchVersion() {
    return patchVersion;
  }

  @Override
  public String toString() {
    return versionString;
  }

  @Override
  public int hashCode() {
    return version;
  }

  @Override
  public boolean equals(Object versionToCompare) {
    return versionToCompare instanceof SemanticVersion
            && version == ((SemanticVersion) versionToCompare).version;
  }

  public boolean newerThan(SemanticVersion versionToCompare) {
    return version > versionToCompare.version;
  }

  public boolean equalsOrNewerThan(SemanticVersion versionToCompare) {
    return version >= versionToCompare.version;
  }

  public boolean olderThan(SemanticVersion versionToCompare) {
    return version < versionToCompare.version;
  }

  public boolean equalsOrOlderThan(SemanticVersion versionToCompare) {
    return version <= versionToCompare.version;
  }
}
