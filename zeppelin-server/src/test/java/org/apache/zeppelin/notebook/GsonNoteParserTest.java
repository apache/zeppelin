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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.jupiter.api.Test;

class GsonNoteParserTest {
  private final GsonNoteParser parser = new GsonNoteParser(ZeppelinConfiguration.load());

  @Test
  void preservesLegacyApplicationMetadataAndChartDataWhenSavingNote() throws Exception {
    String json = "{\"id\":\"note\",\"name\":\"legacy\",\"paragraphs\":[{"
        + "\"id\":\"paragraph\",\"text\":\"%sql select 1\",\"status\":\"FINISHED\","
        + "\"apps\":[{\"id\":\"app_note-paragraph\",\"status\":\"LOADED\","
        + "\"pkg\":{\"type\":\"APPLICATION\",\"name\":\"legacy-app\"},"
        + "\"output\":\"saved output\",\"customMetadata\":{\"version\":1}}],"
        + "\"config\":{\"results\":[{\"graph\":{\"mode\":\"external-chart\","
        + "\"height\":420,\"setting\":{\"external-chart\":{\"color\":\"red\"}}},"
        + "\"helium\":{\"activeApp\":\"app_note-paragraph\"}}]},"
        + "\"results\":{\"code\":\"SUCCESS\",\"msg\":[{\"type\":\"TABLE\","
        + "\"data\":\"value\\n1\"}]}}]}";
    JsonObject original = JsonParser.parseString(json).getAsJsonObject()
        .getAsJsonArray("paragraphs").get(0).getAsJsonObject();

    Note note = parser.fromJson("note", json);
    note.getParagraph("paragraph").setText("%sql select 2");
    Note reloaded = parser.fromJson("note", parser.toJson(note));
    JsonObject saved = JsonParser.parseString(parser.toJson(reloaded)).getAsJsonObject()
        .getAsJsonArray("paragraphs").get(0).getAsJsonObject();

    assertEquals("%sql select 2", reloaded.getParagraph("paragraph").getText());
    assertEquals(original.get("apps"), saved.get("apps"));
    assertEquals(original.get("config"), saved.get("config"));
    assertEquals(original.get("results"), saved.get("results"));
  }

  @Test
  void doesNotAddApplicationMetadataToOrdinaryParagraphs() {
    Paragraph paragraph = parser.fromJson("{\"id\":\"paragraph\",\"text\":\"%sql select 1\"}");
    JsonObject saved = JsonParser.parseString(parser.toJson(paragraph)).getAsJsonObject();

    assertEquals("%sql select 1", paragraph.getText());
    assertFalse(saved.has("apps"));
  }
}
