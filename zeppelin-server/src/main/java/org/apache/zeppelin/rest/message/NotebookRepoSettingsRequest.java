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
package org.apache.zeppelin.rest.message;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 * Represent payload of a notebook repo settings.
 */
public class NotebookRepoSettingsRequest {

  public static final NotebookRepoSettingsRequest EMPTY = new NotebookRepoSettingsRequest();

  public String name;
  public Map<String, String> settings;

  public NotebookRepoSettingsRequest() {
    name = StringUtils.EMPTY;
    settings = Collections.emptyMap();
  }

  public boolean isEmpty() {
    return this == EMPTY;
  }

  public static boolean isEmpty(NotebookRepoSettingsRequest repoSetting) {
    if (repoSetting == null) {
      return true;
    }
    return repoSetting.isEmpty();
  }
}
