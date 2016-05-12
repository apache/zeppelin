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
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Livy Spark interpreter for Zeppelin.
 */
public class LivySparkInterpreter extends Interpreter {

  static String DEFAULT_URL = "http://localhost:8998";
  static String LOCAL = "local[*]";
  Logger LOGGER = LoggerFactory.getLogger(LivySparkInterpreter.class);
  private LivyOutputStream out;

  static {
    Interpreter.register(
        "spark",
        "livy",
        LivySparkInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("zeppelin.livy.url", DEFAULT_URL, "The URL for Livy Server.")
            .add("zeppelin.livy.master", LOCAL, "Spark master uri. ex) spark://masterhost:7077")
            .build()
    );
  }

  protected static Map<String, Integer> userSessionMap;
  private LivyHelper livyHelper;

  public LivySparkInterpreter(Properties property) {
    super(property);
    userSessionMap = new HashMap<>();
    livyHelper = new LivyHelper(property);
    out = new LivyOutputStream();
  }

  protected static Map<String, Integer> getUserSessionMap() {
    return userSessionMap;
  }

  public void setUserSessionMap(Map<String, Integer> userSessionMap) {
    this.userSessionMap = userSessionMap;
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
      if (userSessionMap.get(interpreterContext.getAuthenticationInfo().getUser()) == null) {
        try {
          userSessionMap.put(
              interpreterContext.getAuthenticationInfo().getUser(),
              livyHelper.createSession(
                  interpreterContext,
                  "spark")
          );
          livyHelper.initializeSpark(interpreterContext, userSessionMap);
        } catch (Exception e) {
          LOGGER.error("Exception in LivySparkInterpreter while interpret ", e);
          return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
        }
      }
      if (line == null || line.trim().length() == 0) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      return livyHelper.interpretInput(line, interpreterContext, userSessionMap, out);
    } catch (Exception e) {
      LOGGER.error("Exception in LivySparkInterpreter while interpret ", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
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
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
