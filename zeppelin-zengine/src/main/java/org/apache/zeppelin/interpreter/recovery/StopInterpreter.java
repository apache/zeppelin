/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.recovery;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;


/**
 * Utility class for stopping interpreter in the case that you want to stop all the
 * interpreter process even when you enable recovery, or you want to kill interpreter process
 * to avoid orphan process.
 */
public class StopInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(StopInterpreter.class);

  public static void main(String[] args) throws IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    InterpreterSettingManager interpreterSettingManager =
            new InterpreterSettingManager(zConf, null, null, null);

    RecoveryStorage recoveryStorage  = ReflectionUtils.createClazzInstance(zConf.getRecoveryStorageClass(),
        new Class[] {ZeppelinConfiguration.class, InterpreterSettingManager.class},
        new Object[] {zConf, interpreterSettingManager});

    LOGGER.info("Using RecoveryStorage: {}", recoveryStorage.getClass().getName());
    Map<String, InterpreterClient> restoredClients = recoveryStorage.restore();
    if (restoredClients != null) {
      for (InterpreterClient client : restoredClients.values()) {
        LOGGER.info("Stop Interpreter Process: {}:{}", client.getHost(), client.getPort());
        client.stop();
      }
    }
  }
}
