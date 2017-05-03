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

/**
 * Base class for PySpark Interpreter
 */
public abstract class LivyPySparkBaseInterpreter extends BaseLivyInterpreter {

  public LivyPySparkBaseInterpreter(Properties property) {
    super(property);
  }

  @Override
  protected String extractAppId() throws LivyException {
    return extractStatementResult(
        interpret("sc.applicationId", null, false, false).message()
            .get(0).getData());
  }

  @Override
  protected String extractWebUIAddress() throws LivyException {
    return extractStatementResult(
        interpret(
            "sc._jsc.sc().ui().get().appUIAddress()", null, false, false)
            .message().get(0).getData());
  }

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * u'application_1473129941656_0048'
   *
   * @param result
   * @return
   */
  private String extractStatementResult(String result) {
    int pos = -1;
    if ((pos = result.indexOf("'")) >= 0) {
      return result.substring(pos + 1, result.length() - 1).trim();
    } else {
      throw new RuntimeException("No result can be extracted from '" + result + "', " +
          "something must be wrong");
    }
  }
}
