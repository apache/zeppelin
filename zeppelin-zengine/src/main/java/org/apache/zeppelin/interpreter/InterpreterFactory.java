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

package org.apache.zeppelin.interpreter;

import com.google.common.base.Preconditions;
import javax.inject.Inject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * //TODO(zjffdu) considering to move to InterpreterSettingManager
 *
 * Factory class for creating interpreters.
 *
 */
public class InterpreterFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterFactory.class);

  private final InterpreterSettingManager interpreterSettingManager;

  @Inject
  public InterpreterFactory(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public Interpreter getInterpreter(String user,
                                    String noteId,
                                    String replName,
                                    String defaultInterpreterSetting)
      throws InterpreterNotFoundException {

    if (StringUtils.isBlank(replName)) {
      // Get the default interpreter of the defaultInterpreterSetting
      InterpreterSetting defaultSetting =
          interpreterSettingManager.getByName(defaultInterpreterSetting);
      return defaultSetting.getDefaultInterpreter(user, noteId);
    }

    String[] replNameSplits = replName.split("\\.");
    if (replNameSplits.length == 2) {
      String group = replNameSplits[0];
      String name = replNameSplits[1];
      InterpreterSetting setting = interpreterSettingManager.getByName(group);
      if (null != setting) {
        Interpreter interpreter = setting.getInterpreter(user, noteId, name);
        if (null != interpreter) {
          return interpreter;
        }
        throw new InterpreterNotFoundException("No such interpreter: " + replName);
      }
      throw new InterpreterNotFoundException("No interpreter setting named: " + group);

    } else if (replNameSplits.length == 1){
      // first assume group is omitted
      InterpreterSetting setting =
          interpreterSettingManager.getByName(defaultInterpreterSetting);
      if (setting != null) {
        Interpreter interpreter = setting.getInterpreter(user, noteId, replName);
        if (null != interpreter) {
          return interpreter;
        }
      }

      // then assume interpreter name is omitted
      setting = interpreterSettingManager.getByName(replName);
      if (null != setting) {
        return setting.getDefaultInterpreter(user, noteId);
      }
    }

    throw new InterpreterNotFoundException("No such interpreter: " + replName);
  }
}
