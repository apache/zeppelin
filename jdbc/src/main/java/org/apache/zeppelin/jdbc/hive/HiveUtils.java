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

  private static final Pattern APPID_PATTERN =
          Pattern.compile(".*with App id (\\S*)\\).*", Pattern.DOTALL);

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
      LOGGER.debug("ProgressBar is supported for hive version: {}", hiveVersion);
      progressBarTemp = new ProgressBar();
    } else {
      LOGGER.debug("ProgressBar is not supported for hive version: {}", hiveVersion);
    }
    // need to use final variable progressBar in thread, so need progressBarTemp here.
    final ProgressBar progressBar = progressBarTemp;
    final long queryInterval = Long.parseLong(
            jdbcInterpreter.getProperty("zeppelin.jdbc.hive.monitor.query_interval",
                    DEFAULT_QUERY_PROGRESS_INTERVAL + ""));
    Thread thread = new Thread(() -> {
      String jobUrlTemplate = jdbcInterpreter.getProperty("zeppelin.jdbc.hive.jobUrl.template");
      boolean jobUrlExtracted = false;

      try {
        while (hiveStmt.hasMoreLogs() && !hiveStmt.isClosed() && !Thread.interrupted()) {
          Thread.sleep(queryInterval);
          List<String> logs = hiveStmt.getQueryLog();
          String logsOutput = StringUtils.join(logs, System.lineSeparator());
          LOGGER.debug("Hive job output: {}", logsOutput);
          boolean displayLogProperty = context.getBooleanLocalProperty("displayLog", displayLog);
          if (!StringUtils.isBlank(logsOutput) && displayLogProperty) {
            context.out.write(logsOutput + "\n");
            context.out.flush();
          }
          if (!StringUtils.isBlank(logsOutput) && progressBar != null && displayLogProperty) {
            progressBar.operationLogShowedToUser();
          }

          if (!jobUrlExtracted) {
            jobUrlExtracted = extractJobURL(logsOutput, jobUrlTemplate, context);
          }
        }
      } catch (InterruptedException e) {
        LOGGER.warn("Hive monitor thread is interrupted", e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOGGER.warn("Fail to monitor hive statement", e);
      }

      LOGGER.info("HiveMonitor-Thread is finished");
    });
    thread.setName("HiveMonitor-Thread");
    thread.setDaemon(true);
    thread.start();
    LOGGER.info("Start HiveMonitor-Thread for sql: {}", hiveStmt);

    if (progressBar != null) {
      // old: hiveStmt.setInPlaceUpdateStream(progressBar.getInPlaceUpdateStream(context.out));
      // Move codes into ProgressBar to delay NoClassDefFoundError of InPlaceUpdateStream
      // until ProgressBar instanced.
      // When hive < 2.3, ProgressBar will not be instanced, so it works well.
      progressBar.setInPlaceUpdateStream(hiveStmt, context.out);
    }
  }

  /**
   * Extract hive job url in the following order:
   * 1. Extract job url from hive logs when hive use mr engine
   * 2. Extract job url from hive logs when hive use tez engine
   * 3. Extract job url via yarn tags when the above 2 methods doesn't work
   * @param logsOutput
   * @param jobUrlTemplate
   * @param context
   * @return
   */
  private static boolean extractJobURL(String logsOutput,
                                       String jobUrlTemplate,
                                       InterpreterContext context) {
    String jobUrl = null;
    Optional<String> mrJobURLOption = extractMRJobURL(logsOutput);
    Optional<String> tezAppIdOption = extractTezAppId(logsOutput);
    if (mrJobURLOption.isPresent()) {
      jobUrl = mrJobURLOption.get();
      LOGGER.info("Extract MR jobUrl: {} from logs", mrJobURLOption.get());
      if (StringUtils.isNotBlank(jobUrlTemplate)) {
        Optional<String> yarnAppId = extractYarnAppId(jobUrl);
        if (yarnAppId.isPresent()) {
          LOGGER.info("Extract yarn app id: {} from MR jobUrl", yarnAppId);
          jobUrl = jobUrlTemplate.replace("{{applicationId}}", yarnAppId.get());
        } else {
          LOGGER.warn("Unable to extract yarn App Id from jobURL: {}", jobUrl);
        }
      }
    } else if (tezAppIdOption.isPresent()) {
      String yarnAppId = tezAppIdOption.get();
      LOGGER.info("Extract Tez job yarn appId: {} from logs", yarnAppId);
      if (StringUtils.isNotBlank(jobUrlTemplate)) {
        jobUrl = jobUrlTemplate.replace("{{applicationId}}", yarnAppId);
      } else {
        LOGGER.warn("Unable to set JobUrl because zeppelin.jdbc.hive.jobUrl.template is not set");
        jobUrl = yarnAppId;
      }
    } else {
      if (isHadoopJarAvailable()) {
        String yarnAppId = YarnUtil.getYarnAppIdByTag(context.getParagraphId());
        if (StringUtils.isNotBlank(yarnAppId)) {
          LOGGER.info("Extract yarn appId: {} by tag", yarnAppId);
          if (StringUtils.isNotBlank(jobUrlTemplate)) {
            jobUrl = jobUrlTemplate.replace("{{applicationId}}", yarnAppId);
          } else {
            jobUrl = yarnAppId;
          }
        }
      } else {
        LOGGER.warn("Hadoop jar is not available, unable to use tags to fetch yarn app url");
      }
    }

    if (jobUrl != null) {
      LOGGER.info("Detected hive jobUrl: {}", jobUrl);
      Map<String, String> infos = new HashMap<>();
      infos.put("jobUrl", jobUrl);
      infos.put("label", "HIVE JOB");
      infos.put("tooltip", "View in YARN WEB UI");
      infos.put("noteId", context.getNoteId());
      infos.put("paraId", context.getParagraphId());
      context.getIntpEventClient().onParaInfosReceived(infos);
      return true;
    } else {
      return false;
    }
  }

  private static boolean isHadoopJarAvailable() {
    try {
      Class.forName("org.apache.hadoop.conf.Configuration");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
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

  // extract yarn appId from logs, it only works for Tez engine.
  static Optional<String> extractTezAppId(String log) {
    Matcher matcher = APPID_PATTERN.matcher(log);
    if (matcher.matches()) {
      String appId = matcher.group(1);
      return Optional.of(appId);
    }
    return Optional.empty();
  }

  // extract yarn appId from jobURL
  static Optional<String> extractYarnAppId(String jobURL) {
    int pos = jobURL.indexOf("application_");
    if (pos != -1) {
      return Optional.of(jobURL.substring(pos));
    }
    return Optional.empty();
  }
}
