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

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * RecoveryStorage implementation based on java native local file system.
 */
public class LocalRecoveryStorage extends RecoveryStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalRecoveryStorage.class);

  private InterpreterSettingManager interpreterSettingManager;
  private File recoveryDir;

  public LocalRecoveryStorage(ZeppelinConfiguration zConf) {
    super(zConf);
  }

  public LocalRecoveryStorage(ZeppelinConfiguration zConf,
                              InterpreterSettingManager interpreterSettingManager)
          throws IOException {
    super(zConf);
    this.recoveryDir = new File(zConf.getRecoveryDir());
    LOGGER.info("Using folder {} to store recovery data", recoveryDir);
    if (!this.recoveryDir.exists()) {
      FileUtils.forceMkdir(this.recoveryDir);
    }
    if (!this.recoveryDir.isDirectory()) {
      throw new IOException("Recovery dir " + this.recoveryDir.getAbsolutePath() + " is not a directory");
    }
    this.interpreterSettingManager = interpreterSettingManager;
  }

  @Override
  public void onInterpreterClientStart(InterpreterClient client) throws IOException {
    save(client.getInterpreterSettingName());
  }

  @Override
  public void onInterpreterClientStop(InterpreterClient client) throws IOException {
    save(client.getInterpreterSettingName());
  }

  @Override
  public Map<String, InterpreterClient> restore() throws IOException {
    Map<String, InterpreterClient> clients = new HashMap<>();
    File[] recoveryFiles = recoveryDir.listFiles(file -> file.getName().endsWith(".recovery"));
    for (File recoveryFile : recoveryFiles) {
      String fileName = recoveryFile.getName();
      String interpreterSettingName = fileName.substring(0,
              fileName.length() - ".recovery".length());
      String recoveryData = org.apache.zeppelin.util.FileUtils.readFromFile(recoveryFile);
      clients.putAll(RecoveryUtils.restoreFromRecoveryData(
              recoveryData, interpreterSettingName, interpreterSettingManager, zConf));
    }

    return clients;
  }

  private void save(String interpreterSettingName) throws IOException {
    InterpreterSetting interpreterSetting =
            interpreterSettingManager.getInterpreterSettingByName(interpreterSettingName);
    String recoveryData = RecoveryUtils.getRecoveryData(interpreterSetting);
    LOGGER.debug("Updating recovery data of {}: {}", interpreterSettingName, recoveryData);
    File recoveryFile = new File(recoveryDir, interpreterSettingName + ".recovery");
    org.apache.zeppelin.util.FileUtils.atomicWriteToFile(recoveryData, recoveryFile);
  }
}
