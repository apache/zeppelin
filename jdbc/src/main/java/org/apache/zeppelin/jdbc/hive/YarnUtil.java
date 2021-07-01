/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.zeppelin.jdbc.hive;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class YarnUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(YarnUtil.class);

  private static YarnClient yarnClient;

  static {
    try {
      yarnClient = YarnClient.createYarnClient();
      YarnConfiguration yarnConf = new YarnConfiguration();
      // disable timeline service as we only query yarn app here.
      // Otherwise we may hit this kind of ERROR:
      // java.lang.ClassNotFoundException: com.sun.jersey.api.client.config.ClientConfig
      yarnConf.set("yarn.timeline-service.enabled", "false");
      yarnClient.init(yarnConf);
      yarnClient.start();
    } catch (Exception e) {
      LOGGER.warn("Fail to start yarnClient", e);
    }
  }


  public static String getYarnAppIdByTag(String paragraphId) {
    if (yarnClient == null) {
      return null;
    }
    Set<String> applicationTypes = Sets.newHashSet("MAPREDUCE", "TEZ");
    EnumSet<YarnApplicationState> yarnStates =
            Sets.newEnumSet(Lists.newArrayList(YarnApplicationState.RUNNING),
            YarnApplicationState.class);

    try {
      List<ApplicationReport> apps =
              yarnClient.getApplications(applicationTypes, yarnStates);
      for (ApplicationReport appReport : apps) {
        if (appReport.getApplicationTags().contains(paragraphId)) {
          return appReport.getApplicationId().toString();
        }
      }
      return null;
    } catch (YarnException | IOException e) {
      LOGGER.warn("Fail to get yarn apps", e);
      return null;
    }
  }
}
