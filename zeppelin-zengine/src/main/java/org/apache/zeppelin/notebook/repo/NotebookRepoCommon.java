package org.apache.zeppelin.notebook.repo;

import com.google.common.collect.Lists;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotebookRepoCommon {

  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoCommon.class);

  private FileSystemManager fileSystemManager;
  private URI filesystemRoot;
  private ZeppelinConfiguration zeppelinConfiguration;

  public NotebookRepoCommon(ZeppelinConfiguration zeppelinConfiguration) {
    this.zeppelinConfiguration = zeppelinConfiguration;
  }

  protected void setNotebookDirectory(String notebookDirPath) throws IOException {
    try {
      if (zeppelinConfiguration.isWindowsPath(notebookDirPath)) {
        filesystemRoot = new File(notebookDirPath).toURI();
      } else {
        filesystemRoot = new URI(notebookDirPath);
      }
    } catch (URISyntaxException e1) {
      throw new IOException(e1);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      File f = new File(zeppelinConfiguration.getRelativeDir(filesystemRoot.getPath()));
      this.filesystemRoot = f.toURI();
    }

    fileSystemManager = VFS.getManager();
    FileObject file = fileSystemManager.resolveFile(filesystemRoot.getPath());
    if (!file.exists()) {
      LOG.info("Notebook dir doesn't exist, create on is {}.", file.getName());
      file.createFolder();
    }
  }

  protected Note get(String noteId, AuthenticationInfo subject) throws IOException {
    FileObject rootDir = getFileSystemManager().resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    return getNote(noteDir);
  }

  protected Note getNote(FileObject noteDir) throws IOException {
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile("note.json", NameScope.CHILD);
    if (!noteJson.exists()) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }

    FileContent content = noteJson.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils
        .toString(ins, zeppelinConfiguration.getString(ZeppelinConfiguration.ConfVars
            .ZEPPELIN_ENCODING));
    ins.close();

    Note note = Note.fromJson(json);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Job.Status.PENDING || p.getStatus() == Job.Status.RUNNING) {
        p.setStatus(Job.Status.ABORT);
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

    return note;
  }

  protected List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    FileObject rootDir = getRootDir();

    FileObject[] children = rootDir.getChildren();

    List<NoteInfo> infos = new LinkedList<>();
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

  private NoteInfo getNoteInfo(FileObject noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new NoteInfo(note);
  }

  protected List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    NotebookRepoSettingsInfo repoSetting = NotebookRepoSettingsInfo.newInstance();
    List<NotebookRepoSettingsInfo> settings = Lists.newArrayList();

    repoSetting.name = "Notebook Path";
    repoSetting.type = NotebookRepoSettingsInfo.Type.INPUT;
    repoSetting.value = Collections.emptyList();
    repoSetting.selected = getNotebookDirPath();

    settings.add(repoSetting);
    return settings;
  }

  protected void save(Note note, AuthenticationInfo subject) throws IOException {
    LOG.info("Saving note:" + note.getId());
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
    out.write(json.getBytes(
        zeppelinConfiguration.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING)));
    out.close();
    noteJson.moveTo(noteDir.resolveFile("note.json", NameScope.CHILD));
  }

  protected FileObject getRootDir() throws IOException {
    FileObject rootDir = fileSystemManager.resolveFile(getPath("/"));

    if (!rootDir.exists()) {
      throw new IOException("Root path does not exists");
    }

    if (!isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }

  protected void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
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

  protected String getPath(String path) {
    if (path == null || path.trim().length() == 0) {
      return filesystemRoot.toString();
    }
    if (path.startsWith("/")) {
      return filesystemRoot.toString() + path;
    } else {
      return filesystemRoot.toString() + "/" + path;
    }
  }

  protected boolean isDirectory(FileObject fo) throws IOException {
    if (fo == null) {
      return false;
    }
    if (fo.getType() == FileType.FOLDER) {
      return true;
    } else {
      return false;
    }
  }

  private String getNotebookDirPath() {
    return getFilesystemRoot().getPath().toString();
  }

  public FileSystemManager getFileSystemManager() {
    return fileSystemManager;
  }

  public URI getFilesystemRoot() {
    return filesystemRoot;
  }
}
