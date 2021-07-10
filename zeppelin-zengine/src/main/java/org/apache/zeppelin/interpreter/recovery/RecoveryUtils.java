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


package org.apache.zeppelin.interpreter.recovery;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE;

public class RecoveryUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryUtils.class);

  /**
   * Get the recovery data of this interpreterSetting.
   * It contains all the metadata of running interpreter processes under this interpreterSetting.
   *
   * @param interpreterSetting
   * @return
   */
  public static String getRecoveryData(InterpreterSetting interpreterSetting) {
    List<String> recoveryData = new ArrayList<>();
    if (interpreterSetting != null) {
      for (ManagedInterpreterGroup interpreterGroup : interpreterSetting.getAllInterpreterGroups()) {
        RemoteInterpreterProcess interpreterProcess = interpreterGroup.getInterpreterProcess();
        if (interpreterProcess != null && interpreterProcess.isRunning()) {
          recoveryData.add(interpreterGroup.getId() + "\t" + interpreterProcess.getHost() + ":" +
                  interpreterProcess.getPort());
        }
      }
    }
    return StringUtils.join(recoveryData, System.lineSeparator());
  }

  /**
   * Return interpreterClient from recoveryData of one interpreterSetting.
   *
   * @param recoveryData
   * @param interpreterSettingName
   * @param interpreterSettingManager
   * @param zConf
   * @return
   */
  public static Map<String, InterpreterClient> restoreFromRecoveryData(String recoveryData,
                                                                       String interpreterSettingName,
                                                                       InterpreterSettingManager interpreterSettingManager,
                                                                       ZeppelinConfiguration zConf) {
    Map<String, InterpreterClient> clients = new HashMap<>();
    int connectTimeout =
            zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    InterpreterSetting interpreterSetting =  interpreterSettingManager.getByName(interpreterSettingName);
    if (interpreterSetting == null) {
      LOGGER.warn("Unable to recover interpreter process of " + interpreterSettingName +
              ", because there's no such interpreter setting, maybe it is removed");
      return clients;
    }
    Properties interpreterProperties = interpreterSetting.getJavaProperties();
    int connectionPoolSize = Integer.parseInt(interpreterProperties.getProperty(
            ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE.getVarName(),
            ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE.getIntValue() + ""));

    if (!StringUtils.isBlank(recoveryData)) {
      for (String line : recoveryData.split(System.lineSeparator())) {
        String[] tokens = line.split("\t");
        String interpreterGroupId = tokens[0];
        String[] hostPort = tokens[1].split(":");

        RemoteInterpreterRunningProcess client = new RemoteInterpreterRunningProcess(
                interpreterSettingName, interpreterGroupId, connectTimeout, connectionPoolSize,
                interpreterSettingManager.getInterpreterEventServer().getHost(),
                interpreterSettingManager.getInterpreterEventServer().getPort(),
                hostPort[0], Integer.parseInt(hostPort[1]), true);
        clients.put(interpreterGroupId, client);
        LOGGER.info("Recovering Interpreter Process: " + interpreterGroupId + ", " +
                hostPort[0] + ":" + hostPort[1]);
      }
    }

    return clients;
  }
}
