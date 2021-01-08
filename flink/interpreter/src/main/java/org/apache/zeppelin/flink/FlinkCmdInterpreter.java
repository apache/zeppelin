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


package org.apache.zeppelin.flink;

import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.shell.ShellInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

public class FlinkCmdInterpreter extends ShellInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkCmdInterpreter.class);

  private String flinkHome;

  public FlinkCmdInterpreter(Properties property) {
    super(property);
    // Set time to be max integer so that the shell process won't timeout.
    setProperty("shell.command.timeout.millisecs", Integer.MAX_VALUE + "");
    this.flinkHome = properties.getProperty("FLINK_HOME");
    LOGGER.info("FLINK_HOME: " + flinkHome);
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    String flinkCommand = flinkHome + "/bin/flink " + cmd.trim();
    LOGGER.info("Flink command: " + flinkCommand);
    context.out.addInterpreterOutListener(new FlinkCmdOutputListener(context));
    return super.internalInterpret(flinkCommand, context);
  }

  /**
   * InterpreterOutputListener which extract flink link from logs.
   */
  private static class FlinkCmdOutputListener implements InterpreterOutputListener {

    private InterpreterContext context;
    private boolean isFlinkUrlSent = false;

    public FlinkCmdOutputListener(InterpreterContext context) {
      this.context = context;
    }

    @Override
    public void onUpdateAll(InterpreterOutput out) {

    }

    @Override
    public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
      String text = new String(line);
      if (isFlinkUrlSent) {
        return;
      }
      if (text.contains("Submitted application")) {
        // yarn mode, extract yarn proxy url as flink ui link
        buildFlinkUIInfo(text, context);
        isFlinkUrlSent = true;
      }
    }

    private void buildFlinkUIInfo(String log, InterpreterContext context) {
      int pos = log.lastIndexOf(" ");
      if (pos != -1) {
        String appId = log.substring(pos + 1);
        try {
          YarnClient yarnClient = YarnClient.createYarnClient();
          yarnClient.init(new YarnConfiguration());
          yarnClient.start();

          ApplicationReport applicationReport = yarnClient.getApplicationReport(ConverterUtils.toApplicationId(appId));
          Map<String, String> infos = new java.util.HashMap<String, String>();
          infos.put("jobUrl", applicationReport.getTrackingUrl());
          infos.put("label", "Flink UI");
          infos.put("tooltip", "View in Flink web UI");
          infos.put("noteId", context.getNoteId());
          infos.put("paraId", context.getParagraphId());
          context.getIntpEventClient().onParaInfosReceived(infos);
        } catch (Exception e) {
          LOGGER.error("Fail to extract flink url", e);
        }
      } else {
        LOGGER.error("Unable to extract flink url from this log: " + log);
      }
    }

    @Override
    public void onUpdate(int index, InterpreterResultMessageOutput out) {

    }
  }
}
