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

import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.io.IOException;

/**
 * Notebook event
 */
public interface NoteEventListener {
  void onNoteRemove(Note note, AuthenticationInfo subject) throws IOException;
  void onNoteCreate(Note note, AuthenticationInfo subject) throws IOException;
  void onNoteUpdate(Note note, AuthenticationInfo subject) throws IOException;

  void onParagraphRemove(Paragraph p) throws IOException;
  void onParagraphCreate(Paragraph p) throws IOException;
  void onParagraphUpdate(Paragraph p) throws IOException;
  void onParagraphStatusChange(Paragraph p, Job.Status status) throws IOException;
}
