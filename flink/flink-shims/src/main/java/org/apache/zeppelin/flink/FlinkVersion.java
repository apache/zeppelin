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
package org.apache.zeppelin.flink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FlinkVersion {
  private static final Logger logger = LoggerFactory.getLogger(FlinkVersion.class);

  private int majorVersion;
  private int minorVersion;
  private int patchVersion;
  private String versionString;

  FlinkVersion(String versionString) {
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
      if (versions.length == 3) {
        this.patchVersion = Integer.parseInt(versions[2]);
      }

    } catch (Exception e) {
      logger.error("Can not recognize Flink version " + versionString +
          ". Assume it's a future release", e);
    }
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public String toString() {
    return versionString;
  }

  public static FlinkVersion fromVersionString(String versionString) {
    return new FlinkVersion(versionString);
  }

  public boolean isFlink110() {
    return this.majorVersion == 1 && minorVersion == 10;
  }
}
