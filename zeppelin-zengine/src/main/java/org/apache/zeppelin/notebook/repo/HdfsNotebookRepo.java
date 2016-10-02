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
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.*;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.util.HdfsUtils;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */

public class HdfsNotebookRepo implements NotebookRepo {
  private Logger logger = LoggerFactory.getLogger(HdfsNotebookRepo.class);
  private HdfsUtils hdfsUtils;
  private ZeppelinConfiguration conf;


  public HdfsNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;
    String hadoopConfDir = conf.getString("HADOOP_CONF_DIR");
    try {
      hdfsUtils = new HdfsUtils(conf.getNotebookDir(), hadoopConfDir);
      hdfsUtils.mkdirs(hdfsUtils.getRootPath());
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    Path[] children = hdfsUtils.listFiles(hdfsUtils.getRootPath());
    List<NoteInfo> infos = new LinkedList<NoteInfo>();
    for (Path child : children) {
      String fileName = child.getName();
      if (fileName.startsWith(".")
          || fileName.startsWith("#")
          || fileName.startsWith("~")) {
        // skip hidden, temporary files
        continue;
      }

      if (!hdfsUtils.isDirectory(child)) {
        // currently single note is saved like, [NOTE_ID]/note.json.
        // so it must be a directory
        continue;
      }

      NoteInfo info = null;

      try {
        info = getNoteInfo(child);
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        logger.error("Can't read note " + fileName, e);
      }
    }

    return infos;
  }

  private Note getNote(Path noteDir) throws IOException {
    if (!hdfsUtils.isDirectory(noteDir)) {
      throw new IOException(noteDir.getName() + " is not a directory");
    }

    Path noteJson = new Path(noteDir, "note.json");
    if (!hdfsUtils.exists(noteJson)) {
      throw new IOException(noteJson.getName() + " not found");
    }

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.registerTypeAdapter(Date.class, new NotebookImportDeserializer())
        .create();

    byte[] content = hdfsUtils.readFile(noteJson);
    String json = new String(content, conf.getString(ConfVars.ZEPPELIN_ENCODING));

    Note note = gson.fromJson(json, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }
    }

    return note;
  }

  private NoteInfo getNoteInfo(Path noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    Path path = new Path(hdfsUtils.getRootPath(), noteId);
    return getNote(path);
  }

  protected Path getRootDir() throws IOException {
    Path rootDir = hdfsUtils.getRootPath();
    if (!hdfsUtils.exists(rootDir)) {
      throw new IOException("Root path does not exists");
    }

    if (!hdfsUtils.isDirectory(rootDir)) {
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

    Path rootDir = hdfsUtils.getRootPath();
    Path noteDir = new Path(rootDir, note.getId());

    if (!hdfsUtils.exists(noteDir)) {
      hdfsUtils.mkdirs(noteDir);
    }
    if (!hdfsUtils.isDirectory(noteDir)) {
      throw new IOException(noteDir.getName() + " is not a directory");
    }

    Path noteJson = new Path(noteDir, "note.json");
    hdfsUtils.writeFile(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)), noteJson);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    Path rootDir = hdfsUtils.getRootPath();
    Path noteDir = new Path(rootDir, noteId);

    if (!hdfsUtils.exists(noteDir)) {
      throw new IOException("Can not remove " + noteDir.getName());
    }
    hdfsUtils.delete(noteDir);
  }

  @Override
  public void close() {
    //no-op
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    // Auto-generated method stub
    return null;
  }

  @Override
  public Note get(String noteId, Revision rev, AuthenticationInfo subject) throws IOException {
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    // Auto-generated method stub
    return null;
  }

}
