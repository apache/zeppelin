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
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.notebook.FileSystemStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Hadoop compatible FileSystem based RecoveryStorage implementation.
 * All the running interpreter process info will be save into files on hdfs.
 * Each interpreter setting will have one file.
 *
 * Save InterpreterProcess in the format of:
 * InterpreterGroupId host:port
 */
public class FileSystemRecoveryStorage extends RecoveryStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemRecoveryStorage.class);

  private FileSystemStorage fs;
  private Path recoveryDir;
  private InterpreterSettingManager interpreterSettingManager;

  public FileSystemRecoveryStorage(ZeppelinConfiguration zConf,
                                   InterpreterSettingManager interpreterSettingManager)
      throws IOException {
    super(zConf);
    this.interpreterSettingManager = interpreterSettingManager;
    this.fs = new FileSystemStorage(zConf, zConf.getRecoveryDir());
    LOGGER.info("Creating FileSystem: " + this.fs.getFs().getClass().getName() +
        " for Zeppelin Recovery.");
    this.recoveryDir = this.fs.makeQualified(new Path(zConf.getRecoveryDir()));
    LOGGER.info("Using folder {} to store recovery data", recoveryDir);
    this.fs.tryMkDir(recoveryDir);
  }

  @Override
  public void onInterpreterClientStart(InterpreterClient client) throws IOException {
    save(client.getInterpreterSettingName());
  }

  @Override
  public void onInterpreterClientStop(InterpreterClient client) throws IOException {
    save(client.getInterpreterSettingName());
  }

  private void save(String interpreterSettingName) throws IOException {
    InterpreterSetting interpreterSetting =
        interpreterSettingManager.getInterpreterSettingByName(interpreterSettingName);
    List<String> recoveryContent = new ArrayList<>();
    if (interpreterSetting != null) {
      for (ManagedInterpreterGroup interpreterGroup : interpreterSetting.getAllInterpreterGroups()) {
        RemoteInterpreterProcess interpreterProcess = interpreterGroup.getInterpreterProcess();
        if (interpreterProcess != null && interpreterProcess.isRunning()) {
          recoveryContent.add(interpreterGroup.getId() + "\t" + interpreterProcess.getHost() + ":" +
                  interpreterProcess.getPort());
        }
      }
    }
    String recoveryContentStr = StringUtils.join(recoveryContent, System.lineSeparator());
    LOGGER.debug("Updating recovery data of {}: {}", interpreterSettingName, recoveryContentStr);
    Path recoveryFile = new Path(recoveryDir, interpreterSettingName + ".recovery");
    fs.writeFile(recoveryContentStr, recoveryFile, true);
  }

  @Override
  public Map<String, InterpreterClient> restore() throws IOException {
    Map<String, InterpreterClient> clients = new HashMap<>();
    List<Path> paths = fs.list(new Path(recoveryDir + "/*.recovery"));

    for (Path path : paths) {
      String fileName = path.getName();
      String interpreterSettingName = fileName.substring(0,
          fileName.length() - ".recovery".length());
      String recoveryContent = fs.readFile(path);
      if (!StringUtils.isBlank(recoveryContent)) {
        for (String line : recoveryContent.split(System.lineSeparator())) {
          String[] tokens = line.split("\t");
          String interpreterGroupId = tokens[0];
          String[] hostPort = tokens[1].split(":");
          int connectTimeout =
                  zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
          RemoteInterpreterRunningProcess client = new RemoteInterpreterRunningProcess(
              interpreterSettingName, interpreterGroupId, connectTimeout,
                  interpreterSettingManager.getInterpreterEventServer().getHost(),
                  interpreterSettingManager.getInterpreterEventServer().getPort(),
                  hostPort[0], Integer.parseInt(hostPort[1]), true);
          clients.put(interpreterGroupId, client);
          LOGGER.info("Recovering Interpreter Process: " + interpreterGroupId + ", " +
                  hostPort[0] + ":" + hostPort[1]);
        }
      }
    }

    return clients;
  }
}
