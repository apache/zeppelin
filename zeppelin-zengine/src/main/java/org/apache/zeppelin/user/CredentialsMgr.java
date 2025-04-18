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
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.storage.ConfigStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.inject.Inject;

/**
 * Class defining credentials for data source authorization
 */
public class CredentialsMgr {

  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsMgr.class);

  private final ZeppelinConfiguration zConf;

  private ConfigStorage storage;
  private Credentials credentials;
  private final Gson gson;
  private final Encryptor encryptor;

  /**
   * Wrapper for user credentials. It can load credentials from a file
   * and will encrypt the file if an encryptKey is configured.
   *
   * @param zConf
   * @throws IOException
   */
  @Inject
  public CredentialsMgr(ZeppelinConfiguration zConf, ConfigStorage storage) {
    this.zConf = zConf;
    credentials = new Credentials();
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    this.gson = builder.create();
    String encryptKey = zConf.getCredentialsEncryptKey();
    if (StringUtils.isNotBlank(encryptKey)) {
      this.encryptor = new Encryptor(encryptKey);
    } else {
      this.encryptor = null;
    }
    if (zConf.credentialsPersist()) {
      try {
        this.storage = storage;
        loadFromFile();
      } catch (IOException e) {
        LOGGER.error("Fail to create ConfigStorage for Credentials. Persistenz will be disabled", e);
        this.storage = null;
      }
    } else {
      this.storage = null;
    }
  }

  public UsernamePasswords getAllUsernamePasswords(Set<String> userAndRoles) {
    UsernamePasswords up = new UsernamePasswords();
    up.putAll(getAllReadableCredentials(userAndRoles, false));
    return up;
  }

  public Credential getCredentialByEntity(String entity) {
    return credentials.get(entity);
  }

  /**
   *
   * @param userAndRoles Set of user and roles of the current user
   * @param maskPassword if true, all credential password information for readers will be removed
   * @return
   */
  public Credentials getAllReadableCredentials(Set<String> userAndRoles, boolean maskPassword) {
    Credentials sharedCreds = new Credentials();
    for (Entry<String, Credential> cred : credentials.entrySet()) {
      if (isReader(cred.getValue(), userAndRoles)) {
        if (maskPassword && !isOwner(cred.getValue(), userAndRoles)) {
          sharedCreds.putCredential(cred.getKey(), Credential.credentialWithoutPassword(cred.getValue()));
        } else {
          sharedCreds.putCredential(cred.getKey(), cred.getValue());
        }
      }
    }
    return sharedCreds;
  }

  public boolean isReader(Credential cred, Set<String> userAndRoles) {
    return AuthorizationService.isMember(userAndRoles, cred.getReaders())
      || isOwner(cred, userAndRoles)
      || AuthorizationService.isAdmin(userAndRoles, zConf);
  }

  public boolean isOwner(Credential cred, Set<String> userAndRoles) {
    return AuthorizationService.isMember(userAndRoles, cred.getOwners())
      || AuthorizationService.isAdmin(userAndRoles, zConf);
  }

  public boolean exists(String enitiy) {
    return credentials.containsEntity(enitiy);
  }

  public boolean isOwner(String entity, Set<String> userAndRoles) {
    Credential cred = credentials.get(entity);
    return isOwner(cred, userAndRoles);
  }

  public void putCredentialsEntity(String entity, Credential cred) throws IOException {
    loadCredentials();
    credentials.putCredential(entity, cred);
    saveCredentials();
  }

  /**
   *
   * @param entity to remove
   * @return true if the credentials entity was removed, false if the credentials entity was not found and not deleted
   * @throws IOException
   */
  public boolean removeCredentialEntity(String entity) throws IOException {
    loadCredentials();
    if (!credentials.containsEntity(entity)) {
      return false;
    }

    credentials.removeCredential(entity);
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
      if (StringUtils.isBlank(json)) {
        return;
      }
      if (encryptor != null) {
        json = encryptor.decrypt(json);
      }

      Credentials loadedCreds = parseToCredentials(json, gson);
      if (!loadedCreds.isEmpty()) {
        credentials = loadedCreds;
      }
    } catch (IOException e) {
      throw new IOException("Error loading credentials file", e);
    }
  }

  public static Credentials parseToCredentials(@Nonnull String json, Gson gson) {
    CredentialsInfoSaving info = gson.fromJson(json, CredentialsInfoSaving.class);
    if (info.getCredentialsMap() != null) {
      return info.getCredentialsMap();
    }
    LOGGER.warn("Parsing with CredentialsInfoSavingOld");
    CredentialsInfoSavingOld infoOld = gson.fromJson(json, CredentialsInfoSavingOld.class);
    return infoOld.getCredentialsMap();
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (credentials) {
      CredentialsInfoSaving info = new CredentialsInfoSaving(credentials);
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
