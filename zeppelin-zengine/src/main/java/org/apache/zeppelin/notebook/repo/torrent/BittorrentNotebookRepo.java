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

package org.apache.zeppelin.notebook.repo.torrent;

import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.alerts.StateChangedAlert;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * TO download note from magnet link and share a note
 */
public class BittorrentNotebookRepo extends VFSNotebookRepo implements TorrentEngineListener {
  private static final Logger LOG = LoggerFactory.getLogger(BittorrentNotebookRepo.class);
  static File torrentHomeDir;
  static File torrentDir;
  static File noteDownloadDir;
  static File resumeDataDir;
  static File settingsFile;
  private List<TorrentSocket> connections = new CopyOnWriteArrayList<>();
  private TorrentEngine engine;

  public BittorrentNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);
    torrentHomeDir = new File(getRootDir().getName().getPath());
    torrentDir = new File(torrentHomeDir.getAbsolutePath() + "/" + "torrents");
    noteDownloadDir = new File(torrentHomeDir.getAbsolutePath() + "/" + "notes");
    resumeDataDir = new File(torrentHomeDir.getAbsolutePath() + "/" + "resume");
    settingsFile = new File(torrentHomeDir.getAbsolutePath() + "settings.dat");

    loadLibrary();
    setupDir();
    engine = TorrentEngine.Instance;
    engine.start();
    engine.setListener(this);
  }

  private static void loadLibrary() throws IOException {
    String path = LIBTORRENT_OS_LIBRARY_PATH();
    if (path == null) {
      LOG.error("Unknown Operating system");
      throw new IOException();
    }
    InputStream is = BittorrentNotebookRepo.class.getResourceAsStream(path);
    File temp = File.createTempFile("libjlibtorrent", ".so");
    OutputStream os = new FileOutputStream(temp);
    if (is == null) {
      throw new FileNotFoundException("File " + path + " was not found inside JAR.");
    }
    int readBytes;
    byte[] buffer = new byte[1024];
    try {
      while ((readBytes = is.read(buffer)) != -1) {
        os.write(buffer, 0, readBytes);
      }
    } finally {
      os.close();
      is.close();
    }
    //System.load(temp.getAbsolutePath());
    System.setProperty("jlibtorrent.jni.path", temp.getAbsolutePath());
  }

  private static String LIBTORRENT_OS_LIBRARY_PATH() {
    String osName = System.getProperty("os.name").toLowerCase();
    String jvmBits = System.getProperty("sun.arch.data.model");
    LOG.info("Operating system: " + osName + ", JVM bits: " + jvmBits);

    String ret = null;
    if (osName.contains("linux")) {
      if (jvmBits.equals("32")) {
        ret = "/lib/x86/libjlibtorrent.so";
      } else {
        ret = "/lib/x86_64/libjlibtorrent.so";
      }
    } else if (osName.contains("windows")) {
      if (jvmBits.equals("32")) {
        ret = "/lib/x86/jlibtorrent.dll";
      } else {
        ret = "/lib/x86_64/jlibtorrent.dll";
      }
    } else if (osName.contains("mac")) {
      ret = "/lib/x86_64/libjlibtorrent.dylib";
    }
    LOG.info("Using libtorrent @ " + ret);
    return ret;
  }

  public void addConnection(TorrentSocket torrentSocket) {
    connections.add(torrentSocket);
  }

  public void removeConnection(TorrentSocket torrentSocket) {
    connections.remove(torrentSocket);
  }

  public void handleMessage(String message) {
    try {
      TorrentMessage torrentMessage = TorrentMessage.deserilize(message);

      switch (torrentMessage.op) {
          case ADDED_TO_DOWNLOAD:
            break;
          case DOWNLOAD_COMPLETE:
            break;
          case LIST_DOWNLOAD:
            break;
          case DOWNLOAD:
            downloadNote(torrentMessage);
            break;
          case METADATA_RECEIVED:
            break;
          case STATE_CHANGED:
            break;
          case SHARE_NOTE:
            shareNote(torrentMessage);
            break;
      }

    } catch (Exception ex) {
      LOG.error("Can't handle message" + message);
    }
  }

  private void setupDir() {
    if (!torrentHomeDir.exists())
      torrentHomeDir.mkdir();
    if (!torrentDir.exists())
      torrentDir.mkdir();
    if (!noteDownloadDir.exists())
      noteDownloadDir.mkdir();
    if (!resumeDataDir.exists())
      resumeDataDir.mkdir();
  }

  private void downloadNote(TorrentMessage torrentMessage) {
    String magnetLink = (String) torrentMessage.get("magnet");
    if (magnetLink != null) {
      try {
        engine.download(magnetLink);
      } catch (RuntimeException ex) {
        LOG.error("Failed to download" + magnetLink);
      }
    }
  }

  private void shareNote(TorrentMessage torrentMessage) throws IOException {
    String noteId = (String) torrentMessage.get("noteid");
    if (noteId != null) {
      shareNote(noteId);
    }
  }

  public String shareNote(String noteId) throws IOException {
    FileObject rootDir = getRootDir();
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);
    if (!(noteDir != null && noteDir.getType() == FileType.FOLDER)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile("note.json", NameScope.CHILD);
    if (!noteJson.exists()) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }
    String magnetLink = engine.ShareFile(new File(noteJson.getName().getPath()));
    return magnetLink;
  }

  @Override
  public void close() {
    super.close();
    engine.stop();
  }

  @Override
  public void started() {

  }

  @Override
  public void stopped() {

  }

  @Override
  public void downloadAdded(TorrentHandle handle) {
    String infoHash = handle.getInfoHash().toHex();
    TorrentMessage message = new TorrentMessage(TorrentOp.ADDED_TO_DOWNLOAD);
    message.put("hash", infoHash);
    broadcast(message.serialize());
  }

  @Override
  public void downloadComplete(TorrentHandle handle) {
    String infoHash = handle.getInfoHash().toHex();
    TorrentMessage message = new TorrentMessage(TorrentOp.DOWNLOAD_COMPLETE);
    message.put("hash", infoHash);
    broadcast(message.serialize());
  }

  @Override
  public void metadatareceived(TorrentHandle handle) {
    String infoHash = handle.getInfoHash().toHex();
    TorrentMessage message = new TorrentMessage(TorrentOp.METADATA_RECEIVED);
    message.put("hash", infoHash);
    List<String> filesInTorrent = engine.getFilesInTorrent(handle.getTorrentInfo());
    message.put("files", filesInTorrent);
    broadcast(message.serialize());
  }

  @Override
  public void torrentStateChanged(TorrentHandle handle, StateChangedAlert alert) {
    String infoHash = handle.getInfoHash().toHex();
    TorrentMessage message = new TorrentMessage(TorrentOp.STATE_CHANGED);
    message.put("hash", infoHash);
    message.put("previous", alert.getPrevState());
    message.put("current", alert.getState());
    broadcast(message.serialize());
  }

  private void broadcast(String message) {
    for (TorrentSocket connection : connections) {
      connection.sendMessage(message);
    }
  }
}
