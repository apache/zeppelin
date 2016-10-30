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
package org.apache.zeppelin.notebook.repo;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Representation of a notebook repo with settings. This is mostly a Wrapper around notebook repo
 * information plus settings.
 */
public class NotebookRepoWithSettings {

  public static final NotebookRepoWithSettings EMPTY =
      NotebookRepoWithSettings.builder(StringUtils.EMPTY).build();

  public String name;
  public String className;
  public List<NotebookRepoSettingsInfo> settings;

  private NotebookRepoWithSettings() {}

  public static Builder builder(String name) {
    return new Builder(name);
  }

  private NotebookRepoWithSettings(Builder builder) {
    name = builder.name;
    className = builder.className;
    settings = builder.settings;
  }

  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  /**
   * Simple builder :).
   */
  public static class Builder {
    private final String name;
    private String className = StringUtils.EMPTY;
    private List<NotebookRepoSettingsInfo> settings = Collections.emptyList();

    public Builder(String name) {
      this.name = name;
    }

    public NotebookRepoWithSettings build() {
      return new NotebookRepoWithSettings(this);
    }

    public Builder className(String className) {
      this.className = className;
      return this;
    }

    public Builder settings(List<NotebookRepoSettingsInfo> settings) {
      this.settings = settings;
      return this;
    }
  }
}
