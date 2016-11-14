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
package org.apache.zeppelin.utils;

import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.types.InterpreterSettingsList;

import java.util.LinkedList;
import java.util.List;

/**
 * Utils for interpreter bindings
 */
public class InterpreterBindingUtils {
  public static List<InterpreterSettingsList> getInterpreterBindings(Notebook notebook,
                                                                     String noteId) {
    List<InterpreterSettingsList> settingList = new LinkedList<>();
    List<InterpreterSetting> selectedSettings =
        notebook.getBindedInterpreterSettings(noteId);
    for (InterpreterSetting setting : selectedSettings) {
      settingList.add(new InterpreterSettingsList(setting.getId(), setting.getName(),
          setting.getInterpreterInfos(), true));
    }

    List<InterpreterSetting> availableSettings = notebook.getInterpreterFactory().get();
    for (InterpreterSetting setting : availableSettings) {
      boolean selected = false;
      for (InterpreterSetting selectedSetting : selectedSettings) {
        if (selectedSetting.getId().equals(setting.getId())) {
          selected = true;
          break;
        }
      }

      if (!selected) {
        settingList.add(new InterpreterSettingsList(setting.getId(), setting.getName(),
            setting.getInterpreterInfos(), false));
      }
    }

    return settingList;
  }
}
