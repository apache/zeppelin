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

import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Downloader;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.ErrorCode;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.UdpEndpoint;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.ExternalIpAlert;
import com.frostwire.jlibtorrent.alerts.ListenFailedAlert;
import com.frostwire.jlibtorrent.alerts.ListenSucceededAlert;
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataFailedAlert;
import com.frostwire.jlibtorrent.alerts.StateChangedAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.add_torrent_params;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.torrent_handle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.frostwire.jlibtorrent.alerts.AlertType.DHT_STATS;
import static com.frostwire.jlibtorrent.alerts.AlertType.EXTERNAL_IP;
import static com.frostwire.jlibtorrent.alerts.AlertType.LISTEN_FAILED;
import static com.frostwire.jlibtorrent.alerts.AlertType.LISTEN_SUCCEEDED;
import static com.frostwire.jlibtorrent.alerts.AlertType.METADATA_RECEIVED;
import static com.frostwire.jlibtorrent.alerts.AlertType.PIECE_FINISHED;
import static com.frostwire.jlibtorrent.alerts.AlertType.PORTMAP;
import static com.frostwire.jlibtorrent.alerts.AlertType.PORTMAP_ERROR;
import static com.frostwire.jlibtorrent.alerts.AlertType.SAVE_RESUME_DATA;
import static com.frostwire.jlibtorrent.alerts.AlertType.SAVE_RESUME_DATA_FAILED;
import static com.frostwire.jlibtorrent.alerts.AlertType.STATE_CHANGED;
import static com.frostwire.jlibtorrent.alerts.AlertType.STORAGE_MOVED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_ADDED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_CHECKED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_FINISHED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_PAUSED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_RESUMED;


/**
 * TorrentEngine Singleton
 */
public enum TorrentEngine {
  Instance;

  private static final int[] INNER_LISTENER_TYPES = new int[]{
          TORRENT_ADDED.swig(),
          PIECE_FINISHED.swig(),
          PORTMAP.swig(),
          PORTMAP_ERROR.swig(),
          DHT_STATS.swig(),
          LISTEN_SUCCEEDED.swig(),
          LISTEN_FAILED.swig(),
          EXTERNAL_IP.swig(),
          METADATA_RECEIVED.swig(),
          SAVE_RESUME_DATA.swig(),
          SAVE_RESUME_DATA_FAILED.swig(),
          STATE_CHANGED.swig(),
          TORRENT_PAUSED.swig(),
          TORRENT_RESUMED.swig(),
          TORRENT_CHECKED.swig(),
          TORRENT_FINISHED.swig(),
          STORAGE_MOVED.swig(),
  };
  private static final Logger log = LoggerFactory.getLogger(TorrentEngine.class);
  private InnerListener alertlistener;
  private TorrentEngineListener listener;
  private int totalDHTNodes;
  private Session session;
  private Downloader downloader;
  private boolean firewalled;
  private List<UdpEndpoint> udpEndpoints;
  private List<TcpEndpoint> tcpEndpoints;
  private Address externalAddress;

  TorrentEngine() {
    this.alertlistener = new InnerListener();
    udpEndpoints = new LinkedList<>();
    tcpEndpoints = new LinkedList<>();
    firewalled = false;
  }

  public void start() {
    alertlistener = new InnerListener();
    session = new Session("0.0.0.0", 5, false, alertlistener);
    downloader = new Downloader(session);
    loadSettings();

  }

  public void stop() {
    if (session == null) return;
    session.removeListener(alertlistener);
    saveSettings();
    downloader = null;
    session.abort();
    session = null;
  }

  public void restart() {
    try {

      stop();
      Thread.sleep(1000); // allow some time to release native resources
      start();

    } catch (InterruptedException e) {

    }
  }

  public void pause() {
    if (session != null && !session.isPaused()) {
      session.pause();
    }
  }

  public void resume() {
    if (session != null) {
      session.resume();
    }
  }

  private void loadSettings() {
    if (session == null) {
      return;
    }
    try {
      File f = BittorrentNotebookRepo.settingsFile;
      if (f.exists()) {
        byte[] data = FileUtils.readFileToByteArray(f);
        session.loadState(data);
      } else {
        addDefaultSettings();
      }
    } catch (IOException e) {
      e.printStackTrace();
      log.error("Error loading session state", e);
    }
  }

  public void setChecking(int n) {
    SettingsPack settingsPack = session.getSettingsPack();
    settingsPack.activeChecking(4);
    session.applySettings(settingsPack);
  }

  public void saveSettings() {
    if (session == null) {
      return;
    }
    try {
      File f = BittorrentNotebookRepo.settingsFile;
      byte[] data = session.saveState();
      FileUtils.writeByteArrayToFile(f, data);
    } catch (IOException e) {
      e.printStackTrace();
      log.error("Error saving session state", e);
    }
  }

  public void addDefaultSettings() {
    SettingsPack settingsPack = session.getSettingsPack();

    settingsPack.setBoolean(settings_pack.bool_types.announce_double_nat.swigValue(), true);
    settingsPack.setInteger(settings_pack.int_types.min_announce_interval.swigValue(), 2 * 60);
    settingsPack.activeSeeds(10);
    settingsPack.activeDownloads(5);
    session.applySettings(settingsPack);

    saveSettings();
  }

  public void download(String magnetLink) {
    /*if (session == null) return;

    byte[] bytes = downloader.fetchMagnet(magnetLink, 2000);
    TorrentInfo info = TorrentInfo.bdecode(bytes);*/

    if (session == null) {
      return;
    }

    add_torrent_params p = add_torrent_params.create_instance();
    error_code ec = new error_code();
    libtorrent.parse_magnet_uri(magnetLink, p, ec);
    p.setUrl(magnetLink);

    if (ec.value() != 0) {
      throw new IllegalArgumentException(ec.message());
    }

    final sha1_hash info_hash = p.getInfo_hash();
    String sha1 = info_hash.to_hex();

    boolean add;
    torrent_handle th;

    th = session.swig().find_torrent(info_hash);
    if (th != null && th.is_valid()) {
      // we have a download with the same info-hash
      add = false;
      log.info("Torrent already present");
    } else {
      add = true;
    }

    if (add) {
      if (p.getName() == null) {
        p.setName("fetch_magnet:" + magnetLink);
      }
      p.setSave_path(BittorrentNotebookRepo.noteDownloadDir.getAbsolutePath() + "/" + sha1);

      long flags = p.get_flags();
      flags &= ~add_torrent_params.flags_t.flag_auto_managed.swigValue();
      p.set_flags(flags);

      ec.clear();
      th = session.swig().add_torrent(p, ec);
      th.resume();
    }
  }


  public void addTorrent(File torrentFile, File outputDir) {
    if (session == null)
      return;

    try {
      TorrentInfo ti = new TorrentInfo(torrentFile);
      TorrentHandle th = downloader.find(ti.infoHash());
      boolean exists = th != null;
      if (exists) {
        log.debug("Torrent already added");
        return;
      }
      AddTorrentParams params = new AddTorrentParams();
      params.swig().set_ti(ti.swig());
      String s = params.savePath();
      //System.out.println("save path before  = " + s);
      params.savePath(outputDir.getAbsolutePath());
      long flags = params.flags();
      flags &= add_torrent_params.flags_t.flag_seed_mode.swigValue();
      params.flags(flags);

      ErrorCode ec = new ErrorCode();
      th = session.addTorrent(params, ec);
      th.resume();

    } catch (Exception ex) {
      log.warn(ExceptionUtils.getStackTrace(ex));
    }
  }

  public void addTorrent(File torrentFile, File outputDir, File resumeFile) {
    if (session == null)
      return;
    try {
      session.addTorrent(torrentFile, outputDir, resumeFile);
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getStackTrace(ex));
    }
  }

  private TorrentInfo createTorrentInfo(File inputFile) throws IOException {
    File sourceFile = inputFile;
    file_storage fs = new file_storage();
    libtorrent.add_files(fs, sourceFile.getAbsolutePath());
    create_torrent t = new create_torrent(fs);
    t.set_priv(false);
    t.set_creator(System.getProperty("user.name"));

    error_code ec = new error_code();
    libtorrent.set_piece_hashes(t, sourceFile.getParent(), ec);

    if (ec.value() != 0) {
      throw new IOException("Error creating torrent");
    }

    Entry entry = new Entry(t.generate());


    Map<String, Entry> entryMap = entry.dictionary();
    Entry entryFromUpdatedMap = Entry.fromMap(entryMap);
    final byte[] bencode = entryFromUpdatedMap.bencode();

    TorrentInfo info = TorrentInfo.bdecode(bencode);
    return info;
  }

  public String ShareFile(File inputFile) throws IOException {
    if (!inputFile.exists())
      throw new IOException();

    TorrentInfo torrentInfo = createTorrentInfo(inputFile);
    Sha1Hash sha1Hash = torrentInfo.infoHash();
    File torrentSaveDirectory = getTorrentSaveDirectory(sha1Hash);
    FileUtils.copyFileToDirectory(inputFile, torrentSaveDirectory);
    String name = inputFile.getName();
    File sourceFile = new File(torrentSaveDirectory.getAbsolutePath() + "/" + name);

    TorrentInfo newInfo = createTorrentInfo(sourceFile);
    String magnetLink = newInfo.makeMagnetUri();
    File torrentFile = saveTorrent(newInfo);
    addTorrent(torrentFile, torrentSaveDirectory);
    log.info("magnetLink = " + magnetLink);
    return magnetLink;
  }

  private File getTorrentSaveDirectory(Sha1Hash sha1Hash) {
    File dir = new File(BittorrentNotebookRepo.noteDownloadDir + "/" + sha1Hash.toHex());
    if (!dir.exists())
      dir.mkdir();
    return dir;
  }

  /*public  getFilesInTorrent(File torrentFile){

    TorrentInfo torrentInfo = new TorrentInfo(torrentFile);
    FileStorage files = torrentInfo.files();
    for (int i = 0; i < files.numFiles(); i++) {
      long l = files.fileSize(i);
      files.
    }

  }*/

  public List<String> getFilesInTorrent(TorrentInfo info) {
    List<String> fileNames = new ArrayList<>();
    FileStorage files = info.files();
    for (int i = 0; i < info.numFiles(); i++) {
      fileNames.add(files.fileName(i));
    }
    return fileNames;
  }


  public void addTorrentOnlyWithResume(File resumeFile) {
    if (session == null)
      return;
    AddTorrentParams params = AddTorrentParams.createInstance();
    try {
      byte[] bytes = FileUtils.readFileToByteArray(resumeFile);
      params.resumeData(bytes);
      error_code ec = new error_code();

      if (ec.value() != 0) {
        throw new IllegalArgumentException(ec.message());
      }

      Sha1Hash sha1Hash = params.infoHash();
      String sha1 = sha1Hash.toHex();
      params.savePath(BittorrentNotebookRepo.noteDownloadDir.getAbsolutePath());
      ec.clear();
      torrent_handle th = session.swig().add_torrent(params.swig(), ec);
      th.resume();
      //System.out.println(th.status().getSave_path());
    } catch (IOException e) {
      e.printStackTrace();
    }

  }


  /*public void addTorrent(File outputDir, File torrentFile, boolean[] selection) {

    if (outputDir.exists() || torrentFile.exists())
      return;

    TorrentInfo ti = new TorrentInfo(torrentFile);
    Priority[] priorities = null;

    TorrentHandle th = downloader.find(ti.infoHash());
    boolean exists = th != null;

    if (selection != null) {
      if (th != null) {
        priorities = th.getFilePriorities();
      } else {
        priorities = Priority.array(Priority.IGNORE, ti.numFiles());
      }

      for (int i = 0; i < selection.length; i++) {
        if (selection[i]) {
          priorities[i] = Priority.NORMAL;
        }
      }
    }

    session.addTorrent(ti, outputDir, priorities, null);

    *//*if (!exists) {
      saveResumeTorrent(torrent);
    }*//*
  }*/


  private File saveTorrent(TorrentInfo ti) {
    File torrentFile;

    try {
      String displayName = ti.name();
      String name = ti.infoHash().toString();

      torrentFile = new File(BittorrentNotebookRepo.torrentDir, name + ".torrent");
      byte[] arr = ti.toEntry().bencode();

      FileUtils.writeByteArrayToFile(torrentFile, arr);
      log.info("torrent file saved for hash" + name);

    } catch (Throwable e) {
      torrentFile = null;
      log.warn("Error saving torrent info to file", e);
    }

    return torrentFile;
  }


  private void doResumeData(TorrentAlert<?> alert, boolean force) {

    TorrentHandle th = session.findTorrent(alert.handle().getInfoHash());
    if (th != null && th.isValid() && th.getStatus().hasMetadata()) {
      th.saveResumeData();
      log.info("Resume data generated for torrent " + th.getName());
    }

  }

  private void onExternalIpAlert(ExternalIpAlert alert) {
    String address = alert.getExternalAddress().toString();
    externalAddress = new Address(address);
    log.info("External IP: " + externalAddress);
  }

  private void onListenSucceeded(ListenSucceededAlert alert) {
    TcpEndpoint endpoint = alert.getEndpoint();
    if (alert.getSocketType() == ListenSucceededAlert.SocketType.TCP) {
      String address = endpoint.address().toString();
      int port = endpoint.port();
      tcpEndpoints.add(new TcpEndpoint(address, port));
    } else if (alert.getSocketType() == ListenSucceededAlert.SocketType.UDP) {
      String address = endpoint.address().toString();
      int port = endpoint.port();
      udpEndpoints.add(new UdpEndpoint(address, port));
    }
    String s = "endpoint: " + endpoint + " type:" + alert.getSocketType();
    log.info("Listen succeeded on " + s);
  }

  private void onListenFailed(ListenFailedAlert alert) {
    TcpEndpoint endp = alert.endpoint();
    String s = "endpoint: " + endp + " type:" + alert.getSocketType();
    String message = alert.getError().message();
    log.info("Listen failed on " + s + " (error: " + message + ")");
  }

  private void saveMagnetData(MetadataReceivedAlert alert) {
    TorrentHandle handle = alert.handle();
    saveTorrent(handle.getTorrentInfo());
  }

  private void saveResumedata(SaveResumeDataAlert alert) {
    Entry entry = alert.resumeData();
    byte[] bencode = entry.bencode();

    Sha1Hash infoHash = alert.handle().getInfoHash();
    File saveFile = new File(BittorrentNotebookRepo.resumeDataDir + "/" + infoHash.toString()
        + ".dat");
    try {
      FileUtils.writeByteArrayToFile(saveFile, bencode);
      log.info("resume data saved for " + infoHash.toHex());
    } catch (IOException e) {
      e.printStackTrace();
      log.warn("Could not save resume data");
    }
  }

  private void fireDownloadComplete(TorrentAlert<?> alert) {
    TorrentHandle handle = alert.handle();
    if (listener != null) {
      listener.downloadComplete(handle);
    }
  }

  private void fireDownloadAdded(TorrentAlert<?> torrentAlert) {
    TorrentHandle handle = torrentAlert.handle();
    if (listener != null) {
      listener.downloadAdded(handle);
    }
  }

  private void fireMetadataReceived(MetadataReceivedAlert alert) {
    TorrentHandle handle = alert.handle();
    if (listener != null) {
      listener.metadatareceived(handle);
    }
  }

  private void fireStateChanged(StateChangedAlert stateChangedAlert) {
    TorrentHandle handle = stateChangedAlert.handle();
    if (listener != null) {
      listener.torrentStateChanged(handle, stateChangedAlert);
    }
  }

  public void setListener(TorrentEngineListener listener) {
    if (listener != null)
      this.listener = listener;
  }

  private class InnerListener implements AlertListener {
    public int[] types() {
      return INNER_LISTENER_TYPES;
    }

    public void alert(Alert<?> alert) {
      AlertType type = alert.type();

      switch (type) {
          case TORRENT_ADDED:
            TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;
            log.info("torrent added alerttt  " + torrentAlert.torrentName());
            //torrentAlert.handle().setAutoManaged(true);
            boolean autoManaged = torrentAlert.handle().getStatus().isAutoManaged();
            log.info("torrent auto managed ? " + autoManaged);
            fireDownloadAdded(torrentAlert);
            //runNextRestoreDownloadTask();
            break;
          case TORRENT_FINISHED:
            log.info("Torrent finished ");
            fireDownloadComplete((TorrentAlert<?>) alert);
            break;
          case TORRENT_RESUMED:
            log.info("Torrent resumed");
            break;
          case PIECE_FINISHED:
            doResumeData((TorrentAlert<?>) alert, true);
            break;
          case SAVE_RESUME_DATA:
            saveResumedata((SaveResumeDataAlert) alert);
            break;
          case STATE_CHANGED:
            StateChangedAlert stateChangedAlert = (StateChangedAlert) alert;
            log.info("State change: " + stateChangedAlert.getPrevState() + " -> "
                  + stateChangedAlert.getState());
            fireStateChanged(stateChangedAlert);
            break;
          case SAVE_RESUME_DATA_FAILED:
            SaveResumeDataFailedAlert resumeDataFailed = (SaveResumeDataFailedAlert) alert;
            log.error("resumeDataFailed = " + resumeDataFailed.getError() +
                  "\n" + resumeDataFailed.message());
            break;
          case PORTMAP:
            firewalled = false;
            break;
          case PORTMAP_ERROR:
            firewalled = true;
            break;
          case DHT_STATS:
            totalDHTNodes = (int) session.getStats().dhtNodes();
            break;
          case LISTEN_SUCCEEDED:
            onListenSucceeded((ListenSucceededAlert) alert);
            break;
          case LISTEN_FAILED:
            onListenFailed((ListenFailedAlert) alert);
            break;
          case EXTERNAL_IP:
            onExternalIpAlert((ExternalIpAlert) alert);
            break;
          case METADATA_RECEIVED:
            saveMagnetData((MetadataReceivedAlert) alert);
            fireMetadataReceived((MetadataReceivedAlert) alert);
            break;
          case TORRENT_PAUSED:
            log.info("Torrent pause");
            break;
          case STORAGE_MOVED:
            log.info("Storage moved");
            break;
          case TORRENT_CHECKED:
            log.info("Torrent checked");
            break;
      }
    }
  }

}
