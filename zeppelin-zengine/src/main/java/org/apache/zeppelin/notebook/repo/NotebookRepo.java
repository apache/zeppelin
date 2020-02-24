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

package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Notebook repository (persistence layer) abstraction.
 */
public interface NotebookRepo {

  void init(ZeppelinConfiguration zConf) throws IOException;

  /**
   * Lists notebook information about all notebooks in storage. This method should only read
   * the note file name, rather than reading all note content which usually takes long time.
   *
   * @param subject contains user information.
   * @return Map of noteId -> NoteInfo
   * @throws IOException
   */
  @ZeppelinApi
  Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException;

  /**
   * Get the notebook with the given noteId and given notePath.
   *
   * @param noteId   is note id.
   * @param notePath is note path
   * @param subject  contains user information.
   * @return
   * @throws IOException
   */
  @ZeppelinApi
  Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException;

  /**
   * Save given note in storage
   *
   * @param note    is the note itself.
   * @param subject contains user information.
   * @throws IOException
   */
  @ZeppelinApi
  void save(Note note, AuthenticationInfo subject) throws IOException;

  /**
   *
   * Move given note to another path
   *
   * @param noteId
   * @param notePath
   * @param newNotePath
   * @throws IOException
   */
  @ZeppelinApi
  void move(String noteId, String notePath, String newNotePath,
            AuthenticationInfo subject) throws IOException;

  /**
   * Move folder to another path
   *
   * @param folderPath
   * @param newFolderPath
   * @param subject
   * @throws IOException
   */
  void move(String folderPath, String newFolderPath,
            AuthenticationInfo subject) throws IOException;

  /**
   * Remove note with given id and notePath
   *
   * @param noteId   is note id.
   * @param notePath is note path
   * @param subject  contains user information.
   * @throws IOException
   */
  @ZeppelinApi
  void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException;

  /**
   * Remove folder
   *
   * @param folderPath
   * @param subject
   * @throws IOException
   */
  @ZeppelinApi
  void remove(String folderPath, AuthenticationInfo subject) throws IOException;

  /**
   * Release any underlying resources
   */
  @ZeppelinApi
  void close();


  /**
   * Get NotebookRepo settings got the given user.
   *
   * @param subject
   * @return
   */
  @ZeppelinApi
  List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject);

  /**
   * update notebook repo settings.
   *
   * @param settings
   * @param subject
   */
  @ZeppelinApi
  void updateSettings(Map<String, String> settings, AuthenticationInfo subject);

  default String buildNoteFileName(String noteId, String notePath) throws IOException {
    if (!notePath.startsWith("/")) {
      throw new IOException("Invalid notePath: " + notePath);
    }
    return (notePath + "_" + noteId + ".zpln").substring(1);
  }

  default String buildNoteFileName(Note note) throws IOException {
    return buildNoteFileName(note.getId(), note.getPath());
  }

  default String buildNoteTempFileName(Note note) {
    return (note.getPath() + "_" + note.getId() + ".tmp").substring(1);
  }

  default String getNoteId(String noteFileName) throws IOException {
    int separatorIndex = noteFileName.lastIndexOf("_");
    if (separatorIndex == -1) {
      throw new IOException(
          "Invalid note name, no '_' in note name: " + noteFileName);
    }
    try {
      int dotIndex = noteFileName.lastIndexOf(".");
      return noteFileName.substring(separatorIndex + 1, dotIndex);
    } catch (StringIndexOutOfBoundsException e) {
      throw new IOException("Invalid note name: " + noteFileName);
    }
  }

  default String getNotePath(String rootNoteFolder, String noteFileName)
      throws IOException {
    int index = noteFileName.lastIndexOf("_");
    if (index == -1) {
      throw new IOException(
          "Invalid note name, no '_' in note name: " + noteFileName);
    }
    try {
      return noteFileName.substring(rootNoteFolder.length(), index);
    } catch (StringIndexOutOfBoundsException e) {
      throw new IOException("Invalid note name: " + noteFileName);
    }
  }
}
