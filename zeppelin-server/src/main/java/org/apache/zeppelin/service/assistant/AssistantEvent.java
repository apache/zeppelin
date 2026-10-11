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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public interface AssistantEvent {

  final class TextDelta implements AssistantEvent {
    public final String delta;

    public TextDelta(String delta) {
      this.delta = delta;
    }
  }

  final class ToolCall implements AssistantEvent {
    private static final Gson GSON = new Gson();

    public final String id;
    public final String name;
    public final Map<String, Object> arguments;

    public ToolCall(String id, String name, String arguments) {
      this.id = id;
      this.name = name;
      this.arguments = StringUtils.isBlank(arguments)
          ? Collections.emptyMap()
          : GSON.fromJson(arguments, new TypeToken<Map<String, Object>>() { }.getType());
    }
  }

  final class Usage implements AssistantEvent {
    public final int inputTokens;
    public final int outputTokens;

    public Usage(int inputTokens, int outputTokens) {
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }
  }
}
