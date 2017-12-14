package org.apache.zeppelin.notebook.repo;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.FileSystemStorage;
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

  private FileSystemStorage fs;
  private Path notebookDir;

  public FileSystemNotebookRepo(ZeppelinConfiguration zConf) throws IOException {
    this.fs = FileSystemStorage.get(zConf);
    this.notebookDir = this.fs.makeQualified(new Path(zConf.getNotebookDir()));
    LOGGER.info("Using folder {} to store notebook", notebookDir);
    this.fs.tryMkDir(notebookDir);

  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    List<Path> notePaths = fs.list(new Path(notebookDir, "*/note.json"));
    List<NoteInfo> noteInfos = new ArrayList<>();
    for (Path path : notePaths) {
      NoteInfo noteInfo = new NoteInfo(path.getParent().getName(), "", null);
      noteInfos.add(noteInfo);
    }
    return noteInfos;
  }

  @Override
  public Note get(final String noteId, AuthenticationInfo subject) throws IOException {
    String content = this.fs.readFile(
        new Path(notebookDir.toString() + "/" + noteId + "/note.json"));
    return Note.fromJson(content);
  }

  @Override
  public void save(final Note note, AuthenticationInfo subject) throws IOException {
    this.fs.writeFile(note.toJson(),
        new Path(notebookDir.toString() + "/" + note.getId() + "/note.json"),
        true);
  }

  @Override
  public void remove(final String noteId, AuthenticationInfo subject) throws IOException {
    this.fs.delete(new Path(notebookDir.toString() + "/" + noteId));
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
}
