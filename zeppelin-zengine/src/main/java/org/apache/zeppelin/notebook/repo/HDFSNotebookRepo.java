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
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

/**
*
*/
public class HDFSNotebookRepo implements NotebookRepo {
  Logger logger = LoggerFactory.getLogger(HDFSNotebookRepo.class);

  private FileSystem fs;
  private URI filesystemRoot;

  private ZeppelinConfiguration conf;

  public HDFSNotebookRepo(ZeppelinConfiguration conf, URI filesystemRoot) throws IOException {
    this.conf = conf;
    this.filesystemRoot = filesystemRoot;
    if (filesystemRoot.getScheme().equalsIgnoreCase("hdfs")) {
      Configuration configuration = new Configuration();

      // add hadoop config, so that we can visit hdfs using ha
      String hadoopConfDir = conf.getString("HADOOP_CONF_DIR");
      if (hadoopConfDir != null && !hadoopConfDir.equals("")) {
        configuration.addResource(hadoopConfDir + "/core-site.xml");
        configuration.addResource(hadoopConfDir + "/hdfs-site.xml");
      }
      this.fs = FileSystem.get(filesystemRoot, configuration);
    } else {
      throw new IOException("unkonw uri scheme");
    }
  }

  @Override
  public List<NoteInfo> list() throws IOException {
    Path rootDir = getRootDir();

    FileStatus[] children = fs.listStatus(rootDir);

    List<NoteInfo> infos = new LinkedList<NoteInfo>();
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
      } catch (IOException e) {
        logger.error("Can't read note " + f.getPath().toString(), e);
      }
    }

    return infos;
  }

  private Note getNote(Path noteDir) throws IOException {
    if (!fs.isDirectory(noteDir)) {
      throw new IOException(noteDir.toString() + " is not a directory");
    }

    Path noteJson = new Path(noteDir.toUri() + Path.SEPARATOR + NOTE_FILENAME);
    if (!fs.exists(noteJson)) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();

    FSDataInputStream in = this.fs.open(noteJson);
    String json = IOUtils.toString(in, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    in.close();

    Note note = gson.fromJson(json, Note.class);
//    note.setReplLoader(replLoader);
//    note.jobListenerFactory = jobListenerFactory;

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
  public Note get(String noteId) throws IOException {
    Path rootDir = getRootDir();
    Path noteDir = new Path(rootDir.toUri() + Path.SEPARATOR + noteId);

    return getNote(noteDir);
  }

  private Path getRootDir() throws IOException {
    Path rootDir = new Path(filesystemRoot);

    if (!fs.exists(rootDir)) {
      throw new IOException("Root path does not exists");
    }

    if (!fs.isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }

  @Override
  public void save(Note note) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    String json = gson.toJson(note);

    Path rootDir = getRootDir();

    Path noteDir = new Path(rootDir.toUri() + Path.SEPARATOR + note.id());

    if (!fs.exists(noteDir)) {
      fs.mkdirs(noteDir);
    }
    if (!fs.isDirectory(noteDir)) {
      throw new IOException(noteDir.toString() + " is not a directory");
    }

    Path noteJson = new Path(noteDir.toUri() + Path.SEPARATOR + NOTE_FILENAME);
    // true means not appending, creates file if not exists
    FSDataOutputStream os = fs.create(noteJson, true);
    BufferedWriter br = new BufferedWriter(new OutputStreamWriter(os,
            ConfVars.ZEPPELIN_ENCODING.getStringValue()));
    br.write(json);
    br.close();
    os.close();
  }

  @Override
  public void remove(String noteId) throws IOException {
    Path rootDir = getRootDir();

    Path noteDir = new Path(rootDir.toUri() + Path.SEPARATOR + noteId);

    if (!fs.exists(noteDir)) {
      // nothing to do
      return;
    }

    if (!fs.isDirectory(noteDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + noteDir.toString());
    }

    // recursive delete
    fs.delete(noteDir, true);
  }

}
