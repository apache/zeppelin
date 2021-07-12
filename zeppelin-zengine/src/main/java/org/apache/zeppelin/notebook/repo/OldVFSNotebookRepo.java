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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.OldNoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
*
*/
public class OldVFSNotebookRepo implements OldNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(OldVFSNotebookRepo.class);

  private FileSystemManager fsManager;
  private URI filesystemRoot;
  protected ZeppelinConfiguration conf;

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    setNotebookDirectory(conf.getNotebookDir());
  }

  protected void setNotebookDirectory(String notebookDirPath) throws IOException {
    try {
      LOG.info("Using notebookDir: {}", notebookDirPath);
      if (conf.isWindowsPath(notebookDirPath)) {
        filesystemRoot = new File(notebookDirPath).toURI();
      } else {
        filesystemRoot = new URI(notebookDirPath);
      }
    } catch (URISyntaxException e1) {
      throw new IOException(e1);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      File f = new File(conf.getAbsoluteDir(filesystemRoot.getPath()));
      this.filesystemRoot = f.toURI();
    }

    fsManager = VFS.getManager();
    FileObject file = fsManager.resolveFile(filesystemRoot.getPath());
    if (!file.exists()) {
      LOG.info("Notebook dir doesn't exist, create on is {}.", file.getName());
      file.createFolder();
    }
  }

  private String getNotebookDirPath() {
    return filesystemRoot.getPath();
  }

  private String getPath(String path) {
    if (path == null || path.trim().length() == 0) {
      return filesystemRoot.toString();
    }
    if (path.startsWith("/")) {
      return filesystemRoot.toString() + path;
    } else {
      return filesystemRoot.toString() + "/" + path;
    }
  }

  private boolean isDirectory(FileObject fo) throws IOException {
    if (fo == null) {
      return false;
    }
    return fo.getType() == FileType.FOLDER;
  }

  @Override
  public List<OldNoteInfo> list(AuthenticationInfo subject) throws IOException {
    FileObject rootDir = getRootDir();

    FileObject[] children = rootDir.getChildren();

    List<OldNoteInfo> infos = new LinkedList<>();
    for (FileObject f : children) {
      String fileName = f.getName().getBaseName();
      if (f.isHidden()
          || fileName.startsWith(".")
          || fileName.startsWith("#")
          || fileName.startsWith("~")) {
        // skip hidden, temporary files
        continue;
      }

      if (!isDirectory(f)) {
        // currently single note is saved like, [NOTE_ID]/note.json.
        // so it must be a directory
        continue;
      }

      OldNoteInfo info = null;

      try {
        info = getNoteInfo(f);
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        LOG.error("Can't read note {}", f.getName());
      }
    }

    return infos;
  }

  private Note getNote(FileObject noteDir) throws IOException {
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile("note.json", NameScope.CHILD);
    if (!noteJson.exists()) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }

    FileContent content = noteJson.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    return Note.fromJson(null, json);
  }

  private OldNoteInfo getNoteInfo(FileObject noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new OldNoteInfo(note);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    return getNote(noteDir);
  }

  protected FileObject getRootDir() throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));

    if (!rootDir.exists()) {
      throw new IOException("Root path does not exists");
    }

    if (!isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    LOG.info("Saving note: {}", note.getId());
    String json = note.toJson();

    FileObject rootDir = getRootDir();

    FileObject noteDir = rootDir.resolveFile(note.getId(), NameScope.CHILD);

    if (!noteDir.exists()) {
      noteDir.createFolder();
    }
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile(".note.json", NameScope.CHILD);
    // false means not appending. creates file if not exists
    OutputStream out = noteJson.getContent().getOutputStream(false);
    out.write(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
    out.close();
    noteJson.moveTo(noteDir.resolveFile("note.json", NameScope.CHILD));
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    if (!noteDir.exists()) {
      // nothing to do
      return;
    }

    if (!isDirectory(noteDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + noteDir.getName().toString());
    }

    noteDir.delete(Selectors.SELECT_SELF_AND_CHILDREN);
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
    repoSetting.selected = getNotebookDirPath();

    settings.add(repoSetting);
    return settings;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    if (settings == null || settings.isEmpty()) {
      LOG.error("Cannot update {} with empty settings", this.getClass().getName());
      return;
    }
    String newNotebookDirectotyPath = StringUtils.EMPTY;
    if (settings.containsKey("Notebook Path")) {
      newNotebookDirectotyPath = settings.get("Notebook Path");
    }

    if (StringUtils.isBlank(newNotebookDirectotyPath)) {
      LOG.error("Notebook path is invalid");
      return;
    }
    LOG.warn("{} will change notebook dir from {} to {}",
        subject.getUser(), getNotebookDirPath(), newNotebookDirectotyPath);
    try {
      setNotebookDirectory(newNotebookDirectotyPath);
    } catch (IOException e) {
      LOG.error("Cannot update notebook directory", e);
    }
  }

}

