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


import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Properties;

/**
 * This is abstract class for anything that is api incompatible between spark1 and spark2.
 * It will load the correct version of SparkShims based on the version of Spark.
 */
public abstract class SparkShims {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkShims.class);

  private static SparkShims sparkShims;

  private static SparkShims loadShims(String sparkVersion) throws ReflectiveOperationException {
    Class<?> sparkShimsClass;
    if ("2".equals(sparkVersion)) {
      LOGGER.info("Initializing shims for Spark 2.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark2Shims");
    } else {
      LOGGER.info("Initializing shims for Spark 1.x");
      sparkShimsClass = Class.forName("org.apache.zeppelin.spark.Spark1Shims");
    }

    Constructor c = sparkShimsClass.getConstructor();
    return (SparkShims) c.newInstance();
  }

  public static SparkShims getInstance(String sparkVersion) {
    if (sparkShims == null) {
      String sparkMajorVersion = getSparkMajorVersion(sparkVersion);
      try {
        sparkShims = loadShims(sparkMajorVersion);
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
   * This is due to SparkListener api change between spark1 and spark2.
   * SparkListener is trait in spark1 while it is abstract class in spark2.
   */
  public abstract void setupSparkListener(String sparkWebUrl);


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

  protected void buildSparkJobUrl(String sparkWebUrl, int jobId, Properties jobProperties) {
    String jobGroupId = jobProperties.getProperty("spark.jobGroup.id");
    String uiEnabled = jobProperties.getProperty("spark.ui.enabled");
    String jobUrl = sparkWebUrl + "/jobs/job?id=" + jobId;
    String noteId = getNoteId(jobGroupId);
    String paragraphId = getParagraphId(jobGroupId);
    // Button visible if Spark UI property not set, set as invalid boolean or true
    boolean showSparkUI =
        uiEnabled == null || !uiEnabled.trim().toLowerCase().equals("false");
    if (showSparkUI && jobUrl != null) {
      RemoteEventClientWrapper eventClient = BaseZeppelinContext.getEventClient();
      Map<String, String> infos = new java.util.HashMap<String, String>();
      infos.put("jobUrl", jobUrl);
      infos.put("label", "SPARK JOB");
      infos.put("tooltip", "View in Spark web UI");
      if (eventClient != null) {
        eventClient.onParaInfosReceived(noteId, paragraphId, infos);
      }
    }
  }
}
