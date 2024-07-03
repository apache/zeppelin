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

package org.apache.zeppelin.jdbc.kyuubi;

import org.apache.commons.dbcp2.DelegatingConnection;
import org.apache.commons.dbcp2.DelegatingStatement;
import org.apache.commons.lang3.StringUtils;
import org.apache.kyuubi.jdbc.hive.KyuubiConnection;
import org.apache.kyuubi.jdbc.hive.KyuubiStatement;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.jdbc.JDBCInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class include hive specific stuff.
 * e.g. Display hive job execution info.
 *
 */
public class KyuubiUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(KyuubiUtils.class);
  private static final int DEFAULT_QUERY_PROGRESS_INTERVAL = 1000;
  /**
   * Display hive job execution info
   */
  public static void startMonitorThread(Connection conn,
                                        Statement stmt,
                                        InterpreterContext context,
                                        boolean displayLog,
                                        JDBCInterpreter jdbcInterpreter) {
    KyuubiConnection kyuubiConn = (KyuubiConnection) ((DelegatingConnection<?>)
        ((DelegatingConnection<?>) conn).getDelegate()).getDelegate();
    KyuubiStatement kyuubiStmt = (KyuubiStatement)
            ((DelegatingStatement) ((DelegatingStatement) stmt).getDelegate()).getDelegate();
    // need to use final variable progressBar in thread, so need progressBarTemp here.
    final ProgressBar progressBar = new ProgressBar();
    final long queryInterval = Long.parseLong(
            jdbcInterpreter.getProperty("zeppelin.jdbc.kyuubi.monitor.query_interval",
                    DEFAULT_QUERY_PROGRESS_INTERVAL + ""));
    Thread thread = new Thread(() -> {
      String jobUrlTemplate = jdbcInterpreter.getProperty("zeppelin.jdbc.kyuubi.jobUrl.template");
      boolean jobUrlExtracted = false;

      try {
        while (kyuubiStmt.hasMoreLogs() && !kyuubiStmt.isClosed() && !Thread.interrupted()) {
          Thread.sleep(queryInterval);
          List<String> logs = kyuubiStmt.getExecLog();
          String logsOutput = StringUtils.join(logs, System.lineSeparator());
          LOGGER.debug("Kyuubi job output: {}", logsOutput);
          boolean displayLogProperty = context.getBooleanLocalProperty("displayLog", displayLog);
          if (displayLogProperty && !StringUtils.isBlank(logsOutput)) {
            context.out.write(logsOutput + "\n");
            context.out.flush();
            progressBar.operationLogShowedToUser();
          }

          if (!jobUrlExtracted) {
            String appId = kyuubiConn.getEngineId();
            String appUrl = kyuubiConn.getEngineUrl();
            String jobUrl = null;
            // prefer to use customized template, and fallback to Kyuubi returned engine url
            if (StringUtils.isNotBlank(jobUrlTemplate) && StringUtils.isNotBlank(appId)) {
              jobUrl = jobUrlTemplate.replace("{{applicationId}}", appId);
            } else if (StringUtils.isNotBlank(appUrl)) {
              jobUrl = appUrl;
            }
            if (jobUrl != null) {
              LOGGER.info("Detected Kyuubi engine URL: {}", jobUrl);
              Map<String, String> infos = new HashMap<>();
              infos.put("jobUrl", jobUrl);
              infos.put("label", "KYUUBI JOB");
              infos.put("tooltip", "View Application Web UI");
              infos.put("noteId", context.getNoteId());
              infos.put("paraId", context.getParagraphId());
              context.getIntpEventClient().onParaInfosReceived(infos);
              jobUrlExtracted = true;
            }
          }
        }
      } catch (InterruptedException e) {
        LOGGER.warn("Kyuubi monitor thread is interrupted", e);
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOGGER.warn("Fail to monitor KyuubiStatement", e);
      }

      LOGGER.info("KyuubiMonitor-Thread is finished");
    });
    thread.setName("KyuubiMonitor-Thread");
    thread.setDaemon(true);
    thread.start();
    LOGGER.info("Start KyuubiMonitor-Thread for sql: {}", kyuubiStmt);

    progressBar.setInPlaceUpdateStream(kyuubiStmt, context.out);
  }
}
