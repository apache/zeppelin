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
package org.apache.zeppelin.util;

import java.io.IOException;
import java.util.Date;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteJsonExclusionStrategy;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.exception.CorruptedNoteException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class NoteUtils {
  private NoteUtils() {
    throw new IllegalStateException("Utility class");
  }

  public static Gson getNoteGson(ZeppelinConfiguration zConf) {
    return new GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
        .registerTypeAdapterFactory(Input.TypeAdapterFactory)
        .setExclusionStrategies(new NoteJsonExclusionStrategy(zConf))
        .create();
  }

  /**
   * Parse note json from note file. Throw IOException if fail to parse note json.
   *
   * @param json
   * @return Note
   * @throws IOException if fail to parse note json (note file may be corrupted)
   */
  public static Note fromJson(Gson gson, ZeppelinConfiguration conf, String noteId, String json)
      throws IOException {
    try {
      Note note = gson.fromJson(json, Note.class);
      note.setZeppelinConfiguration(conf);
      note.setGson(gson);
      NoteUtils.convertOldInput(note);
      note.getInfo().remove("isRunning");
      note.postProcessParagraphs();
      return note;
    } catch (JsonSyntaxException e) {
      throw new CorruptedNoteException(noteId, "Fail to parse note json: " + json, e);
    }
  }

  private static void convertOldInput(Note note) {
    for (Paragraph p : note.getParagraphs()) {
      p.settings.convertOldInput();
    }
  }
}
