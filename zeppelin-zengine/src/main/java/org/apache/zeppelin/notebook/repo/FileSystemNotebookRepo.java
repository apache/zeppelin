package org.apache.zeppelin.notebook.repo;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NotebookRepos for hdfs.
 *
 * Assume the notebook directory structure is as following
 * - notebookdir
 *              - noteId/note.json
 *              - noteId/note.json
 *              - noteId/note.json
 */
public class FileSystemNotebookRepo implements NotebookRepo {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemNotebookRepo.class);

  private Configuration hadoopConf;
  private ZeppelinConfiguration zConf;
  private boolean isSecurityEnabled = false;
  private FileSystem fs;
  private Path notebookDir;

  public FileSystemNotebookRepo(ZeppelinConfiguration zConf) throws IOException {
    this.zConf = zConf;
    this.hadoopConf = new Configuration();

    this.isSecurityEnabled = UserGroupInformation.isSecurityEnabled();
    if (isSecurityEnabled) {
      String keytab = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      String principal = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);
      if (StringUtils.isBlank(keytab) || StringUtils.isBlank(principal)) {
        throw new IOException("keytab and principal can not be empty, keytab: " + keytab
            + ", principal: " + principal);
      }
      UserGroupInformation.loginUserFromKeytab(principal, keytab);
    }

    try {
      this.fs = FileSystem.get(new URI(zConf.getNotebookDir()), new Configuration());
      LOGGER.info("Creating FileSystem: " + this.fs.getClass().getCanonicalName());
      this.notebookDir = fs.makeQualified(new Path(zConf.getNotebookDir()));
      LOGGER.info("Using folder {} to store notebook", notebookDir);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
    if (!fs.exists(notebookDir)) {
      fs.mkdirs(notebookDir);
      LOGGER.info("Create notebook dir {} in hdfs", notebookDir.toString());
    }
    if (fs.isFile(notebookDir)) {
      throw new IOException("notebookDir {} is file instead of directory, please remove it or " +
          "specify another directory");
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    return callHdfsOperation(new HdfsOperation<List<NoteInfo>>() {
      @Override
      public List<NoteInfo> call() throws IOException {
        List<NoteInfo> noteInfos = new ArrayList<>();
        for (FileStatus status : fs.globStatus(new Path(notebookDir, "*/note.json"))) {
          NoteInfo noteInfo = new NoteInfo(status.getPath().getParent().getName(), "", null);
          noteInfos.add(noteInfo);
        }
        return noteInfos;
      }
    });
  }

  @Override
  public Note get(final String noteId, AuthenticationInfo subject) throws IOException {
    return callHdfsOperation(new HdfsOperation<Note>() {
      @Override
      public Note call() throws IOException {
        Path notePath = new Path(notebookDir.toString() + "/" + noteId + "/note.json");
        LOGGER.debug("Read note from file: " + notePath);
        ByteArrayOutputStream noteBytes = new ByteArrayOutputStream();
        IOUtils.copyBytes(fs.open(notePath), noteBytes, hadoopConf);
        return Note.fromJson(new String(noteBytes.toString(
            zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING))));
      }
    });
  }

  @Override
  public void save(final Note note, AuthenticationInfo subject) throws IOException {
    callHdfsOperation(new HdfsOperation<Void>() {
      @Override
      public Void call() throws IOException {
        Path notePath = new Path(notebookDir.toString() + "/" + note.getId() + "/note.json");
        Path tmpNotePath = new Path(notebookDir.toString() + "/" + note.getId() + "/.note.json");
        LOGGER.debug("Saving note to file: " + notePath);
        if (fs.exists(tmpNotePath)) {
          fs.delete(tmpNotePath, true);
        }
        InputStream in = new ByteArrayInputStream(note.toJson().getBytes(
            zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING)));
        IOUtils.copyBytes(in, fs.create(tmpNotePath), hadoopConf);
        fs.delete(notePath, true);
        fs.rename(tmpNotePath, notePath);
        return null;
      }
    });
  }

  @Override
  public void remove(final String noteId, AuthenticationInfo subject) throws IOException {
    callHdfsOperation(new HdfsOperation<Void>() {
      @Override
      public Void call() throws IOException {
        Path noteFolder = new Path(notebookDir.toString() + "/" + noteId);
        fs.delete(noteFolder, true);
        return null;
      }
    });
  }

  @Override
  public void close() {
    LOGGER.warn("close is not implemented for HdfsNotebookRepo");
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    LOGGER.warn("checkpoint is not implemented for HdfsNotebookRepo");
    return null;
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    LOGGER.warn("get revId is not implemented for HdfsNotebookRepo");
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    LOGGER.warn("revisionHistory is not implemented for HdfsNotebookRepo");
    return null;
  }

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    LOGGER.warn("setNoteRevision is not implemented for HdfsNotebookRepo");
    return null;
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOGGER.warn("getSettings is not implemented for HdfsNotebookRepo");
    return null;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOGGER.warn("updateSettings is not implemented for HdfsNotebookRepo");
  }

  private interface HdfsOperation<T> {
    T call() throws IOException;
  }

  public synchronized <T> T callHdfsOperation(final HdfsOperation<T> func) throws IOException {
    if (isSecurityEnabled) {
      UserGroupInformation.getLoginUser().reloginFromKeytab();
      try {
        return UserGroupInformation.getCurrentUser().doAs(new PrivilegedExceptionAction<T>() {
          @Override
          public T run() throws Exception {
            return func.call();
          }
        });
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    } else {
      return func.call();
    }
  }
}
