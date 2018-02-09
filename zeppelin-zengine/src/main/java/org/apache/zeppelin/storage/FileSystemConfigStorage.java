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


package org.apache.zeppelin.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.helium.HeliumConf;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.FileSystemStorage;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.user.CredentialsInfoSaving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * It could be used either local file system or hadoop distributed file system,
 * because FileSystem support both local file system and hdfs.
 *
 */
public class FileSystemConfigStorage extends ConfigStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemConfigStorage.class);

  private FileSystemStorage fs;
  private Path interpreterSettingPath;
  private Path authorizationPath;
  private Path credentialPath;

  public FileSystemConfigStorage(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);
    this.fs = new FileSystemStorage(zConf, zConf.getConfigFSDir());
    LOGGER.info("Creating FileSystem: " + this.fs.getFs().getClass().getName() +
        " for Zeppelin Config");
    Path configPath = this.fs.makeQualified(new Path(zConf.getConfigFSDir()));
    this.fs.tryMkDir(configPath);
    LOGGER.info("Using folder {} to store Zeppelin Config", configPath);
    this.interpreterSettingPath = fs.makeQualified(new Path(zConf.getInterpreterSettingPath()));
    this.authorizationPath = fs.makeQualified(new Path(zConf.getNotebookAuthorizationPath()));
    this.credentialPath = fs.makeQualified(new Path(zConf.getCredentialsPath()));
  }

  @Override
  public void save(InterpreterInfoSaving settingInfos) throws IOException {
    LOGGER.info("Save Interpreter Settings to " + interpreterSettingPath);
    fs.writeFile(settingInfos.toJson(), interpreterSettingPath, false);
  }

  @Override
  public InterpreterInfoSaving loadInterpreterSettings() throws IOException {
    if (!fs.exists(interpreterSettingPath)) {
      LOGGER.warn("Interpreter Setting file {} is not existed", interpreterSettingPath);
      return null;
    }
    LOGGER.info("Load Interpreter Setting from file: " + interpreterSettingPath);
    String json = fs.readFile(interpreterSettingPath);
    //TODO(zjffdu) This kind of post processing is ugly.
    JsonParser jsonParser = new JsonParser();
    JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();
    InterpreterInfoSaving infoSaving = InterpreterInfoSaving.fromJson(json);
    for (InterpreterSetting interpreterSetting : infoSaving.interpreterSettings.values()) {
      // Always use separate interpreter process
      // While we decided to turn this feature on always (without providing
      // enable/disable option on GUI).
      // previously created setting should turn this feature on here.
      interpreterSetting.getOption();
      interpreterSetting.convertPermissionsFromUsersToOwners(
          jsonObject.getAsJsonObject("interpreterSettings")
              .getAsJsonObject(interpreterSetting.getId()));
    }
    return infoSaving;
  }

  public void save(NotebookAuthorizationInfoSaving authorizationInfoSaving) throws IOException {
    LOGGER.info("Save notebook authorization to file: " + authorizationPath);
    fs.writeFile(authorizationInfoSaving.toJson(), authorizationPath, false);
  }

  @Override
  public NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException {
    if (!fs.exists(authorizationPath)) {
      LOGGER.warn("Interpreter Setting file {} is not existed", authorizationPath);
      return null;
    }
    LOGGER.info("Load notebook authorization from file: " + authorizationPath);
    String json = this.fs.readFile(authorizationPath);
    return NotebookAuthorizationInfoSaving.fromJson(json);
  }

  @Override
  public String loadCredentials() throws IOException {
    if (!fs.exists(credentialPath)) {
      LOGGER.warn("Credential file {} is not existed", authorizationPath);
      return null;
    }
    LOGGER.info("Load Credential from file: " + authorizationPath);
    return this.fs.readFile(credentialPath);
  }

  @Override
  public void saveCredentials(String credentials) throws IOException {
    LOGGER.info("Save Credentials to file: " + credentialPath);
    fs.writeFile(credentials, credentialPath, false);
  }

}
