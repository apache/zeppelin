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
   * { "spark": [ "user1", "user2" ] , "livy": [ "user1" ] }
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

  public Map<String, Set<String>> getInterpreterAuthorization(Map<String, Object> data) {
    return this.authInfo;
  }

  public Map<String, Set<String>> updateInterpreterAuthorization(Map<String, Object> data) {
    LOG.info("updateInterpreterAuthorization = {}", data.toString());
    InterpreterAuthorizationInfoSaving info = gson.fromJson(data.toString(),
      InterpreterAuthorizationInfoSaving.class);

    this.authInfo = info.authInfo;
    saveToFile();
    return this.authInfo;
  }

  public boolean isValidated(String user, String intp) {
    boolean accessable = false;
    for (String intpName: this.authInfo.keySet()) {
      if (intp.trim().equals(intpName)) {
        for (String userName : this.authInfo.get(intpName)) {
          if (user.trim().equals(userName)) {
            accessable = true;
            break;
          }
        }
      }
    }
    return accessable;
  }

}
