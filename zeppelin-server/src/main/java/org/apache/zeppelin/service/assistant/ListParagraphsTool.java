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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.user.AuthenticationInfo;

import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;

public class ListParagraphsTool implements Tool {

  private final NotebookService notebookService;

  public ListParagraphsTool(NotebookService notebookService) {
    this.notebookService = notebookService;
  }

  @Override
  public String name() {
    return "list_paragraphs";
  }

  @Override
  public String description() {
    return "List notebook paragraphs.";
  }

  @Override
  public Map<String, Object> parameters() {
    return Map.of("type", "object", "properties", Map.of());
  }

  @Override
  public List<Map<String, Object>> call(
      String noteId,
      Map<String, Object> args,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles
  ) throws IOException {
    ServiceContext context = new ServiceContext(authInfo, userAndRoles);
    return notebookService.getNote(noteId, context, callback(), note -> {
      List<Map<String, Object>> result = new ArrayList<>();
      for (int i = 0; i < note.getParagraphCount(); i++) {
        Paragraph p = note.getParagraph(i);
        Map<String, Object> info = new HashMap<>();
        info.put("id", p.getId());
        info.put("title", p.getTitle());
        info.put("text", StringUtils.abbreviate(p.getText(), 500));
        info.put("index", i);
        result.add(info);
      }
      return result;
    });
  }

  private static <T> SimpleServiceCallback<T> callback() {
    return new SimpleServiceCallback<T>() {
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
