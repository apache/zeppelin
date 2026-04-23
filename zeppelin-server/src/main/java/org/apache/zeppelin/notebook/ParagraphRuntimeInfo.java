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

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Store runtime infos of each para
 *
 */
public class ParagraphRuntimeInfo {
  private String propertyName;  // Name of the property
  private String label;         // Label to be used in UI
  private String tooltip;       // Tooltip text toshow in UI
  private String group;         // The interpretergroup from which the info was derived

  // runtimeInfos job url or dropdown-menu key in
  // zeppelin-web/src/app/notebook/paragraph/paragraph-control.html
  private List<Object> values;  // values for the key-value pair property
  private String interpreterSettingId;
  
  public ParagraphRuntimeInfo(String propertyName, String label, 
      String tooltip, String group, String intpSettingId) {
    if (intpSettingId == null) {
      throw new IllegalArgumentException("Interpreter setting Id cannot be null");
    }
    this.propertyName = propertyName;
    this.label = label;
    this.tooltip = tooltip;
    this.group = group;
    this.interpreterSettingId = intpSettingId;
    this.values = new ArrayList<>();
  }

  public void addValue(Map<String, String> mapValue) {
    values.add(mapValue);
  }

  @VisibleForTesting
  public List<Object> getValue() {
    return values;
  }
  
  public String getInterpreterSettingId() {
    return interpreterSettingId;
  }
}
