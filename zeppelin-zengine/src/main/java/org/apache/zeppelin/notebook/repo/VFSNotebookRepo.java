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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* NotebookRepo implementation based on apache vfs
*/
public class VFSNotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(VFSNotebookRepo.class);

  protected ZeppelinConfiguration conf;
  protected FileSystemManager fsManager;
  protected FileObject rootNotebookFileObject;
  protected String rootNotebookFolder;

  public VFSNotebookRepo() {

  }

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    setNotebookDirectory(conf.getRelativeDir(conf.getNotebookDir()));
  }

  protected void setNotebookDirectory(String notebookDirPath) throws IOException {
    URI filesystemRoot = null;
    try {
      LOGGER.info("Using notebookDir: " + notebookDirPath);
      if (conf.isWindowsPath(notebookDirPath)) {
        filesystemRoot = new File(notebookDirPath).toURI();
      } else {
        filesystemRoot = new URI(notebookDirPath);
      }
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      File f = new File(conf.getRelativeDir(filesystemRoot.getPath()));
      filesystemRoot = f.toURI();
    }
    this.fsManager = VFS.getManager();
    this.rootNotebookFileObject = fsManager.resolveFile(filesystemRoot);
    if (!this.rootNotebookFileObject.exists()) {
      this.rootNotebookFileObject.createFolder();
      LOGGER.info("Notebook dir doesn't exist: {}, creating it.",
          rootNotebookFileObject.getName().getPath());
    }
    this.rootNotebookFolder = rootNotebookFileObject.getName().getPath();
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    // Must to create rootNotebookFileObject each time when call method list, otherwise we can not
    // get the updated data under this folder.
    this.rootNotebookFileObject = fsManager.resolveFile(this.rootNotebookFolder);
    return listFolder(rootNotebookFileObject);
  }

  private Map<String, NoteInfo> listFolder(FileObject fileObject) throws IOException {
    Map<String, NoteInfo> noteInfos = new HashMap<>();
    if (fileObject.isFolder()) {
      if (fileObject.getName().getBaseName().startsWith(".")) {
        LOGGER.warn("Skip hidden folder: " + fileObject.getName().getPath());
        return noteInfos;
      }
      for (FileObject child : fileObject.getChildren()) {
        noteInfos.putAll(listFolder(child));
      }
    } else {
      String noteFileName = fileObject.getName().getPath();
      if (noteFileName.endsWith(".zpln")) {
        try {
          String noteId = getNoteId(noteFileName);
          String notePath = getNotePath(rootNotebookFolder, noteFileName);
          noteInfos.put(noteId, new NoteInfo(noteId, notePath));
        } catch (IOException e) {
          LOGGER.warn(e.getMessage());
        }

      } else {
        LOGGER.debug("Unrecognized note file: " + noteFileName);
      }
    }
    return noteInfos;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    FileObject noteFile = rootNotebookFileObject.resolveFile(buildNoteFileName(noteId, notePath),
        NameScope.DESCENDENT);
    String json = IOUtils.toString(noteFile.getContent().getInputStream(),
        conf.getString(ConfVars.ZEPPELIN_ENCODING));
    Note note = Note.fromJson(json);
    // setPath here just for testing, because actually NoteManager will setPath
    note.setPath(notePath);
    return note;
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Saving note " + note.getId() + " to " + buildNoteFileName(note));
    // write to tmp file first, then rename it to the {note_name}_{note_id}.zpln
    FileObject noteJson = rootNotebookFileObject.resolveFile(
        buildNoteTempFileName(note), NameScope.DESCENDENT);
    OutputStream out = null;
    try {
      out = noteJson.getContent().getOutputStream(false);
      IOUtils.write(note.toJson().getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)), out);
    } finally {
      if (out != null) {
        out.close();
      }
    }
    noteJson.moveTo(rootNotebookFileObject.resolveFile(
        buildNoteFileName(note), NameScope.DESCENDENT));
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    LOGGER.info("Move note " + noteId + " from " + notePath + " to " + newNotePath);
    FileObject fileObject = rootNotebookFileObject.resolveFile(
        buildNoteFileName(noteId, notePath), NameScope.DESCENDENT);
    FileObject destFileObject = rootNotebookFileObject.resolveFile(
        buildNoteFileName(noteId, newNotePath), NameScope.DESCENDENT);
    // create parent folder first, otherwise move operation will fail
    destFileObject.getParent().createFolder();
    fileObject.moveTo(destFileObject);
  }

  @Override
  public void move(String folderPath, String newFolderPath,
                   AuthenticationInfo subject) throws IOException{
    LOGGER.info("Move folder from " + folderPath + " to " + newFolderPath);
    FileObject fileObject = rootNotebookFileObject.resolveFile(
        folderPath.substring(1), NameScope.DESCENDENT);
    FileObject destFileObject = rootNotebookFileObject.resolveFile(
        newFolderPath.substring(1), NameScope.DESCENDENT);
    // create parent folder first, otherwise move operation will fail
    destFileObject.getParent().createFolder();
    fileObject.moveTo(destFileObject);
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject)
      throws IOException {
    LOGGER.info("Remove note: " + noteId + ", notePath: " + notePath);
    FileObject noteFile = rootNotebookFileObject.resolveFile(
        buildNoteFileName(noteId, notePath), NameScope.DESCENDENT);
    noteFile.delete(Selectors.SELECT_SELF);
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove folder: " + folderPath);
    FileObject folderObject = rootNotebookFileObject.resolveFile(
        folderPath.substring(1), NameScope.DESCENDENT);
    folderObject.deleteAll();
  }

  @Override
  public void close() {
    //no-op    
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    NotebookRepoSettingsInfo repoSetting = NotebookRepoSettingsInfo.newInstance();
    List<NotebookRepoSettingsInfo> settings = new ArrayList<>();
    repoSetting.name = "Notebook Path";
    repoSetting.type = NotebookRepoSettingsInfo.Type.INPUT;
    repoSetting.value = Collections.emptyList();
    repoSetting.selected = rootNotebookFileObject.getName().getPath();

    settings.add(repoSetting);
    return settings;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    if (settings == null || settings.isEmpty()) {
      LOGGER.error("Cannot update {} with empty settings", this.getClass().getName());
      return;
    }
    String newNotebookDirectotyPath = StringUtils.EMPTY;
    if (settings.containsKey("Notebook Path")) {
      newNotebookDirectotyPath = settings.get("Notebook Path");
    }

    if (StringUtils.isBlank(newNotebookDirectotyPath)) {
      LOGGER.error("Notebook path is invalid");
      return;
    }
    LOGGER.warn("{} will change notebook dir from {} to {}",
        subject.getUser(), this.rootNotebookFolder, newNotebookDirectotyPath);
    try {
      setNotebookDirectory(newNotebookDirectotyPath);
    } catch (IOException e) {
      LOGGER.error("Cannot update notebook directory", e);
    }
  }

}

