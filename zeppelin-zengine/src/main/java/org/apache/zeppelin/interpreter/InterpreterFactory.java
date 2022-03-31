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

import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

/**
 * //TODO(zjffdu) considering to move to InterpreterSettingManager
 *
 * Factory class for creating interpreters.
 *
 */
public class InterpreterFactory implements InterpreterFactoryInterface {

  private final InterpreterSettingManager interpreterSettingManager;

  @Inject
  public InterpreterFactory(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  @Override
  public Interpreter getInterpreter(String replName,
                                    ExecutionContext executionContext)
      throws InterpreterNotFoundException {

    if (StringUtils.isBlank(replName)) {
      // Get the default interpreter of the defaultInterpreterSetting
      InterpreterSetting defaultSetting =
          interpreterSettingManager.getByName(executionContext.getDefaultInterpreterGroup());
      return defaultSetting.getDefaultInterpreter(executionContext);
    }

    if ("run".equals(replName)) {
      return new RunNotebookInterpreter(interpreterSettingManager);
    }

    String[] replNameSplits = replName.split("\\.");
    if (replNameSplits.length == 2) {
      String group = replNameSplits[0];
      String name = replNameSplits[1];
      InterpreterSetting setting = interpreterSettingManager.getByName(group);
      if (null != setting) {
        Interpreter interpreter = setting.getInterpreter(executionContext, name);
        if (null != interpreter) {
          return interpreter;
        }
        throw new InterpreterNotFoundException("No such interpreter: " + replName);
      }
      throw new InterpreterNotFoundException("No interpreter setting named: " + group);

    } else if (replNameSplits.length == 1){
      // first assume group is omitted
      InterpreterSetting setting =
          interpreterSettingManager.getByName(executionContext.getDefaultInterpreterGroup());
      if (setting != null) {
        Interpreter interpreter = setting.getInterpreter(executionContext, replName);
        if (null != interpreter) {
          return interpreter;
        }
      }

      // then assume interpreter name is omitted
      setting = interpreterSettingManager.getByName(replName);
      if (null != setting) {
        return setting.getDefaultInterpreter(executionContext);
      }
    }

    throw new InterpreterNotFoundException("No such interpreter: " + replName);
  }
}
