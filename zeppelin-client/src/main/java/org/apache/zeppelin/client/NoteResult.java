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

import java.util.List;

/**
 * Represent the note execution result.
 */
public class NoteResult {
  private String noteId;
  private boolean isRunning;
  private List<ParagraphResult> paragraphResultList;

  public NoteResult(String noteId, boolean isRunning, List<ParagraphResult> paragraphResultList) {
    this.noteId = noteId;
    this.isRunning = isRunning;
    this.paragraphResultList = paragraphResultList;
  }

  public String getNoteId() {
    return noteId;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public List<ParagraphResult> getParagraphResultList() {
    return paragraphResultList;
  }

  @Override
  public String toString() {
    return "NoteResult{" +
            "noteId='" + noteId + '\'' +
            ", isRunning=" + isRunning +
            ", paragraphResultList=" + paragraphResultList +
            '}';
  }
}
