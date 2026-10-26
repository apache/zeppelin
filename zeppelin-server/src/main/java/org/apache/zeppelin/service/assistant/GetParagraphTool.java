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

package org.apache.zeppelin.service.assistant;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.rest.exception.ParagraphNotFoundException;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;
import org.apache.zeppelin.user.AuthenticationInfo;

public class GetParagraphTool implements Tool {

  private static final int MAX_RESULT_LENGTH = 10_000;

  private final NotebookService notebookService;

  public GetParagraphTool(NotebookService notebookService) {
    this.notebookService = notebookService;
  }

  @Override
  public String name() {
    return "get_paragraph";
  }

  @Override
  public String description() {
    return "Get a paragraph's input, status, and latest result (truncated at 10000 chars).";
  }

  @Override
  public Map<String, Object> parameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of("paragraphId", Map.of("type", "string")),
        "required", List.of("paragraphId")
    );
  }

  @Override
  public Map<String, Object> call(
      String noteId,
      Map<String, Object> args,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles
  ) throws IOException {
    Object value = args.get("paragraphId");
    if (!(value instanceof String) || ((String) value).isBlank()) {
      throw new IllegalArgumentException("paragraphId must be a non-blank string");
    }
    var paragraphId = (String) value;
    var context = new ServiceContext(authInfo, userAndRoles);

    return notebookService.getNote(noteId, context, throwOnFailure(), note -> {
      var paragraph = Optional.ofNullable(note.getParagraph(paragraphId))
          .orElseThrow(() -> new ParagraphNotFoundException(paragraphId));

      return Map.of(
          "id", paragraph.getId(),
          "title", StringUtils.defaultString(paragraph.getTitle()),
          "input", StringUtils.defaultString(paragraph.getText()),
          "status", paragraph.getStatus().name(),
          "result", Optional.ofNullable(paragraph.getReturn())
              .map(r -> StringUtils.abbreviate(r.toString(), MAX_RESULT_LENGTH))
              .orElse("")
      );
    });
  }

  private static SimpleServiceCallback<Note> throwOnFailure() {
    return new SimpleServiceCallback<Note>() {
      @Override
      public void onFailure(Exception ex, ServiceContext context) throws IOException {
        if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        }
        throw new IOException(ex);
      }
    };
  }
}
