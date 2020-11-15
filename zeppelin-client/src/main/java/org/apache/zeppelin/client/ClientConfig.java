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

package org.apache.zeppelin.client;

import org.apache.commons.lang3.StringUtils;

/**
 * Configuration of Zeppelin client, such as zeppelin server rest url and
 * query interval of polling note/paragraph result.
 */
public class ClientConfig {
  private String zeppelinRestUrl;
  private long queryInterval ;
  private String knoxSSOUrl;

  public ClientConfig(String zeppelinRestUrl) {
    this(zeppelinRestUrl, 1000);
  }

  public ClientConfig(String zeppelinRestUrl, long queryInterval) {
    this(zeppelinRestUrl, queryInterval, null);
  }

  public ClientConfig(String zeppelinRestUrl, long queryInterval, String knoxSSOUrl) {
    this.zeppelinRestUrl = removeTrailingSlash(zeppelinRestUrl);
    this.queryInterval = queryInterval;
    this.knoxSSOUrl = knoxSSOUrl;
  }

  private String removeTrailingSlash(String zeppelinRestUrl) {
    if (zeppelinRestUrl.endsWith("/")) {
      return zeppelinRestUrl.substring(0, zeppelinRestUrl.length() - 1);
    } else {
      return zeppelinRestUrl;
    }
  }

  public String getZeppelinRestUrl() {
    return zeppelinRestUrl;
  }

  public long getQueryInterval() {
    return queryInterval;
  }

  public boolean isUseKnox() {
    return StringUtils.isNotBlank(knoxSSOUrl);
  }

  public String getKnoxSSOUrl() {
    return knoxSSOUrl;
  }
}
