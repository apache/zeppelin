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

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.pig.PigServer;
import org.apache.pig.backend.BackendException;
import org.apache.pig.backend.hadoop.executionengine.HExecutionEngine;
import org.apache.pig.backend.hadoop.executionengine.Launcher;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public abstract class BasePigInterpreter extends Interpreter {

  private static Logger LOGGER = LoggerFactory.getLogger(BasePigInterpreter.class);

  protected ConcurrentHashMap<String, PigScriptListener> listenerMap = new ConcurrentHashMap<>();

  public BasePigInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void cancel(InterpreterContext context) {
    LOGGER.info("Cancel paragraph:" + context.getParagraphId());
    PigScriptListener listener = listenerMap.get(context.getParagraphId());
    if (listener != null) {
      Set<String> jobIds = listener.getJobIds();
      if (jobIds.isEmpty()) {
        LOGGER.info("No job is started, so can not cancel paragraph:" + context.getParagraphId());
      }
      for (String jobId : jobIds) {
        LOGGER.info("Kill jobId:" + jobId);
        HExecutionEngine engine =
                (HExecutionEngine) getPigServer().getPigContext().getExecutionEngine();
        try {
          Field launcherField = HExecutionEngine.class.getDeclaredField("launcher");
          launcherField.setAccessible(true);
          Launcher launcher = (Launcher) launcherField.get(engine);
          // It doesn't work for Tez Engine due to PIG-5035
          launcher.killJob(jobId, new Configuration());
        } catch (NoSuchFieldException | BackendException | IllegalAccessException e) {
          LOGGER.error("Fail to cancel paragraph:" + context.getParagraphId(), e);
        }
      }
    } else {
      LOGGER.warn("No PigScriptListener found, can not cancel paragraph:"
              + context.getParagraphId());
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    PigScriptListener listener = listenerMap.get(context.getParagraphId());
    if (listener != null) {
      return listener.getProgress();
    }
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            PigInterpreter.class.getName() + this.hashCode());
  }

  public abstract PigServer getPigServer();

  /**
   * Use paragraph title if it exists, else use the last line of pig script.
   * @param cmd
   * @param context
   * @return
   */
  protected String createJobName(String cmd, InterpreterContext context) {
    String pTitle = context.getParagraphTitle();
    if (!StringUtils.isBlank(pTitle)) {
      return pTitle;
    } else {
      // use the last non-empty line of pig script as the job name.
      String[] lines = cmd.split("\n");
      for (int i = lines.length - 1; i >= 0; --i) {
        if (!StringUtils.isBlank(lines[i])) {
          return lines[i];
        }
      }
      // in case all the lines are empty, but usually it is almost impossible
      return "empty_job";
    }
  }
}
