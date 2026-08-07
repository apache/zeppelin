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
import org.apache.zeppelin.interpreter.RemoteInterpreterEventServer;
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
          RemoteInterpreterEventServer.CallbackRecoveryCredential callbackCredential =
              interpreterSetting.getInterpreterSettingManager().getInterpreterEventServer()
                  .getCallbackRecoveryCredential(interpreterGroup.getId());
          if (callbackCredential == null) {
            LOGGER.warn("Skip recovery data for interpreter group {} because its callback "
                + "credential is unavailable", interpreterGroup.getId());
            continue;
          }
          recoveryData.add(interpreterGroup.getId()
              + "\t" + interpreterProcess.getHost() + ":" + interpreterProcess.getPort()
              + "\t" + callbackCredential.getToken()
              + "\t" + callbackCredential.getRegisteredHost()
              + "\t" + callbackCredential.getRegisteredPort());
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

    int connectTimeout =
            (int) zConf.getTime(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    Properties interpreterProperties =  interpreterSettingManager.getByName(interpreterSettingName).getJavaProperties();
    int connectionPoolSize = Integer.parseInt(interpreterProperties.getProperty(
            ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE.getVarName(),
            ZEPPELIN_INTERPRETER_CONNECTION_POOL_SIZE.getIntValue() + ""));

    Map<String, InterpreterClient> clients = new HashMap<>();

    if (!StringUtils.isBlank(recoveryData)) {
      for (String line : recoveryData.split(System.lineSeparator())) {
        String[] tokens = line.split("\t", -1);
        if (tokens.length < 2) {
          LOGGER.warn("Ignore malformed interpreter recovery record");
          continue;
        }
        String interpreterGroupId = tokens[0];
        String[] hostPort = tokens[1].split(":");

        if (tokens.length < 5
            || StringUtils.isAnyBlank(tokens[2], tokens[3])) {
          LOGGER.warn("Interpreter recovery record for {} has no callback identity; "
              + "skipping it because it cannot be recovered securely",
              interpreterGroupId);
          continue;
        }

        String callbackToken = tokens[2];
        String registeredHost = tokens[3];
        int registeredPort;
        try {
          registeredPort = Integer.parseInt(tokens[4]);
        } catch (NumberFormatException e) {
          LOGGER.warn("Interpreter recovery record for {} has an invalid callback port",
              interpreterGroupId);
          continue;
        }
        if (registeredPort < 1 || registeredPort > 65535) {
          LOGGER.warn("Interpreter recovery record for {} has an invalid callback port",
              interpreterGroupId);
          continue;
        }
        RemoteInterpreterEventServer.CallbackCredentialRegistration callbackRegistration =
            interpreterSettingManager.getInterpreterEventServer().registerCallbackToken(
                interpreterGroupId, callbackToken, registeredHost, registeredPort);

        RemoteInterpreterRunningProcess client = new RemoteInterpreterRunningProcess(
                interpreterSettingName, interpreterGroupId, connectTimeout, connectionPoolSize,
                interpreterSettingManager.getInterpreterEventServer().getHost(),
                interpreterSettingManager.getInterpreterEventServer().getPort(),
                hostPort[0], Integer.parseInt(hostPort[1]), true,
                interpreterSettingManager.getInterpreterEventServer(), callbackRegistration);
        clients.put(interpreterGroupId, client);
        LOGGER.info("Recovering Interpreter Process: " + interpreterGroupId + ", " +
                hostPort[0] + ":" + hostPort[1]);
      }
    }

    return clients;
  }
}
