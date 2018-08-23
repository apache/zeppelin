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

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class defining credentials for data source authorization */
public class Credentials {
  private static final Logger LOG = LoggerFactory.getLogger(Credentials.class);

  private Map<String, UserCredentials> credentialsMap;
  private Gson gson;
  private Boolean credentialsPersist = true;
  File credentialsFile;

  private Encryptor encryptor;

  /**
   * Wrapper fro user credentials. It can load credentials from a file if credentialsPath is
   * supplied, and will encrypt the file if an encryptKey is supplied.
   *
   * @param credentialsPersist
   * @param credentialsPath
   * @param encryptKey
   */
  public Credentials(Boolean credentialsPersist, String credentialsPath, String encryptKey) {
    if (encryptKey != null) {
      this.encryptor = new Encryptor(encryptKey);
    }

    this.credentialsPersist = credentialsPersist;
    if (credentialsPath != null) {
      credentialsFile = new File(credentialsPath);
    }
    credentialsMap = new HashMap<>();

    if (credentialsPersist) {
      GsonBuilder builder = new GsonBuilder();
      builder.setPrettyPrinting();
      gson = builder.create();
      loadFromFile();
    }
  }

  public UserCredentials getUserCredentials(String username) {
    UserCredentials uc = credentialsMap.get(username);
    if (uc == null) {
      uc = new UserCredentials();
    }
    return uc;
  }

  public void putUserCredentials(String username, UserCredentials uc) throws IOException {
    credentialsMap.put(username, uc);
    saveCredentials();
  }

  public UserCredentials removeUserCredentials(String username) throws IOException {
    UserCredentials uc;
    uc = credentialsMap.remove(username);
    saveCredentials();
    return uc;
  }

  public boolean removeCredentialEntity(String username, String entity) throws IOException {
    UserCredentials uc = credentialsMap.get(username);
    if (uc != null && uc.existUsernamePassword(entity) == false) {
      return false;
    }

    uc.removeUsernamePassword(entity);
    saveCredentials();
    return true;
  }

  public void saveCredentials() throws IOException {
    if (credentialsPersist) {
      saveToFile();
    }
  }

  private void loadFromFile() {
    LOG.info(credentialsFile.getAbsolutePath());
    if (!credentialsFile.exists()) {
      // nothing to read
      return;
    }

    try {
      FileInputStream fis = new FileInputStream(credentialsFile);
      InputStreamReader isr = new InputStreamReader(fis);
      BufferedReader bufferedReader = new BufferedReader(isr);
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        sb.append(line);
      }
      isr.close();
      fis.close();

      String json = sb.toString();

      if (encryptor != null) {
        json = encryptor.decrypt(json);
      }

      CredentialsInfoSaving info = CredentialsInfoSaving.fromJson(json);
      this.credentialsMap = info.credentialsMap;
    } catch (IOException e) {
      LOG.error("Error loading credentials file", e);
      e.printStackTrace();
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
      if (!credentialsFile.exists()) {
        credentialsFile.createNewFile();

        Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
        Files.setPosixFilePermissions(credentialsFile.toPath(), permissions);
      }

      FileOutputStream fos = new FileOutputStream(credentialsFile, false);
      OutputStreamWriter out = new OutputStreamWriter(fos);

      if (encryptor != null) {
        jsonString = encryptor.encrypt(jsonString);
      }

      out.append(jsonString);
      out.close();
      fos.close();
    } catch (IOException e) {
      LOG.error("Error saving credentials file", e);
    }
  }
}
