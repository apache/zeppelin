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


package org.apache.zeppelin.spark.submit;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.shell.ShellInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Interpreter that supports the `%spark-submit` command in Apache Zeppelin.
 * <p>
 * This interpreter allows users to submit Spark jobs using the standard `spark-submit` CLI
 * interface.
 * Internally, it delegates execution to the ShellInterpreter to run `spark-submit` as a shell
 * command.
 * <p>
 * Key features:
 * - Automatically builds and executes the `spark-submit` command using the configured SPARK_HOME
 * path.
 * - Extracts the Spark UI URL from logs and publishes it to the Zeppelin frontend.
 * - Tracks the YARN Application ID from the logs, allowing the job to be cancelled via `yarn
 * application -kill`.
 * - Handles both YARN and local Spark modes.
 * <p>
 * Required configuration:
 * - SPARK_HOME must be set in the interpreter setting or environment variables. It should point
 * to the root
 * directory of a valid Spark installation.
 * <p>
 * Example usage in a Zeppelin notebook:
 * %spark-submit --class org.apache.spark.examples.SparkPi /path/to/jar spark-args
 */
public class SparkSubmitInterpreter extends ShellInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkSubmitInterpreter.class);

  private final String sparkHome;

  // paragraphId --> yarnAppId
  private ConcurrentMap<String, String> yarnAppIdMap = new ConcurrentHashMap<>();

  public SparkSubmitInterpreter(Properties property) {
    super(property);
    setProperty("shell.command.timeout.millisecs", String.valueOf(Integer.MAX_VALUE));
    this.sparkHome = properties.getProperty("SPARK_HOME");
    LOGGER.info("SPARK_HOME: {}", sparkHome);
  }

  /**
   * Executes a spark-submit command based on the user's input in a Zeppelin notebook paragraph.
   * <p>
   * This method constructs the full spark-submit CLI command using the configured SPARK_HOME and
   * the
   * provided arguments. It performs validation (e.g., SPARK_HOME presence), logs the execution,
   * and registers a listener to extract Spark UI information from the output logs.
   * <p>
   * If SPARK_HOME is not set, an error result is returned.
   * After execution, any associated YARN application ID is removed from the internal tracking map.
   *
   * @param cmd     The spark-submit arguments entered by the user (e.g., "--class ...
   *                /path/to/jar").
   * @param context The interpreter context for the current paragraph execution.
   * @return An {@link InterpreterResult} representing the outcome of the spark-submit execution.
   */
  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    if (StringUtils.isBlank(cmd)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    if (StringUtils.isBlank(sparkHome)) {
      String errorMsg = "SPARK_HOME is not set. Please configure SPARK_HOME in the interpreter " +
          "setting or environment.";
      LOGGER.error("Failed to run spark-submit: {}", errorMsg);
      return new InterpreterResult(InterpreterResult.Code.ERROR, errorMsg);
    }

    File sparkSubmit = Paths.get(sparkHome, "bin", "spark-submit").toFile();
    if (!sparkSubmit.exists()) {
      String errorMsg = "spark-submit command does not exist at: " + sparkSubmit.getAbsolutePath();
      LOGGER.error("Failed to run spark-submit: {}", errorMsg);
      return new InterpreterResult(InterpreterResult.Code.ERROR, errorMsg);
    }

    String sparkSubmitCommand = sparkSubmit.getAbsolutePath() + " " + cmd.trim();
    LOGGER.info("Run spark command: {}", sparkSubmitCommand);
    context.out.addInterpreterOutListener(new SparkSubmitOutputListener(context));
    InterpreterResult result = super.internalInterpret(sparkSubmitCommand, context);
    yarnAppIdMap.remove(context.getParagraphId());
    return result;
  }

  @Override
  public void cancel(InterpreterContext context) {
    super.cancel(context);
    String yarnAppId = yarnAppIdMap.remove(context.getParagraphId());
    if (StringUtils.isNotBlank(yarnAppId)) {
      try {
        LOGGER.info("Try to kill yarn app: {} of code: {}", yarnAppId, context.getParagraphText());
        Runtime.getRuntime().exec(new String[]{"yarn", "application", "-kill", yarnAppId});
      } catch (IOException e) {
        LOGGER.warn("Fail to kill yarn app, please check whether yarn command is on your PATH", e);
      }
    } else {
      LOGGER.warn("No yarn app associated with code: {}", context.getParagraphText());
    }
  }

  /**
   * An output listener that parses logs generated by spark-submit to extract metadata such as:
   * - Spark UI URL (for both YARN and local modes)
   * - YARN application ID (for job cancellation)
   * <p>
   * The extracted information is sent back to Zeppelin frontend to enable dynamic rendering
   * of job status and UI links.
   */
  private class SparkSubmitOutputListener implements InterpreterOutputListener {

    private InterpreterContext context;
    private boolean isSparkUrlSent = false;

    SparkSubmitOutputListener(InterpreterContext context) {
      this.context = context;
    }

    @Override
    public void onUpdateAll(InterpreterOutput out) {

    }

    @Override
    public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
      String text = new String(line);
      LOGGER.debug("Output: {}", text);
      if (isSparkUrlSent) {
        return;
      }
      if (text.contains("tracking URL:")) {
        // yarn mode, extract yarn proxy url as spark ui link
        buildSparkUIInfo(text, context);
        isSparkUrlSent = true;
      } else if (text.contains("Bound SparkUI to")) {
        // other mode, extract the spark ui link
        buildSparkUIInfo(text, context);
        isSparkUrlSent = true;
      }
    }

    private void buildSparkUIInfo(String log, InterpreterContext context) {
      int pos = log.lastIndexOf(" ");
      if (pos != -1) {
        String sparkUI = log.substring(pos + 1).trim();
        sparkUI = sparkUI.substring(0, sparkUI.length() - 1);
        Map<String, String> infos = new java.util.HashMap<String, String>();
        infos.put("jobUrl", sparkUI);
        infos.put("label", "Spark UI");
        infos.put("tooltip", "View in Spark web UI");
        infos.put("noteId", context.getNoteId());
        infos.put("paraId", context.getParagraphId());
        context.getIntpEventClient().onParaInfosReceived(infos);

        // extract yarn appId
        int lastIndexOfSlash = sparkUI.lastIndexOf("/");
        if (lastIndexOfSlash != -1) {
          String yarnAppId = sparkUI.substring(lastIndexOfSlash + 1);
          LOGGER.info("Detected yarn app: {}", yarnAppId);
          SparkSubmitInterpreter.this.yarnAppIdMap.put(context.getParagraphId(), yarnAppId);
        } else {
          LOGGER.warn("Might be an invalid spark URL: {}", sparkUI);
        }
      } else {
        LOGGER.error("Unable to extract spark url from this log: {}", log);
      }
    }

    @Override
    public void onUpdate(int index, InterpreterResultMessageOutput out) {

    }
  }
}
