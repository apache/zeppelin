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

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Backend for storing Notebooks on Minio
 */
public class MinioNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(MinioNotebookRepo.class);

  private final String bucketName;
  private final String user;
  private final ZeppelinConfiguration conf;
  private final MinioClient client;

  public MinioNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    bucketName = conf.getMinioBucketName();
    user = conf.getMinioUser();
    String accessKey = conf.getMinioAccessKey();
    String secretKey = conf.getMinioSecretKey();
    boolean https = conf.useHTTPSForMinio();
    try {
      this.client = new MinioClient(conf.getMinioEndpoint(), accessKey, secretKey, https);
    } catch (Exception e) {
      throw new IOException("Unable to create Minio client: ", e);
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    List<NoteInfo> infos = new LinkedList<>();

    try {
      Iterable<Result<Item>> results = client.listObjects(bucketName, user + "/");
      for (Result<Item> result : results) {
        String id = result.get().objectName();
        if (id.endsWith("note.json")) {
          Note note = getNote(id);
          NoteInfo info = new NoteInfo(note);
          infos.add(info);
        }
      }
    } catch (Exception e) {
      LOG.error("Unable to list notes", e);
      throw new IOException("Unable to list notes", e);
    }

    return infos;
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    String key = user + "/" + "notebook" + "/" + noteId + "/" + "note.json";
    return getNote(key);
  }

  private Note getNote(String noteId) throws IOException {
    String noteJson;

    try {
      if (!client.bucketExists(bucketName)) {
        throw new IOException("Minio bucket does not exist");
      }
      InputStream object = client.getObject(bucketName, noteId);
      noteJson = IOUtils.toString(object,
          conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    } catch (Exception e) {
      LOG.error("Exception when getting note {} from Minio", noteId);
      throw new IOException("Unable to get note from Minio", e);
    }

    Note note = Note.fromJson(noteJson);
    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Job.Status.PENDING || p.getStatus() == Job.Status.RUNNING) {
        p.setStatus(Job.Status.ABORT);
      }
    }
    LOG.info("Note {} got from Minio", noteId);
    return note;
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    String json = note.toJson();
    String key = user + "/" + "notebook" + "/" + note.getId() + "/" + "note.json";

    File file = File.createTempFile("note", "json");
    try {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file));
      writer.write(json);
      writer.close();

      client.putObject(bucketName, key, file.getAbsolutePath());
      LOG.info("Saved note {}", key);
    } catch (Exception e) {
      throw new IOException("Unable to store note in Minio: " + e, e);
    } finally {
      FileUtils.deleteQuietly(file);
    }
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    String key = user + "/" + "notebook" + "/" + noteId + "/" + "note.json";
    try {
      client.removeObject(bucketName, key);
      LOG.info("Removed note {}", key);
    } catch (Exception e) {
      throw new IOException("Unable to remove note in Minio: " + e, e);
    }
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
    LOG.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
  }

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    // Auto-generated method stub
    return null;
  }
}
