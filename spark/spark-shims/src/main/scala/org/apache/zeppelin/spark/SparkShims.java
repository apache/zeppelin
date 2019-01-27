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

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.util.VersionInfo;
import org.apache.hadoop.util.VersionUtil;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Properties;

/**
 * This is abstract class for anything that is api incompatible between spark1 and spark2. It will
 * load the correct version of SparkShims based on the version of Spark.
 */
public abstract class SparkShims {

  // the following lines for checking specific versions
  private static final String HADOOP_VERSION_2_6_6 = "2.6.6";
  private static final String HADOOP_VERSION_2_7_0 = "2.7.0";
  private static final String HADOOP_VERSION_2_7_4 = "2.7.4";
  private static final String HADOOP_VERSION_2_8_0 = "2.8.0";
  private static final String HADOOP_VERSION_2_8_2 = "2.8.2";
  private static final String HADOOP_VERSION_2_9_0 = "2.9.0";
  private static final String HADOOP_VERSION_3_0_0 = "3.0.0";
  private static final String HADOOP_VERSION_3_0_0_ALPHA4 = "3.0.0-alpha4";

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkShims.class);

  private static SparkShims sparkShims;

  protected Properties properties;

  public SparkShims(Properties properties) {
    this.properties = properties;
  }

  private static SparkShims loadShims(String sparkVersion, Properties properties)
      throws ReflectiveOperationException {
    Class<?> sparkShimsClass;
    if ("2".equals(sparkVersion)) {
      LOGGER.info("Initializing shims for Spark 2.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark2Shims");
    } else {
      LOGGER.info("Initializing shims for Spark 1.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark1Shims");
    }

    Constructor c = sparkShimsClass.getConstructor(Properties.class);
    return (SparkShims) c.newInstance(properties);
  }

  public static SparkShims getInstance(String sparkVersion, Properties properties) {
    if (sparkShims == null) {
      String sparkMajorVersion = getSparkMajorVersion(sparkVersion);
      try {
        sparkShims = loadShims(sparkMajorVersion, properties);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
    return sparkShims;
  }

  private static String getSparkMajorVersion(String sparkVersion) {
    return sparkVersion.startsWith("2") ? "2" : "1";
  }

  /**
   * This is due to SparkListener api change between spark1 and spark2. SparkListener is trait in
   * spark1 while it is abstract class in spark2.
   */
  public abstract void setupSparkListener(String master, String sparkWebUrl);

  protected String getNoteId(String jobgroupId) {
    int indexOf = jobgroupId.indexOf("-");
    int secondIndex = jobgroupId.indexOf("-", indexOf + 1);
    return jobgroupId.substring(indexOf + 1, secondIndex);
  }

  protected String getParagraphId(String jobgroupId) {
    int indexOf = jobgroupId.indexOf("-");
    int secondIndex = jobgroupId.indexOf("-", indexOf + 1);
    return jobgroupId.substring(secondIndex + 1, jobgroupId.length());
  }

  protected void buildSparkJobUrl(
      String master, String sparkWebUrl, int jobId, Properties jobProperties) {
    String jobGroupId = jobProperties.getProperty("spark.jobGroup.id");
    String jobUrl = sparkWebUrl + "/jobs/job?id=" + jobId;

    String version = VersionInfo.getVersion();
    if (master.toLowerCase().contains("yarn") && !supportYarn6615(version)) {
      jobUrl = sparkWebUrl + "/jobs";
    }

    String noteId = getNoteId(jobGroupId);
    String paragraphId = getParagraphId(jobGroupId);
    RemoteEventClientWrapper eventClient = BaseZeppelinContext.getEventClient();
    Map<String, String> infos = new java.util.HashMap<>();
    infos.put("jobUrl", jobUrl);
    infos.put("label", "SPARK JOB");
    infos.put("tooltip", "View in Spark web UI");
    if (eventClient != null) {
      eventClient.onParaInfosReceived(noteId, paragraphId, infos);
    }

  }

  /**
   * This is temporal patch for support old versions of Yarn which is not adopted YARN-6615
   *
   * @return true if YARN-6615 is patched, false otherwise
   */
  protected boolean supportYarn6615(String version) {
    return (VersionUtil.compareVersions(HADOOP_VERSION_2_6_6, version) <= 0
            && VersionUtil.compareVersions(HADOOP_VERSION_2_7_0, version) > 0)
        || (VersionUtil.compareVersions(HADOOP_VERSION_2_7_4, version) <= 0
            && VersionUtil.compareVersions(HADOOP_VERSION_2_8_0, version) > 0)
        || (VersionUtil.compareVersions(HADOOP_VERSION_2_8_2, version) <= 0
            && VersionUtil.compareVersions(HADOOP_VERSION_2_9_0, version) > 0)
        || (VersionUtil.compareVersions(HADOOP_VERSION_2_9_0, version) <= 0
            && VersionUtil.compareVersions(HADOOP_VERSION_3_0_0, version) > 0)
        || (VersionUtil.compareVersions(HADOOP_VERSION_3_0_0_ALPHA4, version) <= 0)
        || (VersionUtil.compareVersions(HADOOP_VERSION_3_0_0, version) <= 0);
  }

  @VisibleForTesting
  public static void reset() {
    sparkShims = null;
  }
}
