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
package org.apache.zeppelin.users;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * Class for storing user information (persistence layer)
 */
public class UsersRepo {
  private static final Logger LOG = LoggerFactory.getLogger(UsersRepo.class);

  private Gson gson;
  private File usersFile;

  private Map<String, UserInfo> usersInfo = new HashMap<>();

  public UsersRepo() {
  }

  public UsersRepo(String usersInfoPath) {
    if (usersInfoPath != null) {
      this.usersFile = new File(usersInfoPath);
    }


    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();
    loadFromFile();
  }

  public UserInfo getUserInfo(String user) {
    UserInfo userInfo = usersInfo.get(user);
    if (userInfo == null) {
      userInfo = new UserInfo();
    }
    return userInfo;
  }

  public void putRecentNote(String user, String noteId) throws IOException {
    synchronized (usersInfo) {
      if (!usersInfo.containsKey(user)) {
        usersInfo.put(user, new UserInfo());
      }
      usersInfo.get(user)
          .addRecentNote(noteId);
      saveToFile();
    }
  }

  public void removeNoteFromRecent(String noteId) {
    synchronized (usersInfo) {
      for (Map.Entry<String, UserInfo> e : usersInfo.entrySet()) {
        e.getValue().removeNoteFromRecent(noteId);
      }
    }
  }

  public void clearRecent(String user){
    synchronized (usersInfo){
      usersInfo.get(user).clearRecent();
    }
  }

  private void loadFromFile() {
    LOG.info(usersFile.getAbsolutePath());
    if (!usersFile.exists()) {
      return;
    }
    try {
      FileInputStream fis = new FileInputStream(usersFile);
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
      UserInfoSaving info = gson.fromJson(json, UserInfoSaving.class);
      this.usersInfo = info.usersInfo;
    } catch (IOException e) {
      LOG.error("Error loading users file", e);
      e.printStackTrace();
    }
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (usersInfo) {
      UserInfoSaving info = new UserInfoSaving();
      info.usersInfo = usersInfo;
      jsonString = gson.toJson(info);
    }

    try {
      if (!usersFile.exists()) {
        usersFile.createNewFile();

        Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
        Files.setPosixFilePermissions(usersFile.toPath(), permissions);
      }
      try (FileOutputStream fos = new FileOutputStream(usersFile, false);
           OutputStreamWriter out = new OutputStreamWriter(fos)) {
        out.append(jsonString);
      }
    } catch (IOException e) {
      LOG.error("Error while saving users file", e);
    }
  }
}
