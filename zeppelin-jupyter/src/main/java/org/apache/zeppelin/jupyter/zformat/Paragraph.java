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
package org.apache.zeppelin.jupyter.zformat;

import com.google.gson.annotations.SerializedName;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class Paragraph {
  public static final String FINISHED = "FINISHED";

  @SerializedName("config")
  private Map<String, Object> config;

  @SerializedName("text")
  private String text;

  @SerializedName("results")
  private Result results; // It's a bit weird name

  @SerializedName("id")
  private String id;

  @SerializedName("status")
  private String status;

  public Paragraph() {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
    this.id = dateFormat.format(new Date()) + "_" + super.hashCode();
    this.status = FINISHED;
    initializeConfig();
  }

  private void initializeConfig() {
    this.config = new HashMap<>();
    this.config.put("editorHide", false);
    this.config.put("editorMode", "ace/mode/python");
  }

  public void setUpMarkdownConfig(boolean toActiveEditOnDblClickMode) {
    Map<String, Object> editorSetting = new HashMap<>();
    editorSetting.put("language", "markdown");
    editorSetting.put("editOnDblClick", toActiveEditOnDblClickMode);
    this.config.put("editorHide", toActiveEditOnDblClickMode);
    this.config.put("editorSetting", editorSetting);
    this.config.put("editorMode", "ace/mode/markdown");
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Result getResults() {
    return results;
  }

  public void setResults(Result results) {
    this.results = results;
  }

  public String getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public Map<String, Object> getConfig() {
    return config;
  }
}
