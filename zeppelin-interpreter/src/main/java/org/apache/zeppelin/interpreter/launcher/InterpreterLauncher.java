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

import java.io.IOException;
import java.util.Properties;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component to Launch interpreter process.
 */
public abstract class InterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterLauncher.class);
  private static final String SPECIAL_CHARACTER="{}()<>&*‘|=?;[]$–#~!.\"%/\\:+,`";

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

  /**
   * The timeout setting in interpreter setting take precedence over
   * that in zeppelin-site.xml
   * @return
   */
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

  public static String escapeSpecialCharacter(String command) {
    StringBuilder builder = new StringBuilder();
    for (char c : command.toCharArray()) {
      if (SPECIAL_CHARACTER.indexOf(c) != -1) {
        builder.append("\\");
      }
      builder.append(c);
    }
    return builder.toString();
  }

  /**
   * Try to recover interpreter process first, then call launchDirectly via sub class implementation.
   *
   * @param context
   * @return
   * @throws IOException
   */
  public InterpreterClient launch(InterpreterLaunchContext context) throws IOException {
    // try to recover it first
    if (zConf.isRecoveryEnabled()) {
      InterpreterClient recoveredClient =
              recoveryStorage.getInterpreterClient(context.getInterpreterGroupId());
      if (recoveredClient != null) {
        if (recoveredClient.isRunning()) {
          LOGGER.info("Recover interpreter process running at {}:{} of interpreter group: {}",
                  recoveredClient.getHost(), recoveredClient.getPort(),
                  recoveredClient.getInterpreterGroupId());
          return recoveredClient;
        } else {
          recoveryStorage.removeInterpreterClient(context.getInterpreterGroupId());
          LOGGER.warn("Unable to recover interpreter process: {}:{}, as it is already terminated.", recoveredClient.getHost(), recoveredClient.getPort());
        }
      }
    }

    // launch it via sub class implementation without recovering.
    return launchDirectly(context);
  }

  /**
   * launch interpreter process directly without recovering.
   *
   * @param context
   * @return
   * @throws IOException
   */
  public abstract InterpreterClient launchDirectly(InterpreterLaunchContext context) throws IOException;

}
