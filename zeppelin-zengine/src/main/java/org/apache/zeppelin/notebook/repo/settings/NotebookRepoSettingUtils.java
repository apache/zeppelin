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
package org.apache.zeppelin.notebook.repo.settings;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * NotebookRepo settings related utils.
 *
 */
public class NotebookRepoSettingUtils {
  public static final String PERSIST_ON_COMMIT_NAME = "Note Persistence";
  private static final String PERSIST_ON_COMMIT_OPTION1 = "Persist continuously";
  private static final String PERSIST_ON_COMMIT_OPTION2 = "Persist on note commits";
  
  // note persist setting
  public static NotebookRepoSettingsInfo getNotePersistSettings(boolean persistOnCommit) {
    NotebookRepoSettingsInfo repoSetting = NotebookRepoSettingsInfo.newInstance();
    List<Map<String, String>> values = Lists.newLinkedList();
    repoSetting.type = NotebookRepoSettingsInfo.Type.DROPDOWN;
    values = Lists.newLinkedList();
    values.add(ImmutableMap.of("name", PERSIST_ON_COMMIT_OPTION1, "value", "false"));
    values.add(ImmutableMap.of("name", PERSIST_ON_COMMIT_OPTION2, "value", "true"));
    repoSetting.value = values;
    repoSetting.selected  = Boolean.toString(persistOnCommit);
    repoSetting.name = PERSIST_ON_COMMIT_NAME;
    return repoSetting;
  }
  
  public static boolean requiresReload(NotebookRepoWithSettings repoSetting) {
    if (repoSetting.isEmpty()) {
      return false;
    }
    boolean reloadRequired = false;
    for (NotebookRepoSettingsInfo setting: repoSetting.settings) {
      if (setting.reload) {
        reloadRequired = setting.reload;
        break;
      }
    }
    
    return reloadRequired;
  }
}
