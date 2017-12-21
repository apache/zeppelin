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

package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;

import java.util.Properties;

/**
 * Context class for Interpreter Launch
 */
public class InterpreterLaunchContext {

  private Properties properties;
  private InterpreterOption option;
  private InterpreterRunner runner;
  private String userName;
  private String interpreterGroupId;
  private String interpreterSettingId;
  private String interpreterSettingGroup;
  private String interpreterSettingName;

  public InterpreterLaunchContext(Properties properties,
                                  InterpreterOption option,
                                  InterpreterRunner runner,
                                  String userName,
                                  String interpreterGroupId,
                                  String interpreterSettingId,
                                  String interpreterSettingGroup,
                                  String interpreterSettingName) {
    this.properties = properties;
    this.option = option;
    this.runner = runner;
    this.userName = userName;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterSettingId = interpreterSettingId;
    this.interpreterSettingGroup = interpreterSettingGroup;
    this.interpreterSettingName = interpreterSettingName;
  }

  public Properties getProperties() {
    return properties;
  }

  public InterpreterOption getOption() {
    return option;
  }

  public InterpreterRunner getRunner() {
    return runner;
  }

  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  public String getInterpreterSettingId() {
    return interpreterSettingId;
  }

  public String getInterpreterSettingGroup() {
    return interpreterSettingGroup;
  }

  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  public String getUserName() {
    return userName;
  }
}
