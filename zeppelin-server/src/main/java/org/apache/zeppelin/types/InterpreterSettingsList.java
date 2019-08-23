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
package org.apache.zeppelin.types;

import org.apache.zeppelin.interpreter.InterpreterInfo;

import java.util.List;

/**
 * InterpreterSetting information for binding.
 */
public class InterpreterSettingsList {
  private String id;
  private String name;
  private List<InterpreterInfo> interpreters;
  private boolean selected;

  public InterpreterSettingsList(String id, String name,
                                 List<InterpreterInfo> interpreters, boolean selected) {
    this.id = id;
    this.name = name;
    this.interpreters = interpreters;
    this.selected = selected;
  }
}
