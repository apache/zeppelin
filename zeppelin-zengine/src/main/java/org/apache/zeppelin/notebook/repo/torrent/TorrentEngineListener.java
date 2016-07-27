package org.apache.zeppelin.notebook.repo.torrent;


import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.alerts.StateChangedAlert;

public interface TorrentEngineListener {
  void started();

  void stopped();

  void downloadAdded(TorrentHandle handle);

  void downloadComplete(TorrentHandle handle);

  void metadatareceived(TorrentHandle handle);

  void torrentStateChanged(TorrentHandle handle, StateChangedAlert alert);
}
