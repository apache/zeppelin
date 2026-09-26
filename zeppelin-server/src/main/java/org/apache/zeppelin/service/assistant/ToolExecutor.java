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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.zeppelin.user.AuthenticationInfo;
public class ToolExecutor {

  private final Map<String, Tool> byName;

  public ToolExecutor(List<Tool> tools) {
    Map<String, Tool> map = new LinkedHashMap<>();
    for (Tool t : tools) {
      map.put(t.name(), t);
    }
    this.byName = map;
  }

  public List<ToolSpec> specs() {
    return byName.values().stream()
        .map(t -> new ToolSpec(t.name(), t.description(), t.parameters()))
        .collect(Collectors.toList());
  }

  public ToolResult callTool(
      String noteId,
      String name,
      Map<String, Object> args,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles
  ) {
    Tool tool = byName.get(name);

    if (tool == null) return new ToolResult(null, "Unknown tool: " + name);

    try {
      return new ToolResult(tool.call(noteId, args, authInfo, userAndRoles), null);
    } catch (Exception ex) {
      return new ToolResult(null, String.valueOf(ex.getMessage()));
    }
  }
}
