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
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.GenericRow;
import org.apache.spark.sql.types.StructType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.ResultMessages;
import org.apache.zeppelin.interpreter.SingleRowInterpreterResult;
import org.apache.zeppelin.tabledata.TableDataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This is abstract class for anything that is api incompatible between spark1 and spark2. It will
 * load the correct version of SparkUtils based on the version of Spark.
 */
public class SparkUtils {

  // the following lines for checking specific versions
  private static final String HADOOP_VERSION_2_6_6 = "2.6.6";
  private static final String HADOOP_VERSION_2_7_0 = "2.7.0";
  private static final String HADOOP_VERSION_2_7_4 = "2.7.4";
  private static final String HADOOP_VERSION_2_8_0 = "2.8.0";
  private static final String HADOOP_VERSION_2_8_2 = "2.8.2";
  private static final String HADOOP_VERSION_2_9_0 = "2.9.0";
  private static final String HADOOP_VERSION_3_0_0 = "3.0.0";
  private static final String HADOOP_VERSION_3_0_0_ALPHA4 = "3.0.0-alpha4";

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkUtils.class);

  protected SparkSession sparkSession;

  protected Properties properties;

  public SparkUtils(Properties properties, SparkSession sparkSession) {
    this.properties = properties;
    this.sparkSession = sparkSession;
  }

  public void setupSparkListener(final String master,
                                 final String sparkWebUrl,
                                 final InterpreterContext context) {
    SparkContext sc = SparkContext.getOrCreate();
    sc.addSparkListener(new SparkListener() {
      @Override
      public void onJobStart(SparkListenerJobStart jobStart) {

        if (sc.getConf().getBoolean("spark.ui.enabled", true) &&
            !Boolean.parseBoolean(properties.getProperty("zeppelin.spark.ui.hidden", "false"))) {
          buildSparkJobUrl(master, sparkWebUrl, jobStart.jobId(), jobStart.properties(), context);
        }
      }
    });
  }

  public String showDataFrame(Object obj, int maxResult, InterpreterContext context) {
    if (obj instanceof Dataset) {
      Dataset<Row> df = ((Dataset) obj).toDF();
      String[] columns = df.columns();
      // DDL will empty DataFrame
      if (columns.length == 0) {
        return "";
      }
      // fetch maxResult+1 rows so that we can check whether it is larger than zeppelin.spark.maxResult
      List<Row> rows = df.takeAsList(maxResult + 1);
      String template = context.getLocalProperties().get("template");
      if (!StringUtils.isBlank(template)) {
        if (rows.size() >= 1) {
          return new SingleRowInterpreterResult(sparkRowToList(rows.get(0)), template, context).toHtml();
        } else {
          return "";
        }
      }

      StringBuilder msg = new StringBuilder();
      msg.append("%table ");
      msg.append(StringUtils.join(TableDataUtils.normalizeColumns(columns), "\t"));
      msg.append("\n");
      boolean isLargerThanMaxResult = rows.size() > maxResult;
      if (isLargerThanMaxResult) {
        rows = rows.subList(0, maxResult);
      }
      for (Row row : rows) {
        for (int i = 0; i < row.size(); ++i) {
          msg.append(TableDataUtils.normalizeColumn(row.get(i)));
          if (i != row.size() -1) {
            msg.append("\t");
          }
        }
        msg.append("\n");
      }

      if (isLargerThanMaxResult) {
        msg.append("\n");
        msg.append(ResultMessages.getExceedsLimitRowsMessage(maxResult, "zeppelin.spark.maxResult"));
      }
      // append %text at the end, otherwise the following output will be put in table as well.
      msg.append("\n%text ");
      return msg.toString();
    } else {
      return obj.toString();
    }
  }

  private List sparkRowToList(Row row) {
    List list = new ArrayList();
    for (int i = 0; i< row.size(); i++) {
      list.add(row.get(i));
    }
    return list;
  }

  public Dataset<Row> getAsDataFrame(String value) {
    String[] lines = value.split("\\n");
    String head = lines[0];
    String[] columns = head.split("\t");
    StructType schema = new StructType();
    for (String column : columns) {
      schema = schema.add(column, "String");
    }

    List<Row> rows = new ArrayList<>();
    for (int i = 1; i < lines.length; ++i) {
      String[] tokens = lines[i].split("\t");
      Row row = new GenericRow(tokens);
      rows.add(row);
    }
    return sparkSession.createDataFrame(rows, schema);
  }

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

    Map<String, String> infos = new HashMap<>();
    infos.put("jobUrl", jobUrl);
    infos.put("label", "SPARK JOB");
    infos.put("tooltip", "View in Spark web UI");
    infos.put("noteId", getNoteId(jobGroupId));
    infos.put("paraId", getParagraphId(jobGroupId));
    LOGGER.debug("Send spark job url: {}", infos);
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
  protected static boolean supportYarn6615(String version) {
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
}
