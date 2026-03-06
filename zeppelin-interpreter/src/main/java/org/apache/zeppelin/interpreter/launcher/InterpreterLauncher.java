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

import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component to Launch interpreter process.
 */
public abstract class InterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterLauncher.class);
  private static final String SPECIAL_CHARACTER="{}()<>&*'|=?;[]$–#~!.\"%/\\:+,`";

  private static final String CONNECT_TIMEOUT_KEY = "zeppelin.interpreter.connect.timeout";
  private static final long DEFAULT_CONNECT_TIMEOUT = 600000L;

  private static final String CONNECTION_POOL_SIZE_KEY = "zeppelin.interpreter.connection.poolsize";
  private static final int DEFAULT_CONNECTION_POOL_SIZE = 100;

  private static final String RECOVERY_ENABLED_KEY = "zeppelin.recovery.storage.class";
  private static final String NULL_RECOVERY_CLASS =
      "org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage";

  protected final Properties zProperties;
  protected final RecoveryStorage recoveryStorage;

  protected InterpreterLauncher(Properties zProperties, RecoveryStorage recoveryStorage) {
    this.zProperties = zProperties;
    this.recoveryStorage = recoveryStorage;
  }

  /**
   * The timeout setting in interpreter setting take precedence over
   * that in zeppelin-site.xml
   * @return
   */
  protected int getConnectTimeout(InterpreterLaunchContext context) {
    int connectTimeout = (int) Long.parseLong(
        zProperties.getProperty(CONNECT_TIMEOUT_KEY,
            String.valueOf(DEFAULT_CONNECT_TIMEOUT)));
    Properties properties = context.getProperties();
    if (properties != null && properties.containsKey(CONNECT_TIMEOUT_KEY)) {
      connectTimeout = Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT_KEY));
    }
    return connectTimeout;
  }

  protected int getConnectPoolSize(InterpreterLaunchContext context) {
    return Integer.parseInt(context.getProperties().getProperty(
            CONNECTION_POOL_SIZE_KEY,
            String.valueOf(DEFAULT_CONNECTION_POOL_SIZE)));
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
    if (isRecoveryEnabled()) {
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

  private boolean isRecoveryEnabled() {
    String recoveryClass = zProperties.getProperty(RECOVERY_ENABLED_KEY, NULL_RECOVERY_CLASS);
    return !NULL_RECOVERY_CLASS.equals(recoveryClass);
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
