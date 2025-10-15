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

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Listener for events from RemoteInterpreterProcess.
 */
public interface RemoteInterpreterProcessListener {
  /**
   * Invoked when output is appended.
   * @param noteId
   * @param paragraphId
   * @param index
   * @param output
   */
  void onOutputAppend(String noteId, String paragraphId, int index, String output);

  /**
   * Invoked when the whole output is updated
   * @param noteId
   * @param paragraphId
   * @param index
   * @param type
   * @param output
   */
  void onOutputUpdated(
      String noteId, String paragraphId, int index, InterpreterResult.Type type, String output);

  /**
   * Invoked when output is cleared.
   * @param noteId
   * @param paragraphId
   */
  void onOutputClear(String noteId, String paragraphId);

  /**
   * Run paragraphs, paragraphs can be specified via indices(paragraphIndices) or ids(paragraphIds)
   * @param noteId
   * @param paragraphIndices
   * @param paragraphIds
   * @param curParagraphId
   * @throws IOException
   */
  void runParagraphs(String noteId, List<Integer> paragraphIndices, List<String> paragraphIds,
                     String curParagraphId)
      throws IOException;

  /**
   * Invoked when paragraph runtime info is received, such as spark job info.
   * @param noteId
   * @param paragraphId
   * @param interpreterSettingId
   * @param metaInfos
   */
  void onParaInfosReceived(String noteId, String paragraphId,
                                  String interpreterSettingId, Map<String, String> metaInfos);

  /**
   * Invoked for getting paragraph infos.
   * @param user
   * @param noteId
   * @return
   * @throws TException
   * @throws IOException
   */
  List<ParagraphInfo> getParagraphList(String user, String noteId) throws TException, IOException;

  /**
   * Invoked for checkpoint partial paragraph output.
   * @param noteId
   * @param paragraphId
   */
  void checkpointOutput(String noteId, String paragraphId);
}
