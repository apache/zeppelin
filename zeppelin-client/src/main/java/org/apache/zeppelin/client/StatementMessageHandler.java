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

package org.apache.zeppelin.client;

/**
 * MessageHandler for each statement.
 */
public interface StatementMessageHandler {

  /**
   * Invoked when there's new statement output appended.
   *
   * @param statementId
   * @param index
   * @param output
   */
  void onStatementAppendOutput(String statementId, int index, String output);

  /**
   * Invoked when statement's output is updated.
   *
   * @param statementId
   * @param index
   * @param type
   * @param output
   */
  void onStatementUpdateOutput(String statementId, int index, String type, String output);

}
