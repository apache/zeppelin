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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.NotebookAuthorizationInfoSaving;
import org.apache.zeppelin.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface for storing zeppelin configuration.
 *
 * 1. interpreter.json
 * 2. notebook-authorization.json
 * 3. credentials.json
 *
 */
public abstract class ConfigStorage {

  protected static final String STORAGE_HEALTHCHECK_NAME = "ConfigStorage";

  protected ZeppelinConfiguration zConf;

  public static ConfigStorage createConfigStorage(ZeppelinConfiguration zConf) throws IOException {
    String configStorageClass =
        zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS);
    try {
      return ReflectionUtils.createClazzInstance(configStorageClass,
          new Class[] {ZeppelinConfiguration.class}, new Object[] {zConf});
    } catch (IOException e) {
      if (!(e.getCause() instanceof ClassNotFoundException)) {
        throw e;
      }
      ConfigStorage configStorage = loadConfigStoragePlugin(zConf, configStorageClass);
      if (configStorage != null) {
        return configStorage;
      }
      throw e;
    }
  }

  private static ConfigStorage loadConfigStoragePlugin(ZeppelinConfiguration zConf,
                                                       String configStorageClass)
      throws IOException {
    String simpleClassName = configStorageClass.substring(configStorageClass.lastIndexOf(".") + 1);
    File pluginFolder = new File(zConf.getPluginsDir() + File.separator
        + "ConfigStorage" + File.separator + simpleClassName);
    if (!pluginFolder.exists() || pluginFolder.isFile()) {
      return null;
    }

    File[] pluginFiles = pluginFolder.listFiles();
    if (pluginFiles == null || pluginFiles.length == 0) {
      return null;
    }

    List<URL> urls = new ArrayList<>();
    for (File pluginFile : pluginFiles) {
      urls.add(pluginFile.toURI().toURL());
    }

    URLClassLoader pluginClassLoader =
        new URLClassLoader(urls.toArray(new URL[0]), ConfigStorage.class.getClassLoader());
    try {
      Class<?> clazz = Class.forName(configStorageClass, true, pluginClassLoader);
      if (!ConfigStorage.class.isAssignableFrom(clazz)) {
        throw new IOException("Class " + configStorageClass + " does not extend "
            + ConfigStorage.class.getName());
      }
      return (ConfigStorage) clazz.getConstructor(ZeppelinConfiguration.class).newInstance(zConf);
    } catch (ClassNotFoundException e) {
      throw new IOException("Unable to load config storage plugin class: " + configStorageClass, e);
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
      throw new IOException("Unable to instantiate config storage plugin: " + configStorageClass, e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException) cause;
      }
      throw new IOException("Unable to instantiate config storage plugin: " + configStorageClass, e);
    }
  }

  protected ConfigStorage(ZeppelinConfiguration zConf) {
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
    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
    InterpreterInfoSaving infoSaving = InterpreterInfoSaving.fromJson(json);
    for (InterpreterSetting interpreterSetting : infoSaving.interpreterSettings.values()) {
      // Always use separate interpreter process
      // While we decided to turn this feature on always (without providing
      // enable/disable option on GUI).
      // previously created setting should turn this feature on here.
      interpreterSetting.getOption();
      JsonObject interpreterSettingJson = jsonObject.getAsJsonObject("interpreterSettings")
          .getAsJsonObject(interpreterSetting.getId());
      List<String> users = InterpreterSetting.extractUsersFromJsonString(interpreterSettingJson.toString());
      interpreterSetting.convertPermissionsFromUsersToOwners(users);
    }
    return infoSaving;
  }
}
