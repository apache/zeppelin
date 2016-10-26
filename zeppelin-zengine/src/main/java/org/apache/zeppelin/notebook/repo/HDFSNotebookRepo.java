/**
 * Copyright 2015 NAVER Corp. All rights Reserved.
 */
package org.apache.zeppelin.notebook.repo;

import com.google.gson.Gson;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.file.HDFSCommand;
import org.apache.zeppelin.file.HDFSCommand.Arg;
import org.apache.zeppelin.file.HDFSFileInterpreter.AllFileStatus;
import org.apache.zeppelin.file.HDFSFileInterpreter.OneFileStatus;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.zeppelin.file.HDFSFileInterpreter.HDFS_URL;
import static org.apache.zeppelin.file.HDFSFileInterpreter.HDFS_MAXLENGTH;

/**
 *
 * HDFSNotebookRepo : Using HDFS to backup and restore notebook
 *
 */
public class HDFSNotebookRepo extends VFSNotebookRepo implements NotebookRepo {
  Logger logger = LoggerFactory.getLogger(HDFSNotebookRepo.class);

  public static final String HDFS_NOTEBOOK_DIR = "hdfs.notebook.dir";
  static final String NOTE_JSON = "note.json";
  static final String NOTE_JSON_TEMP = "_note.json";

  ZeppelinConfiguration conf;
  FileSystemManager fsManager;
  boolean enableWebHDFS = true;
  String hdfsUrl = null;
  String hdfsUser = null;
  int hdfsMaxLength = 0;
  String hdfsNotebookDir = null;
  HDFSCommand hdfsCmd = null;

  public HDFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);
    this.conf = conf;
    this.fsManager = VFS.getManager();
    this.hdfsUrl = conf.getString(HDFS_URL, HDFS_URL, "http://localhost:50070/webhdfs/v1/");
    this.hdfsMaxLength = conf.getInt(HDFS_URL, HDFS_MAXLENGTH, 100000);
    this.hdfsNotebookDir = conf.getString(HDFS_URL, HDFS_NOTEBOOK_DIR, "/tmp").replaceAll("/$", "");
    this.hdfsUser = System.getenv("HADOOP_USER_NAME");
    if (this.hdfsUser == null)
      this.hdfsUser = System.getenv("LOGNAME");

    if (this.hdfsUser == null) {
      this.enableWebHDFS = false;
    } else {
      this.hdfsCmd = new HDFSCommand(hdfsUrl, hdfsUser, logger, this.hdfsMaxLength);
      this.enableWebHDFS = checkWebHDFS();
    }
  }

  private boolean checkWebHDFS() {
    boolean ret = true;

    OneFileStatus fileStatus;
    Gson gson = new Gson();
    try {
      String notebookStatus = this.hdfsCmd.runCommand(this.hdfsCmd.getFileStatus, "/", null);
      fileStatus = gson.fromJson(notebookStatus, OneFileStatus.class);
      long modificationTime = fileStatus.modificationTime;
    } catch (Exception e) {
      logger.info("disabled webHDFS. Please check webhdfs configurations");
      ret = false;
    }
    finally {
      return ret;
    }
  }

  private List<String> getHdfsNotebook() {
    List<String> hdfsNotebook = new ArrayList<String>();
    Gson gson = new Gson();
    String hdfsDirStatus = null;

    try {
      hdfsDirStatus = this.hdfsCmd.runCommand(this.hdfsCmd.listStatus, this.hdfsNotebookDir, null);

      if (hdfsDirStatus != null) {
        AllFileStatus allFiles = gson.fromJson(hdfsDirStatus, AllFileStatus.class);
        if (allFiles != null && allFiles.FileStatuses != null
          && allFiles.FileStatuses.FileStatus != null) {
          for (OneFileStatus fs : allFiles.FileStatuses.FileStatus) {
            if ("DIRECTORY".equals(fs.type) && fs.pathSuffix.startsWith("_") == false) {
              hdfsNotebook.add(fs.pathSuffix);
              logger.info("read a notebook from HDFS: " + fs.pathSuffix);
            }
          }
        }
      }
    }
    catch (Exception e) {
      logger.error("Exception: ", e);
    }

    return hdfsNotebook;
  }

  private void downloadNotebook(String noteId, FileObject rootDir) throws IOException {
    logger.debug("download notebook from hdfs: " + rootDir.getName().getBaseName());
    OneFileStatus fileStatus;
    Gson gson = new Gson();
    String notebook = this.hdfsNotebookDir + "/" + noteId + "/" + NOTE_JSON;
    try {
      String notebookStatus = this.hdfsCmd.runCommand(this.hdfsCmd.getFileStatus, notebook, null);
      fileStatus = gson.fromJson(notebookStatus, OneFileStatus.class);
    } catch (Exception e) {
      logger.warn("Warning: ", e);
      return;
    }
    long length = fileStatus.length;
    if (length > this.hdfsMaxLength) {
      this.hdfsCmd = new HDFSCommand(this.hdfsUrl, this.hdfsUser, logger, (int) length);
    }
    FileObject noteDir;
    try {
      noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);
      this.hdfsCmd.runCommand(this.hdfsCmd.openFile, notebook, noteDir,
          conf.getString(ConfVars.ZEPPELIN_ENCODING), null);
    } catch (Exception e) {
      throw new IOException(e.getCause());
    }
  }

  private void syncHDFSNoteList() throws IOException {
    FileObject rootDir = super.getRootDir();
    FileObject[] children = rootDir.getChildren();
    List<String> hdfsNotebook = getHdfsNotebook();


    for (FileObject f : children) {
      String baseName = f.getName().getBaseName();
      logger.debug("read a notebook from local storage: " + baseName);

      if (f.isHidden() || f.getType() != FileType.FOLDER ||
         baseName.startsWith(".") || baseName.startsWith("#") || baseName.startsWith("~")) {
        continue;
      }

      if (hdfsNotebook.contains(baseName)) {
        hdfsNotebook.remove(baseName);
      }
      else {
        uploadNoteListToHDFS(baseName);
      }
    }

    for (String noteId : hdfsNotebook) {
      downloadNotebook(noteId, rootDir);
    }
  }

  private void uploadNoteListToHDFS(Note note) throws IOException {
    uploadNoteListToHDFS(note.id());
  }

  private void uploadNoteListToHDFS(String noteId) throws IOException {
    String localNotebook = super.getRootDir() + "/" + noteId + "/" + NOTE_JSON;
    FileObject localNote = super.getRootDir().resolveFile(noteId + "/" + NOTE_JSON);
    String noteDir = this.hdfsNotebookDir + "/" + noteId;
    String notebook = noteDir + "/" + NOTE_JSON;
    String newNotebook = noteDir + "/" + NOTE_JSON_TEMP;
    logger.debug("localNotebook: {}\tnotebook: {}", localNotebook, notebook);

    try {
      Arg user = this.hdfsCmd.new Arg("user.name", this.hdfsUser);
      Arg[] args = {user};
      this.hdfsCmd.runCommand(this.hdfsCmd.makeDirectory, noteDir, args);
      this.hdfsCmd.runCommand(this.hdfsCmd.CreateWriteFile, newNotebook, localNote, args);
      this.hdfsCmd.runCommand(this.hdfsCmd.DeleteFile, notebook, args);
      Arg dest = this.hdfsCmd.new Arg("destination", notebook);
      Arg[] renameArgs = {user, dest};
      this.hdfsCmd.runCommand(this.hdfsCmd.RenameFile, newNotebook, renameArgs);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  private void removeHDFSNote(String noteId) throws IOException {
    String noteDir = this.hdfsNotebookDir + "/" + noteId;
    logger.debug("remove noteDir: " + noteDir);

    Arg user = this.hdfsCmd.new Arg("user.name", this.hdfsUser);
    Arg recursive = this.hdfsCmd.new Arg("recursive", "true");
    Arg[] args = {user, recursive};

    try {
      this.hdfsCmd.runCommand(this.hdfsCmd.DeleteFile, noteDir, args);
    } catch (Exception e) {
      logger.error("Exception: ", e);
      throw new IOException(e.getCause());
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    if (this.enableWebHDFS)
      syncHDFSNoteList();
    return super.list(subject);
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    super.save(note, subject);
    if (this.enableWebHDFS)
      uploadNoteListToHDFS(note);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    if (this.enableWebHDFS)
      removeHDFSNote(noteId);
    super.remove(noteId, subject);
  }

}
