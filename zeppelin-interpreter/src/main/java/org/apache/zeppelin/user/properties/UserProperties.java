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

package org.apache.zeppelin.user.properties;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Class defining user specific properties
 */

public class UserProperties {
  private static final Logger LOG = LoggerFactory.getLogger(UserProperties.class);

  private Map<String, Map<String, String>> propertiesMap;
  private Gson gson;
  private Boolean persist = true;
  File propertiesFile;

  public UserProperties(Boolean persist, String propertiesFilePath) {
    this.persist = persist;
    if (propertiesFilePath != null) {
      propertiesFile = new File(propertiesFilePath);
    }
    propertiesMap = new ConcurrentHashMap<>();

    if (persist) {
      GsonBuilder builder = new GsonBuilder();
      builder.setPrettyPrinting();
      gson = builder.create();
      loadFromFile();
    }
  }

  public Map<String, String> get(String userName) {
    Map<String, String> properties = propertiesMap.get(userName);
    if (properties == null) {
      properties = new HashMap<>();
      propertiesMap.put(userName, properties);
    }
    return properties;
  }

  public void put(String userName, Map<String, String> properties)
      throws IOException {
    propertiesMap.put(userName, properties);
    saveProperties();
  }

  public void put(String userName, String name, String value)
      throws IOException {
    Map<String, String> properties = get(userName);
    properties.put(name, value);
    saveProperties();
  }

  public Map<String, String> remove(String userName) throws IOException {
    Map<String, String> properties = propertiesMap.remove(userName);
    saveProperties();
    return properties;
  }

  public boolean remove(String userName, String name) throws IOException {
    Map<String, String> properties  = propertiesMap.get(userName);
    if (properties != null && !properties.containsKey(name)) {
      return false;
    }
    properties.remove(name);
    if (properties.isEmpty()) {
      propertiesMap.remove(userName);
    }
    saveProperties();
    return true;
  }

  private void saveProperties() throws IOException {
    if (persist) {
      saveToFile();
    }
  }

  private void loadFromFile() {
    LOG.info(propertiesFile.getAbsolutePath());
    if (!propertiesFile.exists()) {
      // nothing to read
      return;
    }

    try {
      FileInputStream fis = new FileInputStream(propertiesFile);
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
      PropertyInfoSaving info = gson.fromJson(json, PropertyInfoSaving.class);
      this.propertiesMap = info.userProperties;
    } catch (IOException e) {
      LOG.error("Error loading properties file", e);
    }
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (propertiesMap) {
      PropertyInfoSaving info = new PropertyInfoSaving();
      info.userProperties = propertiesMap;
      jsonString = gson.toJson(info);
    }

    try {
      if (!propertiesFile.exists()) {
        propertiesFile.createNewFile();
      }

      FileOutputStream fos = new FileOutputStream(propertiesFile, false);
      OutputStreamWriter out = new OutputStreamWriter(fos);
      out.append(jsonString);
      out.close();
      fos.close();
    } catch (IOException e) {
      LOG.error("Error saving properties file", e);
    }
  }
}
