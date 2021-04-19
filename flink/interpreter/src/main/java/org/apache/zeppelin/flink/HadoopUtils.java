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

package org.apache.zeppelin.flink;

import com.google.common.io.Files;
import org.apache.flink.client.program.ClusterClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Move the hadoop related operation (depends on hadoop api) out of FlinkScalaInterpreter to this
 * class. The reason is in this way we don't need to load hadoop class for non-yarn mode. Otherwise
 * even in non-yarn mode, user still need hadoop shaded jar which doesn't make sense.
 */
public class HadoopUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(HadoopUtils.class);

  public static String getYarnAppTrackingUrl(ClusterClient clusterClient) throws IOException, YarnException {
    ApplicationId yarnAppId = (ApplicationId) clusterClient.getClusterId();
    return getYarnAppTrackingUrl(yarnAppId);
  }

  public static String getYarnAppTrackingUrl(String yarnAppIdStr) throws IOException, YarnException {
    ApplicationId yarnAppId = ConverterUtils.toApplicationId(yarnAppIdStr);
    return getYarnAppTrackingUrl(yarnAppId);
  }

  public static String getYarnAppTrackingUrl(ApplicationId yarnAppId) throws IOException, YarnException {
    return getYarnApplicationReport(yarnAppId).getTrackingUrl();
  }

  public static int getFlinkRestPort(String yarnAppId) throws IOException, YarnException {
    return getYarnApplicationReport(ConverterUtils.toApplicationId(yarnAppId)).getRpcPort();
  }

  private static ApplicationReport getYarnApplicationReport(ApplicationId yarnAppId)
          throws IOException, YarnException {
    YarnClient yarnClient = YarnClient.createYarnClient();
    YarnConfiguration yarnConf = new YarnConfiguration();
    // disable timeline service as we only query yarn app here.
    // Otherwise we may hit this kind of ERROR:
    // java.lang.ClassNotFoundException: com.sun.jersey.api.client.config.ClientConfig
    yarnConf.set("yarn.timeline-service.enabled", "false");
    yarnClient.init(yarnConf);
    yarnClient.start();
    return yarnClient.getApplicationReport(yarnAppId);
  }

  public static void cleanupStagingDirInternal(ClusterClient clusterClient) {
    try {
      ApplicationId appId = (ApplicationId) clusterClient.getClusterId();
      FileSystem fs = FileSystem.get(new Configuration());
      Path stagingDirPath = new Path(fs.getHomeDirectory(), ".flink/" + appId.toString());
      if (fs.delete(stagingDirPath, true)) {
        LOGGER.info("Deleted staging directory " + stagingDirPath);
      }
    } catch (IOException e){
        LOGGER.warn("Failed to cleanup staging dir", e);
    }
  }

  public static String downloadJar(String jarOnHdfs) throws IOException {
    File tmpDir = Files.createTempDir();
    FileSystem fs = FileSystem.get(new Configuration());
    Path sourcePath = fs.makeQualified(new Path(jarOnHdfs));
    if (!fs.exists(sourcePath)) {
      throw new IOException("jar file: " + jarOnHdfs + " doesn't exist.");
    }
    Path destPath = new Path(tmpDir.getAbsolutePath() + "/" + sourcePath.getName());
    fs.copyToLocalFile(sourcePath, destPath);
    return new File(destPath.toString()).getAbsolutePath();
  }
}
