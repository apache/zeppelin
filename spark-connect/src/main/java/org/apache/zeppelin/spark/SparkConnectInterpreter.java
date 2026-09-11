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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spark Connect interpreter for Zeppelin.
 * Connects to a remote Spark cluster via the Spark Connect gRPC protocol.
 *
 * Session &amp; Concurrency Model:
 * <ul>
 *   <li>Max Spark Connect sessions per user is capped
 *       ({@code zeppelin.spark.connect.maxSessionsPerUser}, default 5).
 *       Each interpreter instance creates one session; Zeppelin's binding mode
 *       (per-user / per-note / scoped / isolated) controls how many instances exist.</li>
 *   <li>Notebooks are unlimited -- users may open as many as they like.</li>
 *   <li>Within a single notebook only one query executes at a time
 *       (per-notebook fair lock via {@link NotebookLockManager}).</li>
 * </ul>
 */
public class SparkConnectInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkConnectInterpreter.class);

  /** user -> number of active SparkSession instances owned by that user. */
  private static final ConcurrentHashMap<String, Integer> userSessionCount =
      new ConcurrentHashMap<>();

  private static final int DEFAULT_MAX_SESSIONS_PER_USER = 5;

  private SparkSession sparkSession;
  private SqlSplitter sqlSplitter;
  private int maxResult;
  private String currentUser;
  private volatile boolean sessionSlotAcquired = false;

  public SparkConnectInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public synchronized void open() throws InterpreterException {
    if (sparkSession != null) {
      LOGGER.warn("open() called but sparkSession is already active — skipping. " +
          "Call close() first to restart the interpreter.");
      return;
    }

    try {
      currentUser = getUserName();
      if (StringUtils.isBlank(currentUser)) {
        currentUser = "anonymous";
      }

      int maxSessions = Integer.parseInt(
          getProperty("zeppelin.spark.connect.maxSessionsPerUser",
              String.valueOf(DEFAULT_MAX_SESSIONS_PER_USER)));

      if (!acquireSessionSlot(currentUser, maxSessions)) {
        throw new InterpreterException(
            String.format("User '%s' already has %d active Spark Connect sessions " +
                "(max %d). Close an existing interpreter before opening a new one.",
                currentUser, maxSessions, maxSessions));
      }
      sessionSlotAcquired = true;

      LOGGER.info("Opening SparkConnectInterpreter for user: {} (session slot {}/{})",
          currentUser, userSessionCount.getOrDefault(currentUser, 0), maxSessions);

      String remoteUrl = SparkConnectUtils.buildConnectionString(getProperties(), currentUser);
      LOGGER.info("Connecting to Spark Connect server at: {}",
          remoteUrl.replaceAll("token=[^;]*", "token=[REDACTED]")
                   .replaceAll("user_id=[^;]*", "user_id=[REDACTED]"));

      // Clear the thread-local active session on the Spark Connect client so that
      // getOrCreate() creates a fresh remote session rather than reusing the previous
      // closed one. We intentionally do NOT call clearDefaultSession() because that
      // is a JVM-global operation and would disrupt other interpreter instances that
      // are concurrently active in the same process.
      try {
        SparkSession.clearActiveSession();
        LOGGER.info("Cleared thread-local active Spark session (safe for multi-interpreter)");
      } catch (Exception e) {
        LOGGER.warn("Could not clear active Spark session (non-fatal): {}", e.getMessage());
      }

      SparkSession.Builder builder = SparkSession.builder().remote(remoteUrl);

      String appName = getProperty("spark.app.name", "Zeppelin Spark Connect");
      if (StringUtils.isNotBlank(appName)) {
        builder.appName(appName);
      }

      String grpcMaxMsgSize = getProperty(
          "spark.connect.grpc.maxMessageSize", "134217728");
      builder.config("spark.connect.grpc.maxMessageSize", grpcMaxMsgSize);

      for (Object key : getProperties().keySet()) {
        String keyStr = key.toString();
        String value = getProperties().getProperty(keyStr);
        if (StringUtils.isNotBlank(value)
            && keyStr.startsWith("spark.")
            && !keyStr.equals("spark.remote")
            && !keyStr.equals("spark.connect.token")
            && !keyStr.equals("spark.connect.use_ssl")
            && !keyStr.equals("spark.app.name")
            && !keyStr.equals("spark.connect.grpc.maxMessageSize")) {
          builder.config(keyStr, value);
        }
      }

      sparkSession = builder.getOrCreate();
      LOGGER.info("Spark Connect session established for user: {}", currentUser);

      maxResult = Integer.parseInt(getProperty("zeppelin.spark.maxResult", "1000"));
      sqlSplitter = new SqlSplitter();
    } catch (InterpreterException ie) {
      throw ie;
    } catch (Exception e) {
      if (sessionSlotAcquired) {
        releaseSessionSlot(currentUser);
        sessionSlotAcquired = false;
      }
      LOGGER.error("Failed to connect to Spark Connect server", e);
      throw new InterpreterException("Failed to connect to Spark Connect server: "
          + e.getMessage(), e);
    }
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Closing SparkConnectInterpreter for user: {} (sparkSession={})",
        currentUser, sparkSession != null ? "active" : "null");
    if (sparkSession != null) {
      try {
        sparkSession.close();
        LOGGER.info("Spark Connect session closed for user: {}", currentUser);
      } catch (Exception e) {
        LOGGER.warn("Error closing Spark Connect session", e);
      } finally {
        sparkSession = null;
      }
    } else {
      LOGGER.info("close() called but no active sparkSession — nothing to tear down");
    }
    if (sessionSlotAcquired) {
      releaseSessionSlot(currentUser);
      sessionSlotAcquired = false;
    }
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  public InterpreterResult internalInterpret(String st, InterpreterContext context)
      throws InterpreterException {
    if (sparkSession == null) {
      return new InterpreterResult(Code.ERROR,
          "Spark Connect session is not initialized. Check connection settings.");
    }

    String noteId = context.getNoteId();
    if (StringUtils.isBlank(noteId)) {
      return new InterpreterResult(Code.ERROR,
          "Note ID is missing from interpreter context.");
    }

    // Per-notebook lock: only one query at a time inside a notebook
    ReentrantLock notebookLock = NotebookLockManager.getNotebookLock(noteId);
    notebookLock.lock();
    try {
      List<String> sqls = sqlSplitter.splitSql(st);
      int limit = Integer.parseInt(context.getLocalProperties().getOrDefault("limit",
          String.valueOf(maxResult)));

      boolean useStreaming = Boolean.parseBoolean(
          getProperty("zeppelin.spark.connect.streamResults", "false"));

      String curSql = null;
      try {
        for (String sql : sqls) {
          curSql = sql;
          if (StringUtils.isBlank(sql)) {
            continue;
          }
          Dataset<Row> df = sparkSession.sql(sql);
          if (useStreaming) {
            SparkConnectUtils.streamDataFrame(df, limit, context.out);
          } else {
            String result = SparkConnectUtils.showDataFrame(df, limit);
            context.out.write(result);
          }
        }
        context.out.flush();
      } catch (Exception e) {
        return handleSqlException(e, curSql, context);
      }

      return new InterpreterResult(Code.SUCCESS);
    } finally {
      notebookLock.unlock();
    }
  }

  // ---- session-slot helpers (static, shared across all instances) ----

  /**
   * Try to claim one session slot for the user.
   * @return true if a slot was available and claimed
   */
  private static synchronized boolean acquireSessionSlot(String user, int maxSessions) {
    int current = userSessionCount.getOrDefault(user, 0);
    if (current >= maxSessions) {
      LOGGER.warn("User {} already has {} active Spark Connect sessions (max {})",
          user, current, maxSessions);
      return false;
    }
    userSessionCount.put(user, current + 1);
    LOGGER.info("Acquired session slot for user {}. Active sessions: {}/{}",
        user, current + 1, maxSessions);
    return true;
  }

  /**
   * Release one session slot for the user.
   */
  private static synchronized void releaseSessionSlot(String user) {
    if (user == null) {
      return;
    }
    int current = userSessionCount.getOrDefault(user, 0);
    if (current <= 1) {
      userSessionCount.remove(user);
    } else {
      userSessionCount.put(user, current - 1);
    }
    LOGGER.info("Released session slot for user {}. Remaining sessions: {}",
        user, Math.max(0, current - 1));
  }

  /** Visible for testing. */
  static int getActiveSessionCount(String user) {
    return userSessionCount.getOrDefault(user, 0);
  }

  private InterpreterResult handleSqlException(Exception e, String sql,
      InterpreterContext context) {
    try {
      LOGGER.error("Error executing SQL: {}", sql, e);
      context.out.write("\nError in SQL: " + sql + "\n");
      if (Boolean.parseBoolean(getProperty("zeppelin.spark.sql.stacktrace", "true"))) {
        if (e.getCause() != null) {
          context.out.write(ExceptionUtils.getStackTrace(e.getCause()));
        } else {
          context.out.write(ExceptionUtils.getStackTrace(e));
        }
      } else {
        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        context.out.write(msg
            + "\nSet zeppelin.spark.sql.stacktrace = true to see full stacktrace");
      }
      context.out.flush();
    } catch (IOException ex) {
      LOGGER.error("Failed to write error output", ex);
    }
    return new InterpreterResult(Code.ERROR);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    if (sparkSession != null) {
      try {
        sparkSession.interruptAll();
      } catch (Exception e) {
        LOGGER.warn("Error interrupting Spark Connect session", e);
      }
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    return new ArrayList<>();
  }

  public SparkSession getSparkSession() {
    return sparkSession;
  }

  public int getMaxResult() {
    return maxResult;
  }
}
