package org.apache.zeppelin.notebook.repo.torrent;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;


public class TorrentMessage {
  private static final Gson gson = new Gson();

  public TorrentOp op;
  public Map<String, Object> data = new HashMap<>();

  public TorrentMessage(TorrentOp op) {
    this.op = op;

  }

  public static TorrentMessage deserilize(String message) {
    return gson.fromJson(message, TorrentMessage.class);
  }

  public void put(String key, Object o) {
    data.put(key, o);
  }

  public Object get(String key) {
    return data.get(key);
  }

  public String serialize() {
    return gson.toJson(this);
  }

}
