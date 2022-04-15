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


import org.apache.hadoop.util.VersionInfo;
import org.apache.hadoop.util.VersionUtil;
import org.apache.zeppelin.interpreter.InterpreterContext;
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

  private static SparkShims loadShims(int sparkMajorVersion, Properties properties, Object entryPoint)
      throws Exception {
    Class<?> sparkShimsClass;
    if (sparkMajorVersion == 3) {
      LOGGER.info("Initializing shims for Spark 3.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark3Shims");
    } else if (sparkMajorVersion == 2) {
      LOGGER.info("Initializing shims for Spark 2.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark2Shims");
    } else {
      throw new Exception("Spark major version: '" + sparkMajorVersion + "' is not supported yet");
    }

    Constructor c = sparkShimsClass.getConstructor(Properties.class, Object.class);
    return (SparkShims) c.newInstance(properties, entryPoint);
  }

  /**
   *
   * @param sparkVersion
   * @param properties
   * @param entryPoint  entryPoint is SparkContext for Spark 1.x SparkSession for Spark 2.x
   * @return
   */
  public static SparkShims getInstance(String sparkVersion,
                                       Properties properties,
                                       Object entryPoint) throws Exception {
    if (sparkShims == null) {
      int sparkMajorVersion = SparkVersion.fromVersionString(sparkVersion).getMajorVersion();
      sparkShims = loadShims(sparkMajorVersion, properties, entryPoint);
    }
    return sparkShims;
  }

  /**
   * This is due to SparkListener api change between spark1 and spark2. SparkListener is trait in
   * spark1 while it is abstract class in spark2.
   */
  public abstract void setupSparkListener(String master,
                                          String sparkWebUrl,
                                          InterpreterContext context);

  public abstract String showDataFrame(Object obj, int maxResult, InterpreterContext context);

  public abstract Object getAsDataFrame(String value);

  protected void buildSparkJobUrl(String master,
                                  String sparkWebUrl,
                                  int jobId,
                                  Properties jobProperties,
                                  InterpreterContext context) {
    String jobUrl = null;
    if (sparkWebUrl.contains("{jobId}")) {
      jobUrl = sparkWebUrl.replace("{jobId}", jobId + "");
    } else {
      jobUrl = sparkWebUrl + "/jobs/job?id=" + jobId;
      String version = VersionInfo.getVersion();
      if (master.toLowerCase().contains("yarn") && !supportYarn6615(version)) {
        jobUrl = sparkWebUrl + "/jobs";
      }
    }

    String jobGroupId = jobProperties.getProperty("spark.jobGroup.id");

    Map<String, String> infos = new java.util.HashMap<String, String>();
    infos.put("jobUrl", jobUrl);
    infos.put("label", "SPARK JOB");
    infos.put("tooltip", "View in Spark web UI");
    infos.put("noteId", getNoteId(jobGroupId));
    infos.put("paraId", getParagraphId(jobGroupId));
    LOGGER.debug("Send spark job url: " + infos);
    context.getIntpEventClient().onParaInfosReceived(infos);
  }

  public static String getNoteId(String jobGroupId) {
    String[] tokens = jobGroupId.split("\\|");
    if (tokens.length != 4) {
      throw new RuntimeException("Invalid jobGroupId: " + jobGroupId);
    }
    return tokens[2];
  }

  public static String getParagraphId(String jobGroupId) {
    String[] tokens = jobGroupId.split("\\|");
    if (tokens.length != 4) {
      throw new RuntimeException("Invalid jobGroupId: " + jobGroupId);
    }
    return tokens[3];
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

  public static void reset() {
    sparkShims = null;
  }
}
