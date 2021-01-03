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

import org.apache.commons.dbcp2.DelegatingStatement;
import org.apache.commons.lang3.StringUtils;
import org.apache.hive.common.util.HiveVersionInfo;
import org.apache.hive.jdbc.HiveStatement;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.jdbc.JDBCInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class include hive specific stuff.
 * e.g. Display hive job execution info.
 *
 */
public class HiveUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(HiveUtils.class);
  private static final int DEFAULT_QUERY_PROGRESS_INTERVAL = 1000;

  private static final Pattern JOBURL_PATTERN =
          Pattern.compile(".*Tracking URL = (\\S*).*", Pattern.DOTALL);

  /**
   * Display hive job execution info, and progress info for hive >= 2.3
   *
   * @param stmt
   * @param context
   * @param displayLog
   */
  public static void startHiveMonitorThread(Statement stmt,
                                            InterpreterContext context,
                                            boolean displayLog,
                                            JDBCInterpreter jdbcInterpreter) {
    HiveStatement hiveStmt = (HiveStatement)
            ((DelegatingStatement) ((DelegatingStatement) stmt).getDelegate()).getDelegate();
    String hiveVersion = HiveVersionInfo.getVersion();
    ProgressBar progressBarTemp = null;
    if (isProgressBarSupported(hiveVersion)) {
      LOGGER.debug("ProgressBar is supported for hive version: " + hiveVersion);
      progressBarTemp = new ProgressBar();
    } else {
      LOGGER.debug("ProgressBar is not supported for hive version: " + hiveVersion);
    }
    // need to use final variable progressBar in thread, so need progressBarTemp here.
    final ProgressBar progressBar = progressBarTemp;
    final long timeoutThreshold = Long.parseLong(
            jdbcInterpreter.getProperty("zeppelin.jdbc.hive.timeout.threshold", "" + 60 * 1000));
    final long queryInterval = Long.parseLong(jdbcInterpreter.getProperty("zeppelin.jdbc.hive.monitor.query_interval",
            DEFAULT_QUERY_PROGRESS_INTERVAL + ""));
    Thread thread = new Thread(() -> {
      boolean jobLaunched = false;
      long jobLastActiveTime = System.currentTimeMillis();
      while (hiveStmt.hasMoreLogs() && !Thread.interrupted()) {
        try {
          Thread.sleep(queryInterval);
          List<String> logs = hiveStmt.getQueryLog();
          String logsOutput = StringUtils.join(logs, System.lineSeparator());
          LOGGER.debug("Hive job output: " + logsOutput);
          boolean displayLogProperty = context.getBooleanLocalProperty("displayLog", displayLog);
          if (!StringUtils.isBlank(logsOutput) && displayLogProperty) {
            context.out.write(logsOutput + "\n");
            context.out.flush();
          }
          if (!StringUtils.isBlank(logsOutput) && progressBar != null && displayLogProperty) {
            progressBar.operationLogShowedToUser();
          }
          Optional<String> jobURL = extractMRJobURL(logsOutput);
          if (jobURL.isPresent()) {
            Map<String, String> infos = new HashMap<>();
            infos.put("jobUrl", jobURL.get());
            infos.put("label", "HIVE JOB");
            infos.put("tooltip", "View in YARN WEB UI");
            infos.put("noteId", context.getNoteId());
            infos.put("paraId", context.getParagraphId());
            context.getIntpEventClient().onParaInfosReceived(infos);
          }
          if (logsOutput.contains("Launching Job")) {
            jobLaunched = true;
          }

          if (jobLaunched) {
            // Step 1. update jobLastActiveTime first
            // Step 2. Check whether it is timeout.
            if (StringUtils.isNotBlank(logsOutput)) {
              jobLastActiveTime = System.currentTimeMillis();
            } else if (progressBar.getBeelineInPlaceUpdateStream() != null &&
                    progressBar.getBeelineInPlaceUpdateStream().getLastUpdateTimestamp()
                            > jobLastActiveTime) {
              jobLastActiveTime = progressBar.getBeelineInPlaceUpdateStream()
                      .getLastUpdateTimestamp();
            }

            if (((System.currentTimeMillis() - jobLastActiveTime) > timeoutThreshold)) {
              String errorMessage = "Cancel this job as no more log is produced in the " +
                      "last " + timeoutThreshold / 1000 + " seconds, " +
                      "maybe it is because no yarn resources";
              LOGGER.warn(errorMessage);
              jdbcInterpreter.cancel(context, errorMessage);
              break;
            }
          }
        } catch (InterruptedException e) {
          LOGGER.warn("Hive monitor thread is interrupted", e);
          break;
        } catch (Exception e) {
          LOGGER.warn("Fail to monitor hive statement", e);
          break;
        } finally {
          try {
            // Sometimes, maybe hiveStmt was closed unnormally, hiveStmt.hasMoreLogs() will be true,
            // this loop cannot jump out, and exceptions thrown.
            // Add the below codes in case.
            if (hiveStmt.isClosed()){
              break;
            }
          } catch (SQLException e) {
            LOGGER.warn("hiveStmt closed unnormally", e);
          }
        }
      }
      LOGGER.info("HiveMonitor-Thread is finished");
    });
    thread.setName("HiveMonitor-Thread");
    thread.setDaemon(true);
    thread.start();
    LOGGER.info("Start HiveMonitor-Thread for sql: " + hiveStmt);

    if (progressBar != null) {
      // old: hiveStmt.setInPlaceUpdateStream(progressBar.getInPlaceUpdateStream(context.out));
      // Move codes into ProgressBar to delay NoClassDefFoundError of InPlaceUpdateStream
      // until ProgressBar instanced.
      // When hive < 2.3, ProgressBar will not be instanced, so it works well.
      progressBar.setInPlaceUpdateStream(hiveStmt, context.out);
    }
  }

  // Hive progress bar is supported from hive 2.3 (HIVE-16045)
  private static boolean isProgressBarSupported(String hiveVersion) {
    String[] tokens = hiveVersion.split("\\.");
    int majorVersion = Integer.parseInt(tokens[0]);
    int minorVersion = Integer.parseInt(tokens[1]);
    return majorVersion > 2 || ((majorVersion == 2) && minorVersion >= 3);
  }

  // extract hive job url from logs, it only works for MR engine.
  static Optional<String> extractMRJobURL(String log) {
    Matcher matcher = JOBURL_PATTERN.matcher(log);
    if (matcher.matches()) {
      String jobURL = matcher.group(1);
      return Optional.of(jobURL);
    }
    return Optional.empty();
  }
}
