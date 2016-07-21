package org.apache.zeppelin.interpreter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Contains authorization information for interpreters
 */
public class InterpreterAuthorization {
  private static final Logger LOG = LoggerFactory.getLogger(InterpreterAuthorization.class);

  /*
   * { "intp1": [ "user1", "user2" ] , "intp2": [ "user1" ] }
   */
  private Map<String, Set<String>> authInfo = new HashMap<>();
  private ZeppelinConfiguration conf;
  private Gson gson;
  private String filePath;

  public InterpreterAuthorization(ZeppelinConfiguration conf) {
    this.conf = conf;
    filePath = conf.getInterpreterAuthorizationPath();
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();
    try {
      loadFromFile();
    } catch (IOException e) {
      LOG.error("Error loading InterpreterAuthorization", e);
    }
  }

  private void loadFromFile() throws IOException {
    File settingFile = new File(filePath);
    LOG.info(settingFile.getAbsolutePath());
    if (!settingFile.exists()) {
      // nothing to read
      return;
    }
    FileInputStream fis = new FileInputStream(settingFile);
    InputStreamReader isr = new InputStreamReader(fis);
    BufferedReader bufferedReader = new BufferedReader(isr);
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      sb.append(line);
    }
    isr.close();
    fis.close();

    String json = sb.toString();
    InterpreterAuthorizationInfoSaving info = gson.fromJson(json,
      InterpreterAuthorizationInfoSaving.class);
    this.authInfo = info.authInfo;
  }

  private void saveToFile() {
    String jsonString;

    synchronized (authInfo) {
      InterpreterAuthorizationInfoSaving info = new InterpreterAuthorizationInfoSaving();
      info.authInfo = authInfo;
      jsonString = gson.toJson(info);
    }

    try {
      File settingFile = new File(filePath);
      if (!settingFile.exists()) {
        settingFile.createNewFile();
      }

      FileOutputStream fos = new FileOutputStream(settingFile, false);
      OutputStreamWriter out = new OutputStreamWriter(fos);
      out.append(jsonString);
      out.close();
      fos.close();
    } catch (IOException e) {
      LOG.error("Error saving notebook authorization file: " + e.getMessage());
    }
  }

  public void updateInterpreterAuthorization(Map<String, Object> data) {
    LOG.info("updateInterpreterAuthorization = {}", data.get("authInfo").toString());
    InterpreterAuthorizationInfoSaving info = gson.fromJson(data.get("authInfo").toString(),
      InterpreterAuthorizationInfoSaving.class);
    this.authInfo = info.authInfo;

    LOG.info("info = {}", info.authInfo);
    for (String key : info.authInfo.keySet()) {
      LOG.info("---> {}", key);
      //LOG.info("---> {}, {}", key, info.authInfo.get(key).toString());
    }
  }
/*
  public void updateInterpreterAuthorization(Map<String, Set<String>> entities) {
    for (String key : entities.keySet()) {
      LOG.info("key = {}", key );
      for (String v : entities.get(key)) {
        LOG.info("  value = {}", v );
      }
    }
  }
*/
/*
  private Set<String> validateUser(Set<String> users) {
    Set<String> returnUser = new HashSet<>();
    for (String user : users) {
      if (!user.trim().isEmpty()) {
        returnUser.add(user.trim());
      }
    }
    return returnUser;
  }


  public void setOwners(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet(entities));
      noteAuthInfo.put("readers", new LinkedHashSet());
      noteAuthInfo.put("writers", new LinkedHashSet());
    } else {
      noteAuthInfo.put("owners", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  public void setReaders(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet());
      noteAuthInfo.put("readers", new LinkedHashSet(entities));
      noteAuthInfo.put("writers", new LinkedHashSet());
    } else {
      noteAuthInfo.put("readers", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  public void setWriters(String noteId, Set<String> entities) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    entities = validateUser(entities);
    if (noteAuthInfo == null) {
      noteAuthInfo = new LinkedHashMap();
      noteAuthInfo.put("owners", new LinkedHashSet());
      noteAuthInfo.put("readers", new LinkedHashSet());
      noteAuthInfo.put("writers", new LinkedHashSet(entities));
    } else {
      noteAuthInfo.put("writers", new LinkedHashSet(entities));
    }
    authInfo.put(noteId, noteAuthInfo);
    saveToFile();
  }

  public Set<String> getOwners(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<String>();
    } else {
      entities = noteAuthInfo.get("owners");
      if (entities == null) {
        entities = new HashSet<String>();
      }
    }
    return entities;
  }

  public Set<String> getReaders(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<String>();
    } else {
      entities = noteAuthInfo.get("readers");
      if (entities == null) {
        entities = new HashSet<String>();
      }
    }
    return entities;
  }

  public Set<String> getWriters(String noteId) {
    Map<String, Set<String>> noteAuthInfo = authInfo.get(noteId);
    Set<String> entities = null;
    if (noteAuthInfo == null) {
      entities = new HashSet<String>();
    } else {
      entities = noteAuthInfo.get("writers");
      if (entities == null) {
        entities = new HashSet<String>();
      }
    }
    return entities;
  }

  public boolean isOwner(String noteId, Set<String> entities) {
    return isMember(entities, getOwners(noteId));
  }

  public boolean isWriter(String noteId, Set<String> entities) {
    return isMember(entities, getWriters(noteId)) || isMember(entities, getOwners(noteId));
  }

  public boolean isReader(String noteId, Set<String> entities) {
    return isMember(entities, getReaders(noteId)) ||
      isMember(entities, getOwners(noteId)) ||
      isMember(entities, getWriters(noteId));
  }
*/

  // return true if b is empty or if (a intersection b) is non-empty
  private boolean isMember(Set<String> a, Set<String> b) {
    Set<String> intersection = new HashSet<String>(b);
    intersection.retainAll(a);
    return (b.isEmpty() || (intersection.size() > 0));
  }

  public void removeNote(String noteId) {
    authInfo.remove(noteId);
    saveToFile();
  }


}
