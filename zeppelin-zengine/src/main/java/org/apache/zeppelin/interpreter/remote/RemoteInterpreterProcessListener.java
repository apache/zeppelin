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
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Event from remoteInterpreterProcess
 */
public interface RemoteInterpreterProcessListener {
  public void onOutputAppend(String noteId, String paragraphId, int index, String output);
  public void onOutputUpdated(
      String noteId, String paragraphId, int index, InterpreterResult.Type type, String output);
  public void onOutputClear(String noteId, String paragraphId);
  void runParagraphs(String noteId, List<Integer> paragraphIndices, List<String> paragraphIds,
                     String curParagraphId)
      throws IOException;

  public void onParaInfosReceived(String noteId, String paragraphId,
                                  String interpreterSettingId, Map<String, String> metaInfos);

  List<ParagraphInfo> getParagraphList(String user, String noteId) throws TException, IOException;
}
