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

package org.apache.zeppelin.user;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.storage.ConfigStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Class defining credentials for data source authorization
 */
public class Credentials {

  private static final Logger LOGGER = LoggerFactory.getLogger(Credentials.class);

  private ConfigStorage storage;
  private Map<String, UserCredentials> credentialsMap;
  private Gson gson;
  private Encryptor encryptor;

  /**
   * Wrapper for user credentials. It can load credentials from a file
   * and will encrypt the file if an encryptKey is configured.
   *
   * @param conf
   * @throws IOException
   */
  public Credentials(ZeppelinConfiguration conf) {
    credentialsMap = new HashMap<>();
    if (conf.credentialsPersist()) {
      String encryptKey = conf.getCredentialsEncryptKey();
      if (StringUtils.isNotBlank(encryptKey)) {
        this.encryptor = new Encryptor(encryptKey);
      }
      try {
        storage = ConfigStorage.getInstance(conf);
        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        gson = builder.create();
        loadFromFile();
      } catch (IOException e) {
        LOGGER.error("Fail to create ConfigStorage for Credentials. Persistenz will be disabled", e);
        encryptor = null;
        storage = null;
        gson = null;
      }
    } else {
      encryptor = null;
      storage = null;
      gson = null;
    }
  }

  /**
   * Wrapper for inmemory user credentials.
   *
   * @param conf
   * @throws IOException
   */
  public Credentials() {
    credentialsMap = new HashMap<>();
    encryptor = null;
    storage = null;
    gson = null;
  }

  public UserCredentials getUserCredentials(String username) throws IOException {
    UserCredentials uc = credentialsMap.get(username);
    if (uc == null) {
      uc = new UserCredentials();
    }
    return uc;
  }

  public void putUserCredentials(String username, UserCredentials uc) throws IOException {
    loadCredentials();
    credentialsMap.put(username, uc);
    saveCredentials();
  }

  public UserCredentials removeUserCredentials(String username) throws IOException {
    loadCredentials();
    UserCredentials uc = credentialsMap.remove(username);
    saveCredentials();
    return uc;
  }

  public boolean removeCredentialEntity(String username, String entity) throws IOException {
    loadCredentials();
    UserCredentials uc = credentialsMap.get(username);
    if (uc == null || !uc.existUsernamePassword(entity)) {
      return false;
    }

    uc.removeUsernamePassword(entity);
    saveCredentials();
    return true;
  }

  public void saveCredentials() throws IOException {
    if (storage != null) {
      saveToFile();
    }
  }

  private void loadCredentials() throws IOException {
    if (storage != null) {
      loadFromFile();
    }
  }

  private void loadFromFile() throws IOException {
    try {
      String json = storage.loadCredentials();
      if (json != null && encryptor != null) {
        json = encryptor.decrypt(json);
      }

      CredentialsInfoSaving info = CredentialsInfoSaving.fromJson(json);
      if (info != null) {
        this.credentialsMap = info.credentialsMap;
      }
    } catch (IOException e) {
      throw new IOException("Error loading credentials file", e);
    }
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (credentialsMap) {
      CredentialsInfoSaving info = new CredentialsInfoSaving();
      info.credentialsMap = credentialsMap;
      jsonString = gson.toJson(info);
    }

    try {
      if (encryptor != null) {
        jsonString = encryptor.encrypt(jsonString);
      }
      storage.saveCredentials(jsonString);
    } catch (IOException e) {
      throw new IOException("Error saving credentials file", e);
    }
  }
}
