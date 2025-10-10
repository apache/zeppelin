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

import java.util.Date;

import jakarta.inject.Inject;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.notebook.exception.CorruptedNoteException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class GsonNoteParser implements NoteParser {

  private final ZeppelinConfiguration zConf;
  private final Gson gson;

  @Inject
  public GsonNoteParser(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
    this.gson = new GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
        .registerTypeAdapterFactory(Input.TypeAdapterFactory)
        .setExclusionStrategies(new NoteJsonExclusionStrategy(zConf))
        .create();
  }

  @Override
  public Note fromJson(String noteId, String json) throws CorruptedNoteException {
    try {
      Note note = gson.fromJson(json, Note.class);
      note.setZeppelinConfiguration(zConf);
      note.setNoteParser(this);
      convertOldInput(note);
      note.getInfo().remove("isRunning");
      note.postProcessParagraphs();
      return note;
    } catch (JsonSyntaxException e) {
      throw new CorruptedNoteException(noteId, "Fail to parse note json: " + json, e);
    }
  }

  @Override
  public Paragraph fromJson(String json) {
    return gson.fromJson(json, Paragraph.class);
  }

  @Override
  public String toJson(Note note) {
    return gson.toJson(note);
  }

  @Override
  public String toJson(Paragraph paragraph) {
    return gson.toJson(paragraph);
  }

  private static void convertOldInput(Note note) {
    for (Paragraph p : note.getParagraphs()) {
      p.settings.convertOldInput();
    }
  }
}
