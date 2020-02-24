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

import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.FileSystemStorage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotebookRepos for hdfs.
 *
 */
public class FileSystemNotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemNotebookRepo.class);

  private FileSystemStorage fs;
  private Path notebookDir;

  public FileSystemNotebookRepo() {

  }

  public void init(ZeppelinConfiguration zConf) throws IOException {
    this.fs = new FileSystemStorage(zConf, zConf.getNotebookDir());
    LOGGER.info("Creating FileSystem: " + this.fs.getFs().getClass().getName());
    this.notebookDir = this.fs.makeQualified(new Path(zConf.getNotebookDir()));
    LOGGER.info("Using folder {} to store notebook", notebookDir);
    this.fs.tryMkDir(notebookDir);
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    List<Path> notePaths = fs.listAll(notebookDir);
    Map<String, NoteInfo> noteInfos = new HashMap<>();
    for (Path path : notePaths) {
      try {
        NoteInfo noteInfo = new NoteInfo(getNoteId(path.getName()),
            getNotePath(notebookDir.toString(), path.toString()));
        noteInfos.put(noteInfo.getId(), noteInfo);
      } catch (IOException e) {
        LOGGER.warn("Fail to get NoteInfo for note: " + path.getName(), e);
      }
    }
    return noteInfos;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    String content = this.fs.readFile(
        new Path(notebookDir, buildNoteFileName(noteId, notePath)));
    return Note.fromJson(content);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    this.fs.writeFile(note.toJson(),
        new Path(notebookDir, buildNoteFileName(note.getId(), note.getPath())),
        true);
  }

  @Override
  public void move(String noteId,
                   String notePath,
                   String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    Path src = new Path(notebookDir, buildNoteFileName(noteId, notePath));
    Path dest = new Path(notebookDir, buildNoteFileName(noteId, newNotePath));
    // [ZEPPELIN-4195] newNotePath parent path maybe not exist
    this.fs.tryMkDir(new Path(notebookDir, newNotePath.substring(1)).getParent());
    this.fs.move(src, dest);
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject)
      throws IOException {
    // [ZEPPELIN-4195] newFolderPath parent path maybe not exist
    this.fs.tryMkDir(new Path(notebookDir, folderPath.substring(1)).getParent());
    this.fs.move(new Path(notebookDir, folderPath.substring(1)),
        new Path(notebookDir, newFolderPath.substring(1)));
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject)
      throws IOException {
    if (!this.fs.delete(new Path(notebookDir.toString(), buildNoteFileName(noteId, notePath)))) {
      LOGGER.warn("Fail to move note, noteId: " + notePath + ", notePath: " + notePath);
    }
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    if (!this.fs.delete(new Path(notebookDir, folderPath.substring(1)))) {
      LOGGER.warn("Fail to remove folder: " + folderPath);
    }
  }

  @Override
  public void close() {
    LOGGER.warn("close is not implemented for FileSystemNotebookRepo");
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOGGER.warn("getSettings is not implemented for FileSystemNotebookRepo");
    return null;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOGGER.warn("updateSettings is not implemented for FileSystemNotebookRepo");
  }

}
