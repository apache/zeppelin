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

package org.apache.zeppelin.notebook.repo.ipfs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Share and get versioned notebooks with IPFS. Make sure IPFS daemon is running.
 */
public class IPFSNotebookRepo extends VFSNotebookRepo implements NotebookRepo {
  private static final String API_SERVER_PROPERTY_NAME = "zeppelin.notebook.ipfs.apiServer";
  private static final String DEFAULT_API_SERVER = "http://localhost:5001/api/v0/";
  private static final Logger LOG = LoggerFactory.getLogger(IPFSNotebookRepo.class);
  private ExecutorService executorService = Executors.newCachedThreadPool();
  private Gson gson;
  private Ipfs ipfs;
  private FileObject ipfsNoteHashesJson;
  private String encoding;
  /*
   *  map of <notedID, List<Base58 hash-string> > i.e Revision History
   *  { 2A94M5J1Z  : [ QmZQybbanVowHLynssnMjzPJcZ676yB1dA2CpzLN4ZTY48, QmVc.... ],
   *    2BKZQ2FP6  : [ QmNhPUwuUQ1uD1n22h2CEBFLKPCExCiVc7rcgHmMftmzsv ]
   *  }
   */
  private Map<String, List<Revision>> noteHashes;

  public IPFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);
    encoding = conf.getString(ConfVars.ZEPPELIN_ENCODING);
    gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Date.class, new
        NotebookImportDeserializer()).create();
    String ipfsApiServer = conf.getString("IPFS_API_SERVER",
        API_SERVER_PROPERTY_NAME, DEFAULT_API_SERVER);
    ipfs = new Ipfs(ipfsApiServer);
    String versionJson = ipfs.version();
    if (versionJson == null) {
      throw new IOException("Make sure the ipfs daemon is running on given url" + ipfsApiServer);
    }
    Type simpleType = new TypeToken<Map<String, String>>() {
    }.getType();
    Map<String, String> data = gson.fromJson(versionJson, simpleType);
    String version = data.get("Version");
    if (!version.equals("0.4.2")) {
      throw new IOException("Current api is not supported for " + version + " only for 0.4.2");
    }

    // creates a ipfsnotehashes.json file in notebook dir if not exists
    init();
    // initialize noteHashes Map to load noteID and multihash  from file
    noteHashes = loadFromFile();
  }

  /**
   * creates a ipfsnotehashes.json file in notebook directory. This file will represent the
   * noteHashes Map in Json format.
   */
  private void init() throws IOException {
    FileObject file = getRootDir();
    ipfsNoteHashesJson = file.resolveFile("ipfsnotehashes.json", NameScope.CHILD);
    if (!ipfsNoteHashesJson.exists()) {
      ipfsNoteHashesJson.createFile();
    }
  }

  /**
   * Reads the ipfsNoteHashesJson file and converts the Json String to Map
   */
  private Map<String, List<Revision>> loadFromFile() throws IOException {
    FileContent content = ipfsNoteHashesJson.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, encoding);
    ins.close();
    if (json.isEmpty() || json == null) {
      return new HashMap<>();
    }
    Type type = new TypeToken<Map<String, List<Revision>>>() {
    }.getType();
    Map<String, List<Revision>> map = gson.fromJson(json, type);
    return map;
  }

  /**
   * Get's the note from peers.
   *
   * @return Note
   */
  @Override
  public Note getNoteFromUrl(final String hash, AuthenticationInfo subject) throws IOException {
    //getNote is blocking hence using a timeout
    Callable<Note> task = new Callable<Note>() {
      @Override
      public Note call() throws Exception {
        return getNote(hash);
      }
    };
    Future<Note> noteFuture = executorService.submit(task);
    Note note = null;
    try {
      note = noteFuture.get(15, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.error("Failed to download Note, Interrupted", e);
    } catch (ExecutionException e) {
      LOG.error("Failed to get note", e);
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException) cause;
      }
    } catch (TimeoutException e) {
      LOG.error("TimeOut reached", e);
    } finally {
      noteFuture.cancel(true);
    }
    return note;
  }

  /**
   * The call to ipfs.cat() can be blocking. If the file corresponding to hash is not present in
   * local ipfs storage it will try to get from peers which can be time consuming resulting in
   * SocketTimeout Exception
   */
  private Note getNote(String noteMultihash) throws IOException {
    String noteJson = ipfs.cat(noteMultihash);
    if (noteJson == null) {
      throw new IOException("Failed to retrieve Note with hash " + noteMultihash);
    }

    Note note = gson.fromJson(noteJson, Note.class);

    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Job.Status.PENDING || p.getStatus() == Job.Status.RUNNING) {
        p.setStatus(Job.Status.ABORT);
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

  /* This method writes the revision history - noteHashes to ipfsNoteHashesJson file*/
  private synchronized void saveToFile() throws IOException {
    String jsonString = gson.toJson(noteHashes);
    OutputStream out = ipfsNoteHashesJson.getContent().getOutputStream(false);
    out.write(jsonString.getBytes(encoding));
    out.close();
  }

  /**
   * remove method deletes the notebook from directory and also removes all its revisions from ipfs
   * local storage. It also removes the noteID entry from noteHashesJSon file.
   */
  @Override
  public synchronized void remove(String noteId, AuthenticationInfo subject) throws IOException {
    super.remove(noteId, subject);
    List<Revision> revisions = noteHashes.get(noteId);
    if (revisions != null) {
      String pinJsonResponse = ipfs.pinLs();
      if (pinJsonResponse == null) {
        throw new IOException("Failed to retrieve pinned hashes");
      }
      Type pinType = new TypeToken<Map<String, Object>>() {
      }.getType();
      Map<String, Object> data = gson.fromJson(pinJsonResponse, pinType);
      Map<String, Object> pinnedObjects = (Map<String, Object>) data.get("Keys");

      for (Revision rev : revisions) {
        String revisionHash = rev.id;
        if (pinnedObjects.containsKey(revisionHash)) {
          boolean success = ipfs.pinRm(revisionHash);
          if (!success) {
            LOG.warn("Failed to remove " + revisionHash);
          }
        }
      }
      noteHashes.remove(noteId);
    }
    saveToFile();
  }

  /**
   * This method removes only a corresponding revision for a note
   */
  public void removeRevision(String noteID, Revision revision) throws IOException {
    List<Revision> noteRevisions = noteHashes.get(noteID);
    if (noteRevisions == null) {
      LOG.error("This note " + noteID + "does not have any revisions");
      throw new IOException("This note " + noteID + "does not have any revisions");
    }
    if (!noteRevisions.contains(revision)) {
      LOG.error("invalid revision " + revision + " for note " + noteID);
      throw new IOException("invalid revision " + revision + " for note " + noteID);
    }
    boolean success = ipfs.pinRm(revision.id);
    if (success) {
      noteRevisions.remove(revision);
    } else {
      LOG.warn("Failed to remove revision " + revision);
    }
    saveToFile();
  }

  /*
   * Notes are stored in local directory and are added to ipfs only when
   * committed. If there is no change in note same hash will be generated
   * and it will not be added to the file.
   */
  @Override
  public Revision checkpoint(String noteId, String commitMessage, AuthenticationInfo subject)
      throws IOException {
    Note note = get(noteId, subject);
    String json = gson.toJson(note);
    String addResult = ipfs.add(json);
    if (addResult == null) {
      throw new IOException("Failed to checkpoint note with ID " + noteId);
    }
    Type type = new TypeToken<Map<String, String>>() {
    }.getType();
    Map<String, String> data = gson.fromJson(addResult, type);
    String hash = data.get("Hash");

    /*
     * The `time` param in Rev is in int(unix timestamp) seconds
     * System.currentTimeMillis() returns long milliseconds
     */
    int time = (int) (System.currentTimeMillis() / 1000L);
    Revision revision = new Revision(hash, commitMessage, time);
    List<Revision> noteVersions = noteHashes.get(noteId);
    if (noteVersions == null) {
      noteVersions = new ArrayList<>();
      noteVersions.add(revision);
      noteHashes.put(noteId, noteVersions);
    } else {
      if (!noteVersions.contains(revision)) {
        noteVersions.add(revision);
      }
      /*
       * revision already exists before. Should i change time ?
       *
       * */
    }
    saveToFile();
    LOG.info("Checkpoint for Note " + noteId + "  IpfsRevision is " + revision.id);
    return revision;
  }


  public Map<String, List<Revision>> getNoteHashes() {
    return new HashMap<>(noteHashes);
  }

  /**
   * get a particular revision from ipfs
   */
  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    Note note = null;
    String pinJsonResponse = ipfs.pinLs();
    Type pinType = new TypeToken<Map<String, Object>>() {
    }.getType();
    Map<String, Object> data = gson.fromJson(pinJsonResponse, pinType);
    Map<String, Object> allPinnedObjects = (Map<String, Object>) data.get("Keys");

    if (allPinnedObjects.containsKey(revId)) {
      note = getNote(revId);
    }
    if (note == null) {
      LOG.warn("revision " + revId + " not present for note " +
          noteId + " in local ipfs storage");
    }
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    List<Revision> versionHistory = noteHashes.get(noteId);
    return versionHistory;
  }

  @Override
  public void close() {
    super.close();
    try {
      ipfs.getClient().close();
    } catch (IOException e) {
      LOG.info("Couldn't successfully close the ipfsClient", e);
    }
    executorService.shutdown();
  }
}

