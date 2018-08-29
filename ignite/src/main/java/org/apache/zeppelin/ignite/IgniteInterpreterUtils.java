/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.ignite;

import org.apache.zeppelin.interpreter.InterpreterResult;

/**
 * Apache Ignite interpreter utils.
 */
public class IgniteInterpreterUtils {
  /**
   * Builds error result from given exception.
   * @param e Exception.
   * @return result.
   */
  public static InterpreterResult buildErrorResult(Throwable e) {
    StringBuilder sb = new StringBuilder(e.getMessage());

    while ((e = e.getCause()) != null) {
      String errMsg = e.getMessage();

      if (errMsg != null) {
        sb.append('\n').append(errMsg);
      }
    }

    return new InterpreterResult(InterpreterResult.Code.ERROR, sb.toString());
  }
}
