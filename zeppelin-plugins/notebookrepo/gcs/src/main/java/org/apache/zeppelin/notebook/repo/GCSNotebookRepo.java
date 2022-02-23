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

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonParseException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A NotebookRepo implementation for storing notebooks in Google Cloud Storage.
 *
 * Notes are stored in the GCS "directory" specified by zeppelin.notebook.gcs.dir. This path
 * must be in the form gs://bucketName/path/to/Dir. The bucket must already exist. N.B: GCS is an
 * object store, so this "directory" should not itself be an object. Instead, it represents the base
 * path for the note.json files.
 *
 * Authentication is provided by google-auth-library-java. A custom json key file path
 * can be specified by zeppelin.notebook.google.credentialsJsonFilePath to connect with GCS
 * If not specified the GOOGLE_APPLICATION_CREDENTIALS will be used to connect to GCS.
 * @see <a href="https://github.com/google/google-auth-library-java">
 *   google-auth-library-java</a>.
 */
public class GCSNotebookRepo implements NotebookRepo {

  private static final Logger LOGGER = LoggerFactory.getLogger(GCSNotebookRepo.class);
  private String encoding;
  private String bucketName;
  private Optional<String> basePath;
  private Pattern notePathPattern;
  private Storage storage;

  public GCSNotebookRepo() {
  }

  @VisibleForTesting
  public GCSNotebookRepo(ZeppelinConfiguration zConf, Storage storage) throws IOException {
    init(zConf);
    this.storage = storage;
  }

  @Override
  public void init(ZeppelinConfiguration zConf) throws IOException {
    this.encoding =  zConf.getString(ConfVars.ZEPPELIN_ENCODING);

    String gcsStorageDir = zConf.getGCSStorageDir();
    if (gcsStorageDir.isEmpty()) {
      throw new IOException("GCS storage directory must be set using 'zeppelin.notebook.gcs.dir'");
    }
    if (!gcsStorageDir.startsWith("gs://")) {
      throw new IOException(String.format(
          "GCS storage directory '%s' must start with 'gs://'.", gcsStorageDir));
    }
    String storageDirWithoutScheme = gcsStorageDir.substring("gs://".length());

    // pathComponents excludes empty string if trailing slash is present
    List<String> pathComponents = Arrays.asList(storageDirWithoutScheme.split("/"));
    if (pathComponents.isEmpty()) {
      throw new IOException(String.format(
          "GCS storage directory '%s' must be in the form gs://bucketname/path/to/dir",
          gcsStorageDir));
    }
    this.bucketName = pathComponents.get(0);
    if (pathComponents.size() > 1) {
      this.basePath = Optional.of(StringUtils.join(
          pathComponents.subList(1, pathComponents.size()), "/"));
    } else {
      this.basePath = Optional.empty();
    }

    // Notes are stored at gs://bucketName/basePath/<note-name>_<note-id>.zpln
    if (basePath.isPresent()) {
      this.notePathPattern = Pattern.compile(
          "^" + Pattern.quote(basePath.get() + "/") + "(.+\\.zpln)$");
    } else {
      this.notePathPattern = Pattern.compile("^(.+\\.zpln)$");
    }

    Credentials credentials = GoogleCredentials.getApplicationDefault();
    String credentialJsonPath = zConf.getString(ConfVars.ZEPPELIN_NOTEBOOK_GCS_CREDENTIALS_FILE);
    if (credentialJsonPath != null) {
      credentials = GoogleCredentials.fromStream(new FileInputStream(credentialJsonPath));
    }
    this.storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
  }

  private BlobId makeBlobId(String noteId, String notePath) throws IOException {
    if (basePath.isPresent()) {
      return BlobId.of(bucketName, basePath.get() + "/" + buildNoteFileName(noteId, notePath));
    } else {
      return BlobId.of(bucketName, buildNoteFileName(noteId, notePath));
    }
  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    try {
      Map<String, NoteInfo> infos = new HashMap<>();
      Iterable<Blob> blobsUnderDir;
      if (basePath.isPresent()) {
        blobsUnderDir = storage
          .list(bucketName, BlobListOption.prefix(this.basePath.get() + "/"))
          .iterateAll();
      } else {
        blobsUnderDir = storage
          .list(bucketName)
          .iterateAll();
      }
      for (Blob b : blobsUnderDir) {
        Matcher matcher = notePathPattern.matcher(b.getName());
        if (matcher.matches()) {
          // Callers only use the id field, so do not fetch each note
          // This matches the implementation in FileSystemNoteRepo#list
          String noteFileName = matcher.group(1);
          try {
            String noteId = getNoteId(noteFileName);
            String notePath = getNotePath("", noteFileName);
            infos.put(noteId, new NoteInfo(noteId, notePath));
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        }
      }
      return infos;
    } catch (StorageException se) {
      throw new IOException("Could not list GCS directory: " + se.getMessage(), se);
    }
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    BlobId blobId = makeBlobId(noteId, notePath);
    byte[] contents;
    try {
      contents = storage.readAllBytes(blobId);
    } catch (StorageException se) {
      throw new IOException("Could not read " + blobId.toString() + ": " + se.getMessage(), se);
    }

    try {
      return Note.fromJson(noteId, new String(contents, encoding));
    } catch (JsonParseException jpe) {
      throw new IOException(
          "Could note parse as json " + blobId.toString() + jpe.getMessage(), jpe);
    }
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    BlobInfo info = BlobInfo.newBuilder(makeBlobId(note.getId(), note.getPath()))
        .setContentType("application/json")
        .build();
    try {
      storage.create(info, note.toJson().getBytes(StandardCharsets.UTF_8));
    } catch (StorageException se) {
      throw new IOException("Could not write " + info.toString() + ": " + se.getMessage(), se);
    }
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) throws IOException {
    Preconditions.checkArgument(StringUtils.isNotEmpty(noteId));
    BlobId sourceBlobId = makeBlobId(noteId, notePath);
    BlobId destinationBlobId = makeBlobId(noteId, newNotePath);
    try {
      storage.get(sourceBlobId).copyTo(destinationBlobId);
    } catch (Exception se) {
      throw new IOException("Could not copy from " + sourceBlobId.toString() + " to " + destinationBlobId.toString() + ": " + se.getMessage(), se);
    }
    remove(noteId, notePath, subject);
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) throws IOException{
    if(!folderPath.endsWith("/")) {
      folderPath = folderPath + "/";
    }
    if(!newFolderPath.endsWith("/")) {
      newFolderPath = newFolderPath + "/";
    }

    if(basePath.isPresent()) {
      folderPath = basePath.get() + "/" + folderPath;
      newFolderPath = basePath.get() + "/" + newFolderPath;
    }
    String oldPath = folderPath;
    String newPath = newFolderPath;
    try {
      ArrayList<BlobId> toBeDeleted = new ArrayList();
      storage.list(bucketName, Storage.BlobListOption.prefix(oldPath)).getValues()
              .forEach((note -> {
                toBeDeleted.add(note.getBlobId());
              }));
      if(toBeDeleted.isEmpty()) {
        throw new IOException("Empty folder or folder does not exist: " + oldPath);
      }
      toBeDeleted.forEach((note -> {
                storage.get(note).copyTo(bucketName, note.getName().replaceFirst(oldPath, newPath));
                storage.delete(note);
      }));
    } catch (Exception se) {
      throw new IOException("Could not copy from " + oldPath + " to " + newPath + ": " + se.getMessage(), se);
    }
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    Preconditions.checkArgument(StringUtils.isNotEmpty(noteId));
    BlobId blobId = makeBlobId(noteId, notePath);
    try {
      boolean deleted = storage.delete(blobId);
      if (!deleted) {
        throw new IOException("Tried to remove nonexistent blob " + blobId.toString());
      }
    } catch (StorageException se) {
      throw new IOException("Could not remove " + blobId.toString() + ": " + se.getMessage(), se);
    }
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) throws IOException {
    if(!folderPath.endsWith("/")) {
      folderPath = folderPath + "/";
    }
    if(basePath.isPresent()) {
      folderPath = basePath.get() + "/" + folderPath;
    }
    String oldPath = folderPath;
    try {
      ArrayList<BlobId> toBeDeleted = new ArrayList();
      storage.list(bucketName, Storage.BlobListOption.prefix(oldPath)).getValues()
              .forEach((note -> {
                toBeDeleted.add(note.getBlobId());
              }));
      if(toBeDeleted.isEmpty()) {
        throw new IOException("Empty folder or folder does not exist: " + oldPath);
      }
      // Note(Bagus): We an actually do this with storage.delete(toBeDeleted) but FakeStorageRPC used for tests still does not support it
      toBeDeleted.forEach((note -> {
        storage.delete(note);
      }));
    } catch (Exception se) {
      throw new IOException("Could not delete from " + oldPath + ": " + se.getMessage(), se);
    }
  }

  @Override
  public void close() {
    //no-op
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOGGER.warn("getSettings is not implemented for GCSNotebookRepo");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOGGER.warn("updateSettings is not implemented for GCSNotebookRepo");
  }
}
