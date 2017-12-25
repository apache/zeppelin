/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.GoogleCloudStorage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backend for storing Notebooks on Google Cloud Storage
 */
public class GoogleCloudStorageNotebookRepo implements NotebookRepo {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageNotebookRepo.class);

  private final ZeppelinConfiguration conf;
  private final GoogleCloudStorage cloudStorage;

  public GoogleCloudStorageNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    this.cloudStorage = GoogleCloudStorage.get(this.conf);
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    List<NoteInfo> infos = new LinkedList<>();

    try {
      List<String> noteBooks = this.cloudStorage.listNotes();
      for (String note : noteBooks)
        infos.add(getNoteInfo(note));

    } catch (Exception ace) {
      throw new IOException("Unable to list objects in Google Cloud Storage: " + ace, ace);
    }
    return infos;
  }

  private Note getNote(String notePath) throws IOException {
    String noteContent;
    try {
      noteContent = this.cloudStorage.getNote(notePath);
    } catch (Exception ace) {
      throw new IOException("Unable to retrieve object from Google Cloud Storage: " + notePath,
          ace);
    }

    Note note = Note.GSON.fromJson(noteContent, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }
    }

    return note;
  }

  private NoteInfo getNoteInfo(String notePath) throws IOException {
    Note note = getNote(notePath);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    return getNote("/" + "notebook" + "/" + noteId + "/" + "note.json");
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {

    String noteJson = Note.GSON.toJson(note);
    String notePath = "/" + "notebook" + "/" + note.getId() + "/" + "note.json";

    try {
      this.cloudStorage.putNote(notePath, noteJson);
    } catch (Exception ace) {
      throw new IOException("Unable to store note in Google Cloud Storage: " + ace, ace);
    }
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    String notePath = "/" + "notebook" + "/" + noteId;

    try {
      this.cloudStorage.deleteNote(notePath);
    } catch (Exception ace) {
      throw new IOException("Unable to remove note in Google Cloud Storage: " + ace, ace);
    }
  }

  @Override
  public void close() {
    // no-op
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
