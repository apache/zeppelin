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

import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Livy Interpreter for shared kind which share SparkContext across spark/pyspark/r. */
public class LivySharedInterpreter extends BaseLivyInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LivySharedInterpreter.class);

  private boolean isSupported = false;

  public LivySharedInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    try {
      // check livy version
      try {
        this.livyVersion = getLivyVersion();
        LOGGER.info("Use livy " + livyVersion);
      } catch (APINotFoundException e) {
        // assume it is livy 0.2.0 when livy doesn't support rest api of fetching version.
        this.livyVersion = new LivyVersion("0.2.0");
        LOGGER.info("Use livy 0.2.0");
      }

      if (livyVersion.isSharedSupported()) {
        LOGGER.info("LivySharedInterpreter is supported.");
        isSupported = true;
        initLivySession();
      } else {
        LOGGER.info("LivySharedInterpreter is not supported.");
        isSupported = false;
      }
    } catch (LivyException e) {
      String msg =
          "Fail to create session, please check livy interpreter log and " + "livy server log";
      throw new InterpreterException(msg, e);
    }
  }

  public boolean isSupported() {
    return isSupported;
  }

  public InterpreterResult interpret(String st, String codeType, InterpreterContext context) {
    if (StringUtils.isEmpty(st)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
    }

    try {
      return interpret(st, codeType, context.getParagraphId(), this.displayAppInfo, true, true);
    } catch (LivyException e) {
      LOGGER.error("Fail to interpret:" + st, e);
      return new InterpreterResult(
          InterpreterResult.Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
    }
  }

  @Override
  public String getSessionKind() {
    return "shared";
  }

  @Override
  protected String extractAppId() throws LivyException {
    return null;
  }

  @Override
  protected String extractWebUIAddress() throws LivyException {
    return null;
  }

  public static void main(String[] args) {
    ExecuteRequest request = new ExecuteRequest("1+1", null);
    System.out.println(request.toJson());
  }
}
