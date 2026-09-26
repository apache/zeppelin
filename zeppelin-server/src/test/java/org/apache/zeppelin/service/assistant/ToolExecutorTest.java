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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.Test;

class ToolExecutorTest {
  private final AuthenticationInfo authInfo = AuthenticationInfo.ANONYMOUS;
  private final Set<String> userAndRoles = Set.of("user");

  @Test
  void exposesTools() {
    var tool = new EchoTool();
    var sut = new ToolExecutor(List.of(tool));
    var specs = sut.specs();
    assertEquals(1, specs.size());
    assertEquals(tool.name(), specs.get(0).name);
    assertEquals(tool.description(), specs.get(0).description);
    assertEquals(tool.parameters(), specs.get(0).parameters);
  }

  @Test
  void callsTool() {
    var sut = new ToolExecutor(List.of(new EchoTool()));
    var args = Map.<String, Object>of("text", "hello");
    var result = sut.callTool("noteId", "echo", args, authInfo, userAndRoles);
    assertNull(result.error);
    assertEquals(args, result.value);
  }

  @Test
  void rejectsUnknownTool() {
    var sut = new ToolExecutor(List.of(new EchoTool()));
    var result = sut.callTool("noteId", "unknown_tool", Map.of(), authInfo, userAndRoles);
    assertNotNull(result.error);
  }

  @Test
  void returnsToolError() {
    var tool = new EchoTool() {
      @Override
      public Object call(String noteId, Map<String, Object> args, AuthenticationInfo authInfo,
          Set<String> userAndRoles) throws IOException {
        throw new IOException("failed");
      }
    };
    var sut = new ToolExecutor(List.of(tool));
    var result = sut.callTool("noteId", "echo", Map.of(), authInfo, userAndRoles);
    assertEquals("failed", result.error);
    assertNull(result.value);
  }

  private static class EchoTool implements Tool {
    @Override
    public String name() {
      return "echo";
    }

    @Override
    public String description() {
      return "Return the arguments.";
    }

    @Override
    public Map<String, Object> parameters() {
      return Map.of("type", "object");
    }

    @Override
    public Object call(String noteId, Map<String, Object> args, AuthenticationInfo authInfo,
        Set<String> userAndRoles) throws IOException {
      return args;
    }
  }
}
