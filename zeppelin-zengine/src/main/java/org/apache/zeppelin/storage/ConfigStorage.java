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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.util.ReflectionUtils;

import java.io.IOException;

/**
 * Interface for storing zeppelin configuration.
 *
 * 1. interpreter-setting.json
 * 2. helium.json
 * 3. notebook-authorization.json
 * 4. credentials.json
 *
 */
public abstract class ConfigStorage {

  private static ConfigStorage instance;

  protected ZeppelinConfiguration zConf;

  public static synchronized ConfigStorage getInstance(ZeppelinConfiguration zConf)
      throws IOException {
    if (instance == null) {
      instance = createConfigStorage(zConf);
    }
    return instance;
  }

  private static ConfigStorage createConfigStorage(ZeppelinConfiguration zConf) throws IOException {
    String configStorageClass =
        zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS);
    return ReflectionUtils.createClazzInstance(configStorageClass,
        new Class[] {ZeppelinConfiguration.class}, new Object[] {zConf});
  }


  public ConfigStorage(ZeppelinConfiguration zConf) {
    this.zConf = zConf;
  }

  public abstract void save(InterpreterInfoSaving settingInfos) throws IOException;

  public abstract InterpreterInfoSaving loadInterpreterSettings() throws IOException;

  public abstract void save(NotebookAuthorizationInfoSaving authorizationInfoSaving)
      throws IOException;

  public abstract NotebookAuthorizationInfoSaving loadNotebookAuthorization() throws IOException;

  public abstract String loadCredentials() throws IOException;

  public abstract void saveCredentials(String credentials) throws IOException;

  protected InterpreterInfoSaving buildInterpreterInfoSaving(String json) {
    //TODO(zjffdu) This kind of post processing is ugly.
    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
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

  @VisibleForTesting
  public static void reset() {
    instance = null;
  }
}
