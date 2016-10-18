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

package org.apache.zeppelin.pig;


import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pig.PigRunner;
import org.apache.pig.backend.hadoop.executionengine.tez.TezExecType;
import org.apache.pig.tools.pigstats.InputStats;
import org.apache.pig.tools.pigstats.JobStats;
import org.apache.pig.tools.pigstats.OutputStats;
import org.apache.pig.tools.pigstats.PigStats;
import org.apache.pig.tools.pigstats.mapreduce.MRJobStats;
import org.apache.pig.tools.pigstats.mapreduce.SimplePigStats;
import org.apache.pig.tools.pigstats.tez.TezDAGStats;
import org.apache.pig.tools.pigstats.tez.TezPigScriptStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class PigUtils {

  private static Logger LOGGER = LoggerFactory.getLogger(PigUtils.class);

  protected static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public static File createTempPigScript(String content) throws IOException {
    File tmpFile = File.createTempFile("zeppelin", "pig");
    LOGGER.debug("Create pig script file:" + tmpFile.getAbsolutePath());
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();
    return tmpFile.getAbsoluteFile();
  }

  public static File createTempPigScript(List<String> lines) throws IOException {
    return createTempPigScript(StringUtils.join(lines, "\n"));
  }

  public static String extactJobStats(PigStats stats) {
    if (stats instanceof SimplePigStats) {
      return extractFromSimplePigStats((SimplePigStats) stats);
    } else if (stats instanceof TezPigScriptStats) {
      return extractFromTezPigStats((TezPigScriptStats) stats);
    } else {
      throw new RuntimeException("Unrecognized stats type:" + stats.getClass().getSimpleName());
    }
  }

  public static String extractFromSimplePigStats(SimplePigStats stats) {

    try {
      Field userIdField = PigStats.class.getDeclaredField("userId");
      userIdField.setAccessible(true);
      String userId = (String) (userIdField.get(stats));
      Field startTimeField = PigStats.class.getDeclaredField("startTime");
      startTimeField.setAccessible(true);
      long startTime = (Long) (startTimeField.get(stats));
      Field endTimeField = PigStats.class.getDeclaredField("endTime");
      endTimeField.setAccessible(true);
      long endTime = (Long) (endTimeField.get(stats));

      if (stats.getReturnCode() == PigRunner.ReturnCode.UNKNOWN) {
        LOGGER.warn("unknown return code, can't display the results");
        return null;
      }
      if (stats.getPigContext() == null) {
        LOGGER.warn("unknown exec type, don't display the results");
        return null;
      }

      SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
      StringBuilder sb = new StringBuilder();
      sb.append("\nHadoopVersion\tPigVersion\tUserId\tStartedAt\tFinishedAt\tFeatures\n");
      sb.append(stats.getHadoopVersion()).append("\t").append(stats.getPigVersion()).append("\t")
              .append(userId).append("\t")
              .append(sdf.format(new Date(startTime))).append("\t")
              .append(sdf.format(new Date(endTime))).append("\t")
              .append(stats.getFeatures()).append("\n");
      sb.append("\n");
      if (stats.getReturnCode() == PigRunner.ReturnCode.SUCCESS) {
        sb.append("Success!\n");
      } else if (stats.getReturnCode() == PigRunner.ReturnCode.PARTIAL_FAILURE) {
        sb.append("Some jobs have failed! Stop running all dependent jobs\n");
      } else {
        sb.append("Failed!\n");
      }
      sb.append("\n");

      Field jobPlanField = PigStats.class.getDeclaredField("jobPlan");
      jobPlanField.setAccessible(true);
      PigStats.JobGraph jobPlan = (PigStats.JobGraph) jobPlanField.get(stats);

      if (stats.getReturnCode() == PigRunner.ReturnCode.SUCCESS
              || stats.getReturnCode() == PigRunner.ReturnCode.PARTIAL_FAILURE) {
        sb.append("Job Stats (time in seconds):\n");
        sb.append(MRJobStats.SUCCESS_HEADER).append("\n");
        List<JobStats> arr = jobPlan.getSuccessfulJobs();
        for (JobStats js : arr) {
          sb.append(js.getDisplayString());
        }
        sb.append("\n");
      }
      if (stats.getReturnCode() == PigRunner.ReturnCode.FAILURE
              || stats.getReturnCode() == PigRunner.ReturnCode.PARTIAL_FAILURE) {
        sb.append("Failed Jobs:\n");
        sb.append(MRJobStats.FAILURE_HEADER).append("\n");
        List<JobStats> arr = jobPlan.getFailedJobs();
        for (JobStats js : arr) {
          sb.append(js.getDisplayString());
        }
        sb.append("\n");
      }
      sb.append("Input(s):\n");
      for (InputStats is : stats.getInputStats()) {
        sb.append(is.getDisplayString());
      }
      sb.append("\n");
      sb.append("Output(s):\n");
      for (OutputStats ds : stats.getOutputStats()) {
        sb.append(ds.getDisplayString());
      }

      sb.append("\nCounters:\n");
      sb.append("Total records written : " + stats.getRecordWritten()).append("\n");
      sb.append("Total bytes written : " + stats.getBytesWritten()).append("\n");
      sb.append("Spillable Memory Manager spill count : "
              + stats.getSMMSpillCount()).append("\n");
      sb.append("Total bags proactively spilled: "
              + stats.getProactiveSpillCountObjects()).append("\n");
      sb.append("Total records proactively spilled: "
              + stats.getProactiveSpillCountRecords()).append("\n");
      sb.append("\nJob DAG:\n").append(jobPlan.toString());

      return "Script Statistics: \n" + sb.toString();
    } catch (Exception e) {
      LOGGER.error("Can not extract message from SimplePigStats", e);
      return "Can not extract message from SimpelPigStats," + ExceptionUtils.getStackTrace(e);
    }
  }

  private static String extractFromTezPigStats(TezPigScriptStats stats) {

    try {
      Field userIdField = PigStats.class.getDeclaredField("userId");
      userIdField.setAccessible(true);
      String userId = (String) (userIdField.get(stats));
      Field startTimeField = PigStats.class.getDeclaredField("startTime");
      startTimeField.setAccessible(true);
      long startTime = (Long) (startTimeField.get(stats));
      Field endTimeField = PigStats.class.getDeclaredField("endTime");
      endTimeField.setAccessible(true);
      long endTime = (Long) (endTimeField.get(stats));

      SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
      StringBuilder sb = new StringBuilder();
      sb.append("\n");
      sb.append(String.format("%1$20s: %2$-100s%n", "HadoopVersion", stats.getHadoopVersion()));
      sb.append(String.format("%1$20s: %2$-100s%n", "PigVersion", stats.getPigVersion()));
      sb.append(String.format("%1$20s: %2$-100s%n", "TezVersion", TezExecType.getTezVersion()));
      sb.append(String.format("%1$20s: %2$-100s%n", "UserId", userId));
      sb.append(String.format("%1$20s: %2$-100s%n", "FileName", stats.getFileName()));
      sb.append(String.format("%1$20s: %2$-100s%n", "StartedAt", sdf.format(new Date(startTime))));
      sb.append(String.format("%1$20s: %2$-100s%n", "FinishedAt", sdf.format(new Date(endTime))));
      sb.append(String.format("%1$20s: %2$-100s%n", "Features", stats.getFeatures()));
      sb.append("\n");
      if (stats.getReturnCode() == PigRunner.ReturnCode.SUCCESS) {
        sb.append("Success!\n");
      } else if (stats.getReturnCode() == PigRunner.ReturnCode.PARTIAL_FAILURE) {
        sb.append("Some tasks have failed! Stop running all dependent tasks\n");
      } else {
        sb.append("Failed!\n");
      }
      sb.append("\n");

      // Print diagnostic info in case of failure
      if (stats.getReturnCode() == PigRunner.ReturnCode.FAILURE
              || stats.getReturnCode() == PigRunner.ReturnCode.PARTIAL_FAILURE) {
        if (stats.getErrorMessage() != null) {
          String[] lines = stats.getErrorMessage().split("\n");
          for (int i = 0; i < lines.length; i++) {
            String s = lines[i].trim();
            if (i == 0 || !org.apache.commons.lang.StringUtils.isEmpty(s)) {
              sb.append(String.format("%1$20s: %2$-100s%n", i == 0 ? "ErrorMessage" : "", s));
            }
          }
          sb.append("\n");
        }
      }

      Field tezDAGStatsMapField = TezPigScriptStats.class.getDeclaredField("tezDAGStatsMap");
      tezDAGStatsMapField.setAccessible(true);
      Map<String, TezDAGStats> tezDAGStatsMap =
              (Map<String, TezDAGStats>) tezDAGStatsMapField.get(stats);
      int count = 0;
      for (TezDAGStats dagStats : tezDAGStatsMap.values()) {
        sb.append("\n");
        sb.append("DAG " + count++ + ":\n");
        sb.append(dagStats.getDisplayString());
        sb.append("\n");
      }

      sb.append("Input(s):\n");
      for (InputStats is : stats.getInputStats()) {
        sb.append(is.getDisplayString().trim()).append("\n");
      }
      sb.append("\n");
      sb.append("Output(s):\n");
      for (OutputStats os : stats.getOutputStats()) {
        sb.append(os.getDisplayString().trim()).append("\n");
      }
      return "Script Statistics:\n" + sb.toString();
    } catch (Exception e) {
      LOGGER.error("Can not extract message from SimplePigStats", e);
      return "Can not extract message from SimpelPigStats," + ExceptionUtils.getStackTrace(e);
    }
  }

  public static List<String> extractJobIds(PigStats stat) {
    if (stat instanceof SimplePigStats) {
      return extractJobIdsFromSimplePigStats((SimplePigStats) stat);
    } else if (stat instanceof TezPigScriptStats) {
      return extractJobIdsFromTezPigStats((TezPigScriptStats) stat);
    } else {
      throw new RuntimeException("Unrecognized stats type:" + stat.getClass().getSimpleName());
    }
  }

  public static List<String> extractJobIdsFromSimplePigStats(SimplePigStats stat) {
    List<String> jobIds = new ArrayList<>();
    try {
      Field jobPlanField = PigStats.class.getDeclaredField("jobPlan");
      jobPlanField.setAccessible(true);
      PigStats.JobGraph jobPlan = (PigStats.JobGraph) jobPlanField.get(stat);
      List<JobStats> arr = jobPlan.getJobList();
      for (JobStats js : arr) {
        jobIds.add(js.getJobId());
      }
      return jobIds;
    } catch (Exception e) {
      LOGGER.error("Can not extract jobIds from SimpelPigStats", e);
      throw new RuntimeException("Can not extract jobIds from SimpelPigStats", e);
    }
  }

  public static List<String> extractJobIdsFromTezPigStats(TezPigScriptStats stat) {
    List<String> jobIds = new ArrayList<>();
    try {
      Field tezDAGStatsMapField = TezPigScriptStats.class.getDeclaredField("tezDAGStatsMap");
      tezDAGStatsMapField.setAccessible(true);
      Map<String, TezDAGStats> tezDAGStatsMap =
              (Map<String, TezDAGStats>) tezDAGStatsMapField.get(stat);
      for (TezDAGStats dagStats : tezDAGStatsMap.values()) {
        LOGGER.debug("Tez JobId:" + dagStats.getJobId());
        jobIds.add(dagStats.getJobId());
      }
      return jobIds;
    } catch (Exception e) {
      LOGGER.error("Can not extract jobIds from TezPigScriptStats", e);
      throw new RuntimeException("Can not extract jobIds from TezPigScriptStats", e);
    }
  }
}
