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
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Interpreter Launcher which use shell script to launch the interpreter process.
 *
 */
public class ShellScriptLauncher extends InterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShellScriptLauncher.class);

  public ShellScriptLauncher(ZeppelinConfiguration zConf) {
    super(zConf);
  }

  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) {
    LOGGER.info("Launching Interpreter: " + context.getInterpreterGroupName());
    this.properties = context.getProperties();
    InterpreterOption option = context.getOption();
    InterpreterRunner runner = context.getRunner();
    String groupName = context.getInterpreterGroupName();

    int connectTimeout =
        zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    if (option.isExistingProcess()) {
      return new RemoteInterpreterRunningProcess(
          connectTimeout,
          option.getHost(),
          option.getPort());
    } else {
      // create new remote process
      String localRepoPath = zConf.getInterpreterLocalRepoPath() + "/"
          + context.getInterpreterGroupId();
      return new RemoteInterpreterManagedProcess(
          runner != null ? runner.getPath() : zConf.getInterpreterRemoteRunnerPath(),
          zConf.getCallbackPortRange(),
          zConf.getInterpreterDir() + "/" + groupName, localRepoPath,
          buildEnvFromProperties(), connectTimeout, groupName);
    }
  }

  protected Map<String, String> buildEnvFromProperties() {
    Map<String, String> env = new HashMap<>();
    for (Object key : properties.keySet()) {
      if (RemoteInterpreterUtils.isEnvString((String) key)) {
        env.put((String) key, properties.getProperty((String) key));
      }
    }
    return env;
  }
}
