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

import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.shell.ShellInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Support %spark-submit which run spark-submit command. Internally,
 * it would run shell command via ShellInterpreter.
 *
 */
public class SparkSubmitInterpreter extends ShellInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkSubmitInterpreter.class);

  private String sparkHome;

  // paragraphId --> yarnAppId
  private ConcurrentMap<String, String> yarnAppIdMap = new ConcurrentHashMap();

  public SparkSubmitInterpreter(Properties property) {
    super(property);
    // Set time to be max integer so that the shell process won't timeout.
    setProperty("shell.command.timeout.millisecs", Integer.MAX_VALUE + "");
    this.sparkHome = properties.getProperty("SPARK_HOME");
    LOGGER.info("SPARK_HOME: " + sparkHome);
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    if (StringUtils.isBlank(cmd)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }
    String sparkSubmitCommand = sparkHome + "/bin/spark-submit " + cmd.trim();
    LOGGER.info("Run spark command: " + sparkSubmitCommand);
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
        Runtime.getRuntime().exec(new String[] {"yarn", "application", "-kill", yarnAppId});
      } catch (IOException e) {
        LOGGER.warn("Fail to kill yarn app, please check whether yarn command is on your PATH", e);
      }
    } else {
      LOGGER.warn("No yarn app associated with code: {}", context.getParagraphText());
    }
  }

  /**
   * InterpreterOutputListener which extract spark ui link from logs.
   */
  private class SparkSubmitOutputListener implements InterpreterOutputListener  {

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
          LOGGER.warn("Might be an invalid spark URL: " + sparkUI);
        }
      } else {
        LOGGER.error("Unable to extract spark url from this log: " + log);
      }
    }

    @Override
    public void onUpdate(int index, InterpreterResultMessageOutput out) {

    }
  }
}
