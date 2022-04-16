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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.storage.OSSOperator;
import org.apache.zeppelin.notebook.repo.storage.RemoteStorageOperator;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * NotebookRepo for Aliyun OSS (https://cn.aliyun.com/product/oss)
 */
public class OSSNotebookRepo implements NotebookRepoWithVersionControl {
  private static final Logger LOGGER = LoggerFactory.getLogger(OSSNotebookRepo.class);

  private String bucketName;
  private String rootFolder;
  private static int NOTE_MAX_VERSION_NUM;

  // Use ossOperator instead of ossClient directly
  private RemoteStorageOperator ossOperator;

  public OSSNotebookRepo() {
  }

  @Override
  public void init(ZeppelinConfiguration conf) throws IOException {
    String endpoint = conf.getOSSEndpoint();
    bucketName = conf.getOSSBucketName();
    rootFolder = conf.getNotebookDir();
    NOTE_MAX_VERSION_NUM = conf.getOSSNoteMaxVersionNum();
    // rootFolder is part of OSS key
    rootFolder = formatPath(rootFolder);
    String accessKeyId = conf.getOSSAccessKeyId();
    String accessKeySecret = conf.getOSSAccessKeySecret();
    this.ossOperator = new OSSOperator(endpoint, accessKeyId, accessKeySecret);
  }

  private static String formatPath(String path) {
    // The path should not start with '/' or './' or './/'
    // because it is not accepted by OSS service.
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    path = new File(path).getPath();
    if (path.startsWith("./")) {
      path = path.substring(2);
    }
    return path;
  }

  public void setOssOperator(RemoteStorageOperator ossOperator) {
    this.ossOperator = ossOperator;
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    Map<String, NoteInfo> notesInfo = new HashMap<>();
    List<String> objectKeys = ossOperator.listDirObjects(bucketName, rootFolder + "/");
    for (String key : objectKeys) {
      if (key.endsWith(".zpln")) {
        try {
          String noteId = getNoteId(key);
          String notePath = getNotePath(rootFolder, key);
          notesInfo.put(noteId, new NoteInfo(noteId, notePath));
        } catch (IOException e) {
          LOGGER.warn(e.getMessage());
        }
      } else {
        LOGGER.debug("Skip invalid note file: {}", key);
      }
    }
    return notesInfo;
  }

  public Note getByOSSPath(String noteId, String ossPath) throws IOException {
    String noteText = ossOperator.getTextObject(bucketName, ossPath);
    return Note.fromJson(noteId, noteText);
  }


  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    return getByOSSPath(noteId, rootFolder + "/" + buildNoteFileName(noteId, notePath));
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    String content = note.toJson();
    ossOperator.putTextObject(bucketName,
            rootFolder + "/" + buildNoteFileName(note.getId(), note.getPath()),
            new ByteArrayInputStream(content.getBytes()));
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath,
                   AuthenticationInfo subject) throws IOException {
    String noteSourceKey = rootFolder + "/" + buildNoteFileName(noteId, notePath);
    String noteDestKey = rootFolder + "/" + buildNoteFileName(noteId, newNotePath);
    ossOperator.moveObject(bucketName, noteSourceKey, noteDestKey);
    String revisionSourceDirKey = rootFolder + "/" + buildRevisionsDirName(noteId, notePath);
    String revisionDestDirKey = rootFolder + "/" + buildRevisionsDirName(noteId, newNotePath);
    ossOperator.moveDir(bucketName, revisionSourceDirKey, revisionDestDirKey);
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {
    List<String> objectKeys = ossOperator.listDirObjects(bucketName, rootFolder + folderPath + "/");
    for (String key : objectKeys) {
      if (key.endsWith(".zpln")) {
        try {
          String noteId = getNoteId(key);
          String notePath = getNotePath(rootFolder, key);
          String newNotePath = newFolderPath + notePath.substring(folderPath.length());
          move(noteId, notePath, newNotePath, subject);
        } catch (IOException e) {
          LOGGER.warn(e.getMessage());
        }
      } else {
        LOGGER.debug("Skip invalid note file: {}", key);
      }
    }

  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject)
          throws IOException {
    ossOperator.deleteFile(bucketName, rootFolder + "/" + buildNoteFileName(noteId, notePath));
    // if there is no file under revisonInfoPath, deleleDir() would do nothing
    ossOperator.deleteDir(bucketName, rootFolder + "/" + buildRevisionsDirName(noteId, notePath));
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    List<String> objectKeys = ossOperator.listDirObjects(bucketName, rootFolder + folderPath + "/");
    for (String key : objectKeys) {
      if (key.endsWith(".zpln")) {
        try {
          String noteId = getNoteId(key);
          String notePath = getNotePath(rootFolder, key);
          // delete note revision file
          ossOperator.deleteDir(bucketName, rootFolder + "/" + buildRevisionsDirName(noteId, notePath));
        } catch (IOException e) {
          LOGGER.warn(e.getMessage());
        }
      }
    }
    // delete note file
    ossOperator.deleteFiles(bucketName, objectKeys);
  }


  @Override
  public void close() {
    ossOperator.shutdown();
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


  private static String buildRevisionsDirName(String noteId, String notePath) throws IOException {
    if (!notePath.startsWith("/")) {
      throw new IOException("Invalid notePath: " + notePath);
    }
    return ".checkpoint/" + (notePath + "_" + noteId).substring(1);
  }

  private String buildRevisionsInfoAbsolutePath(String noteId, String notePath) throws IOException {
    return rootFolder + "/" + buildRevisionsDirName(noteId, notePath) + "/" + ".revision-info";
  }

  private String buildRevisionsFileAbsolutePath(String noteId, String notePath, String revisionId) throws IOException {
    return rootFolder + "/" + buildRevisionsDirName(noteId, notePath) + "/" + revisionId;
  }


  @Override
  public Revision checkpoint(String noteId, String notePath, String checkpointMsg, AuthenticationInfo subject) throws IOException {
    if (NOTE_MAX_VERSION_NUM <= 0) {
      throw new IOException("Version control is closed because the value of zeppelin.notebook.oss.version.max is set to 0");
    }

    Note note = get(noteId, notePath, subject);

    //1 Write note content to revision file
    String revisionId = UUID.randomUUID().toString().replace("-", "");
    String noteContent = note.toJson();
    ossOperator.putTextObject(bucketName,
            buildRevisionsFileAbsolutePath(noteId, notePath, revisionId),
            new ByteArrayInputStream(noteContent.getBytes()));

    //2 Append revision info
    Revision revision = new Revision(revisionId, checkpointMsg, (int) (System.currentTimeMillis() / 1000L));
    // check revision info file if existed
    RevisionsInfo revisionsHistory = new RevisionsInfo();
    String revisonInfoPath = buildRevisionsInfoAbsolutePath(noteId, notePath);
    boolean found = ossOperator.doesObjectExist(bucketName, revisonInfoPath);
    if (found) {
      String existedRevisionsInfoText = ossOperator.getTextObject(bucketName, revisonInfoPath);
      revisionsHistory = RevisionsInfo.fromText(existedRevisionsInfoText);
      // control the num of revison files, clean the oldest one if it exceeds.
      if (revisionsHistory.size() >= NOTE_MAX_VERSION_NUM) {
        Revision deletedRevision = revisionsHistory.removeLast();
        ossOperator.deleteFile(bucketName, buildRevisionsFileAbsolutePath(noteId, notePath, deletedRevision.id));
      }
    }
    revisionsHistory.addFirst(revision);

    ossOperator.putTextObject(bucketName,
            buildRevisionsInfoAbsolutePath(noteId, notePath),
            new ByteArrayInputStream(revisionsHistory.toText().getBytes()));

    return revision;
  }

  @Override
  public Note get(String noteId, String notePath, String revId, AuthenticationInfo subject) throws IOException {
    Note note = getByOSSPath(noteId,  buildRevisionsFileAbsolutePath(noteId, notePath, revId));
    if (note != null) {
      note.setPath(notePath);
    }
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    if (NOTE_MAX_VERSION_NUM <= 0) {
      return new ArrayList<>();
    }

    List<Revision> revisions = new LinkedList<>();
    String revisonInfoPath = buildRevisionsInfoAbsolutePath(noteId, notePath);
    boolean found = ossOperator.doesObjectExist(bucketName, revisonInfoPath);
    if (!found) {
      return revisions;
    }
    String revisionsText = ossOperator.getTextObject(bucketName, revisonInfoPath);

    return RevisionsInfo.fromText(revisionsText);
  }

  @Override
  public Note setNoteRevision(String noteId, String notePath, String revId, AuthenticationInfo subject) throws IOException {
    Note revisionNote = get(noteId, notePath, revId, subject);
    if (revisionNote != null) {
      save(revisionNote, subject);
    }
    return revisionNote;
  }

}
