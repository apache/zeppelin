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

package org.apache.zeppelin.rest.message;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.zeppelin.notebook.Note;

import java.util.List;
import java.util.stream.Collectors;

public class NoteJobStatus {
  private static final Gson GSON = new Gson();

  private String id;
  private boolean isRunning;
  @SerializedName("paragraphs")
  private List<ParagraphJobStatus> paragraphJobStatusList;

  public NoteJobStatus(Note note) {
    this.id = note.getId();
    this.isRunning = note.isRunning();
    this.paragraphJobStatusList = note.getParagraphs().stream()
            .map(p -> new ParagraphJobStatus(p))
            .collect(Collectors.toList());
  }

  public List<ParagraphJobStatus> getParagraphJobStatusList() {
    return paragraphJobStatusList;
  }

  public static NoteJobStatus fromJson(String json) {
    return GSON.fromJson(json, NoteJobStatus.class);
  }
}
