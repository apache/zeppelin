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
package org.apache.zeppelin.interpreter.remote;

import org.apache.zeppelin.interpreter.InterpreterResult;

import java.util.Map;

/**
 * Event from remoteInterpreterProcess
 */
public interface RemoteInterpreterProcessListener {
  public void onOutputAppend(String noteId, String paragraphId, int index, String output);
  public void onOutputUpdated(
      String noteId, String paragraphId, int index, InterpreterResult.Type type, String output);
  public void onOutputClear(String noteId, String paragraphId);
  public void onMetaInfosReceived(String settingId, Map<String, String> metaInfos);
  public void onRemoteRunParagraph(String noteId, String ParagraphID) throws Exception;
  public void onGetParagraphRunners(
      String noteId, String paragraphId, RemoteWorksEventListener callback);

  /**
   * Remote works for Interpreter callback listener
   */
  public interface RemoteWorksEventListener {
    public void onFinished(Object resultObject);
    public void onError();
  }
}
