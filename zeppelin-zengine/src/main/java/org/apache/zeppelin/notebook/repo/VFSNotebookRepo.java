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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.FileInfo;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
*
*/
public class VFSNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(VFSNotebookRepo.class);

  private FileSystemManager fsManager;
  private URI filesystemRoot;
  private ZeppelinConfiguration conf;

  public VFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    setNotebookDirectory(conf.getNotebookDir());
  }

  private void setNotebookDirectory(String notebookDirPath) throws IOException {
    try {
      if (conf.isWindowsPath(notebookDirPath)) {
        filesystemRoot = new File(notebookDirPath).toURI();
      } else {
        filesystemRoot = new URI(notebookDirPath);
      }
    } catch (URISyntaxException e1) {
      throw new IOException(e1);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      File f = new File(conf.getRelativeDir(filesystemRoot.getPath()));
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
    return filesystemRoot.getPath().toString();
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
    if (fo == null) return false;
    if (fo.getType() == FileType.FOLDER) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    FileObject rootDir = getRootDir();

    FileObject[] children = rootDir.getChildren();

    List<NoteInfo> infos = new LinkedList<>();
    for (FileObject f : children) {
      
      if (isHidden(f)) {
        // skip hidden, temporary files
        continue;
      }

      if (!isDirectory(f)) {
        // currently single note is saved like, [NOTE_ID]/note.json or [NOTE_ID]/title.zpln.
        // so it must be a directory
        continue;
      }

      NoteInfo info = null;

      try {
        info = getNoteInfo(f);
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        LOG.error("Can't read note " + f.getName().toString(), e);
      }
    }

    return infos;
  }

  private boolean isHidden(FileObject file) throws FileSystemException {
    String fileName = file.getName().getBaseName();
    return file.isHidden()
        || fileName.startsWith(".")
        || fileName.startsWith("#")
        || fileName.startsWith("~");
  }
  
  private List<FileObject> filterHiddenFiles(FileObject[] files) throws FileSystemException {
    List<FileObject> filteredFiles = Lists.newArrayList();
    for (FileObject f: files) {
      if (!isHidden(f)) {
        filteredFiles.add(f);
      }
    }
    return filteredFiles;
  }
  
  private Note getNote(FileObject noteDir) throws IOException {
    FileObject noteFile = getNoteFromDir(noteDir);

    FileContent content = noteFile.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    Note note = Note.fromJson(json);
//    note.setReplLoader(replLoader);
//    note.jobListenerFactory = jobListenerFactory;

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }

      List<ApplicationState> appStates = p.getAllApplicationStates();
      if (appStates != null) {
        for (ApplicationState app : appStates) {
          if (app.getStatus() != ApplicationState.Status.ERROR) {
            app.setStatus(ApplicationState.Status.UNLOADED);
          }
        }
      }
    }
    
    setFileInfo(note, noteFile);

    return note;
  }

  private void setFileInfo(Note note, FileObject noteFile) {
    FileInfo fileInfo = note.getFileInfo();
    if (FileInfo.isEmpty(fileInfo)) {
      fileInfo = FileInfo.createInstance();
      note.setFileInfo(fileInfo);
    }
    fileInfo.setFile(noteFile.getName().getBaseName());
    // TODO(khalid): integrate it with folder structure
    fileInfo.setFolder(note.getId());
  }
  
  private FileObject getNoteFromDir(FileObject noteDir) throws IOException {
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    // enforce single file in directory
    FileObject[] files = noteDir.getChildren();
    
    FileObject noteFile;
    if (files.length != 1) {
      List<FileObject> filteredFiles = filterHiddenFiles(files);
      if (filteredFiles.isEmpty()) {
        throw new IOException(
            "note folder " + noteDir.getName().toString() + " is empty");
      }
      if (filteredFiles.size() > 1)
        throw new IOException(
            "note folder " + noteDir.getName().toString() + " contains more than one file");
      noteFile = filteredFiles.get(0);
    } else {
      noteFile = files[0];
    }
    

    if (!noteFile.exists()) {
      throw new IOException(noteFile.getName().toString() + " not found");
    }
    
    // enforce either extended or note.json file
    if (!FilenameUtils.isExtension(noteFile.getName().getBaseName(),
        Util.getZeppelinNoteExtension())
        && !FilenameUtils.equals(noteFile.getName().getBaseName(), "note.json")) {
      throw new IOException(noteFile.getName().getBaseName() + " file isn't in acceptable format");
    }
    
    return noteFile;
  }
  
  private NoteInfo getNoteInfo(FileObject noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new NoteInfo(note);
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
    String json = note.toJson();

    FileObject noteDir = getNoteDir(note.getId());

    FileObject noteJson = noteDir.resolveFile(".note.zpln", NameScope.CHILD);
    
    // false means not appending. creates file if not exists
    OutputStream out = noteJson.getContent().getOutputStream(false);
    out.write(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
    out.close();
    
    String filename = note.getFileInfo().getFile();
    if (StringUtils.isBlank(filename)) {
      // when creating note
      filename = Util.convertTitleToFilename(note.getName());
    }
    noteJson.moveTo(noteDir.resolveFile(filename, NameScope.CHILD));
    note.getFileInfo().setFile(filename);
    note.getFileInfo().setFolder(note.getId());
    
    if (StringUtils.equals(filename, note.getId() + "." + Util.getZeppelinNoteExtension())
        && !StringUtils.equals(note.getId(), note.getName())) {
      // rename new note without title
      FileInfo newFileName = note.getFileInfo().copy();
      newFileName.setFile(Util.convertTitleToFilename(note.getName()));
      note.setFileInfo(rename(note.getFileInfo(), newFileName, subject));
    }
  }

  private FileObject getNoteDir(String dirPath) throws IOException {
    FileObject rootDir = getRootDir();

    FileObject noteDir = rootDir.resolveFile(dirPath, NameScope.CHILD);

    if (!noteDir.exists()) {
      noteDir.createFolder();
    }
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }
    return noteDir;
  }
  
  private FileObject getNoteFile(FileObject noteDir, String filename) throws IOException {
    FileObject noteFile = noteDir.resolveFile(filename, NameScope.CHILD);
    
    if (isDirectory(noteFile)) {
      throw new IOException(noteFile.getName().toString() + " is a directory");
    }
    
    return noteFile;
  }
  
  @Override
  public synchronized FileInfo rename(FileInfo oldFile, FileInfo newFile,
      AuthenticationInfo subject) throws IOException {
    // currently assuming old and new files are in same folder
    LOG.info("entered renaming, oldFile is {}, newFile is {}", oldFile, newFile);
    FileObject noteDir = getNoteDir(newFile.getFolder());

    FileObject oldNoteFile = getNoteFile(noteDir, oldFile.getFile());
    FileObject newNoteFile = getNoteFile(noteDir, newFile.getFile());
    
    if (!oldNoteFile.exists()) {
      throw new IOException(oldNoteFile.getName().toString() + " doesn't exist");
    }
    
    if (!newNoteFile.exists()) {
      newNoteFile.createFile();
    }
    
    oldNoteFile.moveTo(newNoteFile);

    LOG.info("File {} was renamed into {}", oldFile.getFile(), newFile.getFile());
    return newFile;
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
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    // no-op
    LOG.warn("Checkpoint feature isn't supported in {}", this.getClass().toString());
    return Revision.EMPTY;
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    LOG.warn("Get note revision feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    LOG.warn("Get Note revisions feature isn't supported in {}", this.getClass().toString());
    return Collections.emptyList();
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    NotebookRepoSettingsInfo repoSetting = NotebookRepoSettingsInfo.newInstance();
    List<NotebookRepoSettingsInfo> settings = Lists.newArrayList();

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

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    // Auto-generated method stub
    return null;
  }

}
