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
 * Livy Spark interpreter for Zeppelin.
 */
public class LivySparkInterpreter extends BaseLivyInterpreter {

  public LivySparkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public String getSessionKind() {
    return "spark";
  }

  @Override
  protected String extractAppId() throws LivyException {
    return extractStatementResult(
        interpret("sc.applicationId", null, false, false).message()
            .get(0).getData());
  }

  @Override
  protected String extractWebUIAddress() throws LivyException {
    interpret(
        "val webui=sc.getClass.getMethod(\"ui\").invoke(sc).asInstanceOf[Some[_]].get",
        null, false, false);
    return extractStatementResult(
        interpret(
            "webui.getClass.getMethod(\"appUIAddress\").invoke(webui)", null, false, false)
            .message().get(0).getData());
  }

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * res0: String = application_1473129941656_0048
   *
   * @param result
   * @return
   */
  public String extractStatementResult(String result) {
    int pos = -1;
    if ((pos = result.indexOf("=")) >= 0) {
      return result.substring(pos + 1).trim();
    } else {
      throw new RuntimeException("No result can be extracted from '" + result + "', " +
          "something must be wrong");
    }
  }
}
