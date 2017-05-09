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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.util.HdfsSite;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.apache.zeppelin.file.HDFSFileInterpreter.HDFS_URL;

/**
 *
 */

public class HdfsNotebookRepo implements NotebookRepo {
  private static Logger logger = LoggerFactory.getLogger(HdfsNotebookRepo.class);
  private HdfsSite hdfsSite;
  private ZeppelinConfiguration conf;
  private String rootDir;


  public HdfsNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    try {
      rootDir = removeProtocol(conf.getNotebookDir());
      hdfsSite = new HdfsSite(conf);
      hdfsSite.mkdirs(rootDir);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  public String removeProtocol(String hdfsUrl) {
    String newUrl = hdfsUrl.replaceAll("/$", "");
    if (newUrl.startsWith("hdfs://")) {
      return "/" + newUrl.replaceAll("^hdfs://", "").split("/", 2)[1];
    } else {
      return newUrl;
    }
  }


  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    String[] children = hdfsSite.listFiles(rootDir);
    List<NoteInfo> infos = new LinkedList<>();
    for (String child : children) {
      String fileName = child;
      if (fileName.startsWith(".")
          || fileName.startsWith("#")
          || fileName.startsWith("~")) {
        // skip hidden, temporary files
        continue;
      }

      if (!hdfsSite.isDirectory(rootDir + "/" + child)) {
        // currently single note is saved like, [NOTE_ID]/note.json.
        // so it must be a directory
        continue;
      }

      NoteInfo info = null;

      try {
        info = getNoteInfo(rootDir + "/" + child);
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        logger.error("Can't read note " + fileName, e);
      }
    }

    return infos;
  }

  private Note getNote(String noteDir) throws IOException {
    if (!hdfsSite.isDirectory(noteDir)) {
      throw new IOException(noteDir + " is not a directory");
    }

    String noteJson = noteDir + "/" + "note.json";
    if (!hdfsSite.exists(noteJson)) {
      throw new IOException(noteJson + " not found");
    }

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.registerTypeAdapter(Date.class, new NotebookImportDeserializer())
        .create();

    byte[] content = hdfsSite.readFile(noteJson);
    String json = new String(content, conf.getString(ConfVars.ZEPPELIN_ENCODING));

    Note note = gson.fromJson(json, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }
    }
    return note;
  }

  private NoteInfo getNoteInfo(String noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    String path = rootDir + "/" + noteId;
    return getNote(path);
  }

  protected String getRootDir() throws IOException {
    if (!hdfsSite.exists(rootDir)) {
      throw new IOException("Root path does not exists");
    }

    if (!hdfsSite.isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }
    return rootDir;
  }

  @Override
  public synchronized void save(Note note, AuthenticationInfo subject) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    String json = gson.toJson(note);

    String noteDir = rootDir + "/" + note.getId();

    if (!hdfsSite.exists(noteDir)) {
      hdfsSite.mkdirs(noteDir);
    }
    if (!hdfsSite.isDirectory(noteDir)) {
      throw new IOException(noteDir + " is not a directory");
    }

    String noteJson = noteDir + "/" + "note.json";
    hdfsSite.writeFile(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)), noteJson);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    String noteDir = rootDir + "/" + noteId;

    if (!hdfsSite.exists(noteDir)) {
      throw new IOException("Can not remove " + noteDir);
    }
    hdfsSite.delete(noteDir);
  }

  @Override
  public void close() {
    
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    return null;
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    return null;
  }

  @Override
  public Note setNoteRevision(String noteId, String revId, AuthenticationInfo subject)
      throws IOException {
    return null;
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    return null;
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {

  }
}
