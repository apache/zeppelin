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

package org.apache.zeppelin.util;

import java.util.Random;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * TODO(moon) : add description.
 */
public class Util {
  private static final String PROJECT_PROPERTIES_VERSION_KEY = "version";
  private static final String GIT_PROPERTIES_COMMIT_ID_KEY = "git.commit.id.abbrev";
  private static final String GIT_PROPERTIES_COMMIT_TS_KEY = "git.commit.time";

  private static Properties projectProperties;
  private static Properties gitProperties;

  static {
    projectProperties = new Properties();
    gitProperties = new Properties();
    try {
      projectProperties.load(Util.class.getResourceAsStream("/project.properties"));
      gitProperties.load(Util.class.getResourceAsStream("/git.properties"));
    } catch (Exception e) {
      //Fail to read project.properties
    }
  }

  /**
   * Get Zeppelin version
   *
   * @return Current Zeppelin version
   */
  public static String getVersion() {
    return StringUtils.defaultIfEmpty(projectProperties.getProperty(PROJECT_PROPERTIES_VERSION_KEY),
            StringUtils.EMPTY);
  }

  /**
   * Get Zeppelin Git latest commit id
   *
   * @return Latest Zeppelin commit id
   */
  public static String getGitCommitId() {
    return StringUtils.defaultIfEmpty(gitProperties.getProperty(GIT_PROPERTIES_COMMIT_ID_KEY),
            StringUtils.EMPTY);
  }

  /**
   * Get Zeppelin Git latest commit timestamp
   *
   * @return Latest Zeppelin commit timestamp
   */
  public static String getGitTimestamp() {
    return StringUtils.defaultIfEmpty(gitProperties.getProperty(GIT_PROPERTIES_COMMIT_TS_KEY),
            StringUtils.EMPTY);
  }

  public static String getRandomString(int length) {
    char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    StringBuilder sb = new StringBuilder();
    Random random = new Random();
    for (int i = 0; i < length; i++) {
      char c = chars[random.nextInt(chars.length)];
      sb.append(c);
    }
    String randomStr = sb.toString();

    return randomStr;
  }
}
