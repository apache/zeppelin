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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;

import java.io.IOException;
import java.util.Properties;

/**
 * Component to Launch interpreter process.
 */
public abstract class InterpreterLauncher {

  protected ZeppelinConfiguration zConf;
  protected Properties properties;
  protected RecoveryStorage recoveryStorage;

  public InterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    this.zConf = zConf;
    this.recoveryStorage = recoveryStorage;
  }

  public void setProperties(Properties props) {
    this.properties = props;
  }

  protected int getConnectTimeout() {
    int connectTimeout =
        zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    if (properties != null && properties.containsKey(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName())) {
      connectTimeout = Integer.parseInt(properties.getProperty(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName()));
    }
    return connectTimeout;
  }

  public abstract InterpreterClient launch(InterpreterLaunchContext context) throws IOException;
}
