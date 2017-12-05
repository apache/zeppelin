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

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventPoller;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.notebook.FileSystemStorage;
import org.apache.zeppelin.notebook.repo.FileSystemNotebookRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Hadoop compatible FileSystem based RecoveryStorage implementation.
 *
 * Save InterpreterProcess in the format of:
 * InterpreterGroupId host:port
 */
public class FileSystemRecoveryStorage extends RecoveryStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemRecoveryStorage.class);

  private InterpreterSettingManager interpreterSettingManager;
  private FileSystemStorage fs;
  private Path recoveryDir;

  public FileSystemRecoveryStorage(ZeppelinConfiguration zConf,
                                   InterpreterSettingManager interpreterSettingManager)
      throws IOException {
    super(zConf);
    this.interpreterSettingManager = interpreterSettingManager;
    this.zConf = zConf;
    this.fs = FileSystemStorage.get(zConf);
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
    for (ManagedInterpreterGroup interpreterGroup : interpreterSetting.getAllInterpreterGroups()) {
      RemoteInterpreterProcess interpreterProcess = interpreterGroup.getInterpreterProcess();
      if (interpreterProcess != null) {
        recoveryContent.add(interpreterGroup.getId() + "\t" + interpreterProcess.getHost() + ":" +
            interpreterProcess.getPort());
      }
    }
    LOGGER.debug("Updating recovery data for interpreterSetting: " + interpreterSettingName);
    LOGGER.debug("Recovery Data: " + StringUtils.join(recoveryContent, System.lineSeparator()));
    Path recoveryFile = new Path(recoveryDir, interpreterSettingName + ".recovery");
    fs.writeFile(StringUtils.join(recoveryContent, System.lineSeparator()), recoveryFile, true);
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
          String groupId = tokens[0];
          String[] hostPort = tokens[1].split(":");
          int connectTimeout =
              zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
          RemoteInterpreterRunningProcess client = new RemoteInterpreterRunningProcess(
              interpreterSettingName, connectTimeout, hostPort[0], Integer.parseInt(hostPort[1]));
          // interpreterSettingManager may be null when this class is used when it is used
          // stop-interpreter.sh
          if (interpreterSettingManager != null) {
            client.setRemoteInterpreterEventPoller(new RemoteInterpreterEventPoller(
                interpreterSettingManager.getRemoteInterpreterProcessListener(),
                interpreterSettingManager.getAppEventListener()));
          }
          clients.put(groupId, client);
          LOGGER.info("Recovering Interpreter Process: " + hostPort[0] + ":" + hostPort[1]);
        }
      }
    }

    return clients;
  }
}
