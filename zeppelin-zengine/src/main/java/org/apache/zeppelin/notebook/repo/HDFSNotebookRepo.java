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

import static org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod.KERBEROS;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class HDFSNotebookRepo implements NotebookRepo {

  private static final Logger LOG = LoggerFactory.getLogger(HDFSNotebookRepo.class);

  private URI uri;
  private Configuration hadoopConfig;
  private String stringPath;
  private FileSystem fileSystem;
  private Path notebookDirPath;
  private ZeppelinConfiguration conf;

  private Integer kinitFailCount = 0;
  private ScheduledExecutorService scheduledExecutorService;

  public HDFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    if (isKerboseEnabled()) {
      startKerberosLoginThread();
    }
    uri = URI.create(conf.getZeppelinHadoopUri());
    hadoopConfig = new Configuration();
    fileSystem = FileSystem.get(uri, hadoopConfig);
    setNotebookDirectory(conf.getNotebookDir());
  }

  private Long getKerberosRefreshInterval() {
    Long refreshInterval;
    String refreshIntervalString = "1d";
    //defined in zeppelin-env.sh, if not initialized then the default value is one day.
    if (StringUtils.isEmpty(conf.getKerberosRefreshInterval())) {
      refreshIntervalString = conf.getKerberosRefreshInterval();
    }
    try {
      refreshInterval = getTimeAsMs(refreshIntervalString);
    } catch (IllegalArgumentException e) {
      LOG.error("Cannot get time in MS for the given string, " + refreshIntervalString
          + " defaulting to 1d ", e);
      refreshInterval = getTimeAsMs("1d");
    }

    return refreshInterval;
  }

  private Long getTimeAsMs(String time) {
    if (time == null) {
      LOG.error("Cannot convert to time value.", time);
      time = "1d";
    }

    Matcher m = Pattern.compile("(-?[0-9]+)([a-z]+)?").matcher(time.toLowerCase());
    if (!m.matches()) {
      throw new IllegalArgumentException("Invalid time string: " + time);
    }

    long val = Long.parseLong(m.group(1));
    String suffix = m.group(2);

    if (suffix != null && !Constants.TIME_SUFFIXES.containsKey(suffix)) {
      throw new IllegalArgumentException("Invalid suffix: \"" + suffix + "\"");
    }

    return TimeUnit.MILLISECONDS.convert(val,
        suffix != null ? Constants.TIME_SUFFIXES.get(suffix) : TimeUnit.MILLISECONDS);
  }

  private Integer kinitFailThreshold() {
    Integer kinitFailThreshold = 5;
    //defined in zeppelin-env.sh, if not initialized then the default value is 5.
    if (!StringUtils.isEmpty(conf.getKinitFailThreshold())) {
      try {
        kinitFailThreshold = new Integer(conf.getKinitFailThreshold());
      } catch (Exception e) {
        LOG.error("Cannot get integer value from the given string, " + System
            .getenv("KINIT_FAIL_THRESHOLD") + " defaulting to " + kinitFailThreshold, e);
      }
    }
    return kinitFailThreshold;
  }

  private ScheduledExecutorService startKerberosLoginThread() {
    scheduledExecutorService = Executors.newScheduledThreadPool(1);

    scheduledExecutorService.submit(new Callable() {
      public Object call() throws Exception {

        if (runKerberosLogin()) {
          LOG.info("Ran runKerberosLogin command successfully.");
          kinitFailCount = 0;
          // schedule another kinit run with a fixed delay.
          scheduledExecutorService
              .schedule(this, getKerberosRefreshInterval(), TimeUnit.MILLISECONDS);
        } else {
          kinitFailCount++;
          LOG.info("runKerberosLogin failed for " + kinitFailCount + " time(s).");
          // schedule another retry at once or close the interpreter if too many times kinit fails
          if (kinitFailCount >= kinitFailThreshold()) {
            LOG.error("runKerberosLogin failed for  max attempts, calling close interpreter.");
            close();
          } else {
            scheduledExecutorService.submit(this);
          }
        }
        return null;
      }
    });

    return scheduledExecutorService;
  }

  protected boolean isKerboseEnabled() {
    if (!StringUtils.isEmpty(conf.getKerberosPrincipal()) &&
        !StringUtils.isEmpty(conf.getKerberoskeyTab())) {
      return true;
    }
    return false;
  }

  protected boolean runKerberosLogin() {
    try {
      Configuration conf = new
          org.apache.hadoop.conf.Configuration();
      conf.set("hadoop.security.authentication", KERBEROS.toString());
      UserGroupInformation.setConfiguration(conf);
      try {
        UserGroupInformation.loginUserFromKeytab(
            this.conf.getKerberosPrincipal(),
            this.conf.getKerberoskeyTab()
        );

        if (UserGroupInformation.isLoginKeytabBased()) {
          UserGroupInformation.getLoginUser().reloginFromKeytab();
          return true;
        } else if (UserGroupInformation.isLoginTicketBased()) {
          UserGroupInformation.getLoginUser().reloginFromTicketCache();
          return true;
        }

      } catch (IOException e) {
        LOG.error("Failed to get either keytab location or principal name in the " +
            "interpreter", e);
      }
      return true;
    } catch (Exception e) {
      LOG.error("Unable to run kinit for zeppelin", e);
    }
    return false;
  }

  private void setNotebookDirectory(String notebookDirPath) throws IOException {
    stringPath = notebookDirPath;
    setNotebookDirPath(getPath(stringPath));
    if (!fileSystem.exists(getRootDir())) {
      LOG.info("Notebook dir doesn't exist, create on is {}.", stringPath);
      createFolder(this.notebookDirPath);
    }
  }

  protected Path getRootDir() throws IOException {
    if (!fileSystem.exists(fileSystem.getHomeDirectory())) {
      throw new IOException("Root path does not exists");
    }
    if (!isDirectory(fileSystem.getHomeDirectory())) {
      throw new IOException("Root path is not a directory");
    }
    return getNotebookDirPath();
  }

  private Path getPath(String path) {
    String absolutePath;
    if (path == null || path.trim().length() == 0) {
      absolutePath = fileSystem.getHomeDirectory().toString();
    } else if (path.startsWith("/")) {
      absolutePath = fileSystem.getHomeDirectory().toString() + path;
    } else {
      absolutePath = fileSystem.getHomeDirectory().toString() + "/" + path;
    }
    return new Path(absolutePath);
  }

  private boolean isDirectory(Path path) throws IOException {
    if (fileSystem == null) {
      return false;
    }
    FileStatus fileStatus = null;
    try {
      fileStatus = fileSystem.getFileStatus(path);
    } catch (FileNotFoundException e) {
      LOG.info("File/Directory not found, nothing to worry:" + path);
      return false;
    }
    if (fileStatus.isDirectory()) {
      return true;
    } else {
      return false;
    }
  }

  private void createFolder(Path path) throws IOException {
    fileSystem.mkdirs(path);
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    FileStatus[] children = fileSystem.listStatus(getRootDir());

    List<NoteInfo> infos = new LinkedList<>();
    for (FileStatus f : children) {
      String fileName = f.getPath().getName();
      if (fileName.startsWith(".")
          || fileName.startsWith("#")
          || fileName.startsWith("~")) {
        // skip hidden, temporary files
        continue;
      }

      if (!f.isDirectory()) {
        // currently single note is saved like, [NOTE_ID]/note.json.
        // so it must be a directory
        continue;
      }

      NoteInfo info = null;

      try {
        info = getNoteInfo(f.getPath());
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        LOG.error("Can't read note " + f.getPath().getName().toString(), e);
      }
    }

    return infos;
  }

  private Path getNoteFilePath(String noteId) {
    return getPath(stringPath + "/" + noteId + "/note.json");
  }

  private Path getNoteDirectoryPath(String noteId) {
    return getPath(stringPath + "/" + noteId);
  }

  private Note getNote(String noteId) throws IOException {
    Path noteDir = getNoteDirectoryPath(noteId);
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    Path noteJson = getNoteFilePath(noteId);
    if (!fileSystem.exists(noteJson)) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }

    String json = readFile(noteJson);

    Note note = Note.fromJson(json);

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

    return note;
  }

  private NoteInfo getNoteInfo(Path noteDir) throws IOException {
    Note note = getNote(noteDir.getName());
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    return getNote(noteId);
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    String json = note.toJson();

    Path noteDir = getNoteDirectoryPath(note.getId());

    if (!isDirectory(noteDir)) {
      try {
        createFolder(noteDir);
      } catch (Exception e) {
        LOG.error("Cannot create directory" + noteDir.getName().toString(), e);
        throw new IOException("Cannot create directory" + noteDir.getName().toString());
      }
    }
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    try {
      writeFile(json, getNoteFilePath(note.getId()));
    } catch (Exception e) {
      LOG.error("Cannot write file, " + noteDir.getName().toString(), e);
      throw new IOException("Cannot create directory" + noteDir.getName().toString());
    }
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    Path noteDir = getNoteDirectoryPath(noteId);

    if (!fileSystem.exists(noteDir)) {
      // nothing to do
      return;
    }

    if (!isDirectory(noteDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + noteDir.getName().toString());
    }

    deleteFile(noteDir);
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
    repoSetting.selected = getNotebookDirPath().getName().toString();

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

  public String readFile(Path path) throws IOException {
    if (!fileSystem.exists(path)) {
      return null;
    }

    StringBuilder fileContent = new StringBuilder();
    FSDataInputStream in = fileSystem.open(path);
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      fileContent.append(line).append("\n");
    }
    return fileContent.toString();
  }

  public void writeFile(String content, Path path) throws IOException {
    // delete if it already exists

    if (fileSystem.exists(path)) {
      fileSystem.delete(path, true);
    }

    FSDataOutputStream stm = fileSystem.create(path);
    if (content != null) {
      stm.writeBytes(content);
    }
    stm.close();
  }

  public void deleteFile(Path path) throws IOException {
    if (fileSystem.exists(path)) {
      fileSystem.delete(path, true);
    }
  }

  private Path getNotebookDirPath() {
    return notebookDirPath;
  }

  public void setNotebookDirPath(Path notebookDirPath) {
    this.notebookDirPath = notebookDirPath;
  }

}
