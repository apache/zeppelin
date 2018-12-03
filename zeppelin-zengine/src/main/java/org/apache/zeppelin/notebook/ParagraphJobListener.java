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

package org.apache.zeppelin.notebook;

import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.scheduler.JobListener;

import java.util.List;

/**
 * Listen paragraph update
 */
public interface ParagraphJobListener extends JobListener<Paragraph> {
  void onOutputAppend(Paragraph paragraph, int idx, String output);
  void onOutputUpdate(Paragraph paragraph, int idx, InterpreterResultMessage msg);
  void onOutputUpdateAll(Paragraph paragraph, List<InterpreterResultMessage> msgs);

  //TODO(savalek) Temporary solution. Need to refactor cron to be able to notify frontend directly.
  void noteRunningStatusChange(String noteId, boolean newStatus);
}
