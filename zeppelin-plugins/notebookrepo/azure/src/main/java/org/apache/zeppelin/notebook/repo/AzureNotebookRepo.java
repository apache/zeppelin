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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileDirectory;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.azure.storage.file.ListFileItem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure storage backend for notebooks
 */
public class AzureNotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(AzureNotebookRepo.class);

  private ZeppelinConfiguration conf;
  private String user;
  private String shareName;
  private CloudFileDirectory rootDir;

  public AzureNotebookRepo() {

  }

  public void init(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    user = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_AZURE_USER);
    shareName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_AZURE_SHARE);

    try {
      CloudStorageAccount account = CloudStorageAccount.parse(
          conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING));
      CloudFileClient client = account.createCloudFileClient();
      CloudFileShare share = client.getShareReference(shareName);
      share.createIfNotExists();

      CloudFileDirectory userDir = StringUtils.isBlank(user) ?
          share.getRootDirectoryReference() :
          share.getRootDirectoryReference().getDirectoryReference(user);
      userDir.createIfNotExists();

      rootDir = userDir.getDirectoryReference("notebook");
      rootDir.createIfNotExists();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    return list(rootDir);
  }

  private Map<String, NoteInfo> list(CloudFileDirectory folder) throws IOException {
    Map<String, NoteInfo> notesInfo = new HashMap<>();
    for (ListFileItem item : rootDir.listFilesAndDirectories()) {
      if (item instanceof CloudFileDirectory) {
        CloudFileDirectory dir = (CloudFileDirectory) item;
        notesInfo.putAll(list(dir));
      } else if (item instanceof CloudFile){
        CloudFile file = (CloudFile) item;
        if (file.getName().endsWith(".zpln")) {
          try {
            String noteName = getNotePath(rootDir.getUri().getPath(), file.getUri().getPath());
            String noteId = getNoteId(file.getUri().getPath());
            notesInfo.put(noteId, new NoteInfo(noteId, noteName));
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        } else {
          LOGGER.debug("Skip invalid note file: " + file.getUri().getPath());
        }
      }
    }
    return notesInfo;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    InputStream ins = null;
    try {
      CloudFile noteFile = rootDir.getFileReference(buildNoteFileName(noteId, notePath));
      ins = noteFile.openRead();
    } catch (URISyntaxException | StorageException e) {
      String msg = String.format("Error reading notebook %s from Azure storage",
          buildNoteFileName(noteId, notePath));
      LOGGER.error(msg, e);
      throw new IOException(msg, e);
    }
    String json = IOUtils.toString(ins,
        conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    ins.close();
    return Note.fromJson(json);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    try {
      CloudFile noteFile = rootDir.getFileReference(buildNoteFileName(note));
      noteFile.getParent().createIfNotExists();
      noteFile.uploadText(note.toJson());
    } catch (URISyntaxException | StorageException e) {
      String msg = String.format("Error saving notebook %s to Azure storage",
          buildNoteFileName(note));
      LOGGER.error(msg, e);
      throw new IOException(msg, e);
    }
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) {

  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {

  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    try {
      CloudFile noteFile = rootDir.getFileReference(buildNoteFileName(noteId, notePath));
      noteFile.delete();
    } catch (URISyntaxException | StorageException e) {
      String msg = String.format("Error deleting notebook %s from Azure storage",
          buildNoteFileName(noteId, notePath));
      LOGGER.error(msg, e);
      throw new IOException(msg, e);
    }
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) {

  }

  @Override
  public void close() {
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOGGER.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOGGER.warn("Method not implemented");
  }

}
