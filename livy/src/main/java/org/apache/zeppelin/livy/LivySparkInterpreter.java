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

package org.apache.zeppelin.livy;

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Livy Spark interpreter for Zeppelin.
 */
public class LivySparkInterpreter extends Interpreter {

  Logger LOGGER = LoggerFactory.getLogger(LivySparkInterpreter.class);
  private LivyOutputStream out;

  protected static ConcurrentHashMap<String, Integer> userSessionMap = new ConcurrentHashMap();
  protected static ConcurrentHashMap<Integer, String> sessionId2AppIdMap = new ConcurrentHashMap();
  protected static ConcurrentHashMap<Integer, String> sessionId2WebUIMap = new ConcurrentHashMap();

  private LivyHelper livyHelper;
  private boolean displayAppInfo;

  public LivySparkInterpreter(Properties property) {
    super(property);
    livyHelper = new LivyHelper(property);
    out = new LivyOutputStream();
    this.displayAppInfo = Boolean.parseBoolean(getProperty("zeppelin.livy.displayAppInfo"));
  }

  protected static ConcurrentHashMap<String, Integer> getUserSessionMap() {
    return userSessionMap;
  }

  @Override
  public void open() {
  }

  @Override
  public void close() {
    livyHelper.closeSession(userSessionMap);
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext interpreterContext) {
    try {
      Integer sessionId = null;
      if (userSessionMap.get(interpreterContext.getAuthenticationInfo().getUser()) == null) {
        try {
          sessionId = livyHelper.createSession(interpreterContext, "spark");
          userSessionMap.put(interpreterContext.getAuthenticationInfo().getUser(), sessionId);
          if (displayAppInfo) {
            String appId = extractStatementResult(
                    livyHelper.interpret("sc.applicationId", interpreterContext, userSessionMap)
                            .message());
            livyHelper.interpret(
                    "val webui=sc.getClass.getMethod(\"ui\").invoke(sc).asInstanceOf[Some[_]].get",
                    interpreterContext, userSessionMap);
            String webUI = extractStatementResult(
                    livyHelper.interpret(
                            "webui.getClass.getMethod(\"appUIAddress\").invoke(webui)",
                            interpreterContext, userSessionMap).message());
            sessionId2AppIdMap.put(sessionId, appId);
            sessionId2WebUIMap.put(sessionId, webUI);
            LOGGER.info("Create livy session with sessionId: {}, appId: {}, webUI: {}",
                    sessionId, appId, webUI);
          } else {
            LOGGER.info("Create livy session with sessionId: {}", sessionId);
          }
        } catch (Exception e) {
          LOGGER.error("Exception in LivySparkInterpreter while interpret ", e);
          return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
        }
      } else {
        sessionId = userSessionMap.get(interpreterContext.getAuthenticationInfo().getUser());
      }
      if (line == null || line.trim().length() == 0) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      return livyHelper.interpretInput(line, interpreterContext, userSessionMap, out,
              sessionId2AppIdMap.get(sessionId), sessionId2WebUIMap.get(sessionId), displayAppInfo);
    } catch (Exception e) {
      LOGGER.error("Exception in LivySparkInterpreter while interpret ", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
    }
  }

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * res0: String = application_1473129941656_0048
   * @param result
   * @return
   */
  private static String extractStatementResult(String result) {
    int pos = -1;
    if ((pos = result.indexOf("=")) >= 0) {
      return result.substring(pos + 1).trim();
    } else {
      throw new RuntimeException("No result can be extracted from '" + result + "', " +
              "something must be wrong");
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    livyHelper.cancelHTTP(context.getParagraphId());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        LivySparkInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

}
