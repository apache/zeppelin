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
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Launcher for running interpreter in yarn container.
 */
public class YarnInterpreterLauncher extends InterpreterLauncher {

  private static Logger LOGGER = LoggerFactory.getLogger(YarnInterpreterLauncher.class);

  public YarnInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public InterpreterClient launchDirectly(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: {}", context.getInterpreterSettingGroup());
    this.properties = context.getProperties();
    int connectTimeout = getConnectTimeout();

    return new YarnRemoteInterpreterProcess(
            context,
            properties,
            buildEnvFromProperties(context),
            connectTimeout);
  }

  protected Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context) {
    Map<String, String> env = new HashMap<>();
    for (Object key : context.getProperties().keySet()) {
      if (RemoteInterpreterUtils.isEnvString((String) key)) {
        env.put((String) key, context.getProperties().getProperty((String) key));
      }
    }
    env.put("INTERPRETER_GROUP_ID", context.getInterpreterGroupId());
    env.put("ZEPPELIN_INTERPRETER_LAUNCHER", "yarn");
    return env;
  }

}
