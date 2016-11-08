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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Base class for livy interpreters.
 */
public abstract class BaseLivyInterprereter extends Interpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(BaseLivyInterprereter.class);

  // -1 means session is not created yet, valid sessionId start from 0
  protected int sessionId = -1;
  protected String appId;
  protected String webUIAddress;
  protected boolean displayAppInfo;
  protected LivyOutputStream out;
  protected LivyHelper livyHelper;

  public BaseLivyInterprereter(Properties property) {
    super(property);
    this.out = new LivyOutputStream();
    this.livyHelper = new LivyHelper(property);
  }

  public abstract String getSessionKind();

  @Override
  public void open() {
    // TODO(zjffdu) move session creation here.
  }

  @Override
  public void close() {
    if (sessionId != -1) {
      livyHelper.closeSession(sessionId);
    }
  }

  protected void createSession(InterpreterContext context) throws Exception {
    sessionId = livyHelper.createSession(context, getSessionKind());
    if (displayAppInfo) {
      this.appId = extractStatementResult(
          livyHelper.interpret("sc.applicationId", context, sessionId).message());
      livyHelper.interpret(
          "val webui=sc.getClass.getMethod(\"ui\").invoke(sc).asInstanceOf[Some[_]].get",
          context, sessionId);
      this.webUIAddress = extractStatementResult(
          livyHelper.interpret(
              "webui.getClass.getMethod(\"appUIAddress\").invoke(webui)",
              context, sessionId).message());
      LOGGER.info("Create livy session with sessionId: {}, appId: {}, webUI: {}",
          sessionId, appId, webUIAddress);
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    try {
      // add synchronized, because LivySparkSQLInterperter will use ParallelScheduler
      synchronized (this) {
        if (sessionId == -1) {
          try {
            createSession(context);
          } catch (Exception e) {
            LOGGER.error("Exception while creating livy session", e);
            return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
          }
        }
      }
      if (StringUtils.isEmpty(st)) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      return livyHelper.interpretInput(st, context, sessionId, out,
          appId, webUIAddress, displayAppInfo);
    } catch (Exception e) {
      LOGGER.error("Exception in LivyInterpreter.", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
    }
  }

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * res0: String = application_1473129941656_0048
   *
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
}
