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

import java.util.Map;

public class ToolCall {

  private String id;
  private String name;
  private Map<String, Object> arguments;
  private ToolResult result;

  private ToolCall() {}

  public ToolCall(String id, String name, Map<String, Object> arguments) {
    this.id = id;
    this.name = name;
    this.arguments = arguments;
  }

  public String getId() { return id; }
  public String getName() { return name; }
  public Map<String, Object> getArguments() { return arguments; }
  public ToolResult getResult() { return result; }

  public void setResult(ToolResult result) { this.result = result; }
}
