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

package org.apache.zeppelin.interpreter;

/**
 *
 */
public class ResultMessages {
  public static final String EXCEEDS_LIMIT_ROWS =
      "<strong>Output is truncated</strong> to %s rows. Learn more about <strong>%s</strong>";
  public static final String EXCEEDS_LIMIT_SIZE =
      "<strong>Output is truncated</strong> to %s bytes. Learn more about <strong>%s</strong>";
  public static final String EXCEEDS_LIMIT =
      "<div class=\"result-alert alert-warning\" role=\"alert\">" +
          "<button type=\"button\" class=\"close\" data-dismiss=\"alert\" aria-label=\"Close\">" +
          "<span aria-hidden=\"true\">&times;</span></button>" +
          "%s" +
          "</div>";

  public static InterpreterResultMessage getExceedsLimitRowsMessage(int amount, String variable) {
    InterpreterResultMessage message = new InterpreterResultMessage(InterpreterResult.Type.HTML,
        String.format(EXCEEDS_LIMIT, String.format(EXCEEDS_LIMIT_ROWS, amount, variable)));
    return message;
  }

  public static InterpreterResultMessage getExceedsLimitSizeMessage(int amount, String variable) {
    InterpreterResultMessage message = new InterpreterResultMessage(InterpreterResult.Type.HTML,
        String.format(EXCEEDS_LIMIT, String.format(EXCEEDS_LIMIT_SIZE, amount, variable)));
    return message;
  }
}
