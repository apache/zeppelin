package org.apache.zeppelin.notebook.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages mapping from notes => [ snapshotId: revisionID ]
 */
public class NotebookSnapshot {


  private static final Logger LOG = LoggerFactory.getLogger(NotebookSnapshot.class);
  private static volatile Map<String, List<Map<String, String>>> viewInfo;
  private static NotebookSnapshot instance;
  private static ZeppelinConfiguration conf;
  private static Gson gson;
  private static String filePath;

  public NotebookSnapshot() {
  }

  public static NotebookSnapshot init(ZeppelinConfiguration config) {
    if (instance == null) {
      instance = new NotebookSnapshot();
      conf = config;
      filePath = conf.getNotebookRevisionViewPath();
      LOG.info("Loading revisions from file path " + filePath);
      GsonBuilder builder = new GsonBuilder();
      builder.setPrettyPrinting();
      gson = builder.create();
      try {
        loadFromFile();
      } catch (IOException e) {
        LOG.error("Error loading NotebookAuthorization", e);
      }
    }
    return instance;
  }


  public static NotebookSnapshot getInstance() {
    if (instance == null) {
      LOG.warn("Notebook Revision View module was called without " +
        "initialization, initializing with default configuration");
      init(ZeppelinConfiguration.create());
    }
    return instance;
  }

  private static void loadFromFile() throws IOException {
    String json = FileHelper.loadContentFromFile(filePath);
    LOG.info("Read following json from file " + json);
    if (json == null) {
      viewInfo = new HashMap<>();
    } else {
      NotebookSnapshotInfo info = gson.fromJson(json, NotebookSnapshotInfo.class);

      LOG.info("Revision view info is null? => " + (info.viewInfo == null));
      viewInfo = info.viewInfo;
    }
  }


  private void saveToFile() {
    String jsonString;

    synchronized (viewInfo) {
      NotebookSnapshotInfo info = new NotebookSnapshotInfo();
      info.viewInfo = viewInfo;
      jsonString = gson.toJson(info);
    }

    FileHelper.writeToFile(filePath, jsonString);
  }


  public void setSnapshotId(String noteId, List<Map<String, String>> viewToRevisionMap) {
    viewInfo.put(noteId, viewToRevisionMap);

    saveToFile();
  }

  public String getRevisionId(String noteId, String snapshotId) {
    if (viewInfo != null && viewInfo.containsKey(noteId) || viewInfo.get(noteId) != null) {
      for (Map<String, String> map : viewInfo.get(noteId)) {
        if (map.containsKey("snapshotId") && map.get("snapshotId").equals(snapshotId)) {
          return map.get("revisionId");
        }
      }
    }
    return null;
  }

  public List<Map<String, String>> getSnapshotToRevisionIdMap(String noteID) {
    if (viewInfo != null && viewInfo.containsKey(noteID)) {
      return viewInfo.get(noteID);
    } else {
      return new ArrayList<>();
    }
  }

}
