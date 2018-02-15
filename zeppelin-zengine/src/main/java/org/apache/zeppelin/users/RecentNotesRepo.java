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
 * Class for storing users' recent notes (persistence layer).
 */
public class RecentNotesRepo {
  private static final Logger LOG = LoggerFactory.getLogger(RecentNotesRepo.class);

  private Gson gson;
  private File recentNotesFile;

  private Map<String, RecentNotes> recentNotesInfo = new HashMap<>();

  private boolean persistMode;

  public RecentNotesRepo() {
    persistMode = false;
  }

  public RecentNotesRepo(String recentNotesFilePath) {
    persistMode = true;
    if (recentNotesFilePath != null) {
      this.recentNotesFile = new File(recentNotesFilePath);
    }

    gson = new GsonBuilder().setPrettyPrinting().create();
    loadFromFile();
  }

  public RecentNotes getRecentNotesInfo(String user) {
    RecentNotes recentNotes = recentNotesInfo.get(user);
    if (recentNotes == null) {
      recentNotes = new RecentNotes();
    }
    return recentNotes;
  }

  public void putRecentNote(String user, String noteId) throws IOException {
    synchronized (recentNotesInfo) {
      if (!recentNotesInfo.containsKey(user)) {
        recentNotesInfo.put(user, new RecentNotes());
      }
      recentNotesInfo.get(user)
          .addRecentNote(noteId);
      saveToFileIfPersistMode();
    }
  }

  public void removeNoteFromRecent(String user, String noteId) throws IOException {
    synchronized (recentNotesInfo) {
      recentNotesInfo.get(user).removeNoteFromRecent(noteId);
      saveToFileIfPersistMode();
    }
  }

  /**
   * Remove note from recent of all users
   * @param noteId - id of removing note
   * @throws IOException if problems with save to file
   */
  public void removeNoteFromRecent(String noteId) throws IOException {
    synchronized (recentNotesInfo) {
      for (Map.Entry<String, RecentNotes> e : recentNotesInfo.entrySet()) {
        e.getValue().removeNoteFromRecent(noteId);
      }
      saveToFileIfPersistMode();
    }
  }

  public void clearRecent(String user) throws IOException {
    synchronized (recentNotesInfo) {
      recentNotesInfo.get(user).clearRecent();
      saveToFileIfPersistMode();
    }
  }

  private void loadFromFile() {
    LOG.info(recentNotesFile.getAbsolutePath());
    if (!recentNotesFile.exists()) {
      return;
    }
    try (FileInputStream fis = new FileInputStream(recentNotesFile);
         InputStreamReader isr = new InputStreamReader(fis)) {
      BufferedReader bufferedReader = new BufferedReader(isr);
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        sb.append(line);
      }

      String json = sb.toString();
      RecentNotesSaving info = gson.fromJson(json, RecentNotesSaving.class);
      this.recentNotesInfo = info.recentNotesInfo;
    } catch (IOException e) {
      LOG.error("Error loading recent notes file", e);
      e.printStackTrace();
    }
  }

  private void saveToFileIfPersistMode() throws IOException {
    if (persistMode)
      saveToFile();
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (recentNotesInfo) {
      RecentNotesSaving info = new RecentNotesSaving();
      info.recentNotesInfo = recentNotesInfo;
      jsonString = gson.toJson(info);
    }

    try {
      if (!recentNotesFile.exists()) {
        recentNotesFile.createNewFile();

        Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
        Files.setPosixFilePermissions(recentNotesFile.toPath(), permissions);
      }
      try (FileOutputStream fos = new FileOutputStream(recentNotesFile, false);
           OutputStreamWriter out = new OutputStreamWriter(fos)) {
        out.append(jsonString);
      }
    } catch (IOException e) {
      LOG.error("Error while saving recent notes file", e);
    }
  }
}
