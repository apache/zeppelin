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

package org.apache.zeppelin.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterInfoSaving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Code editor settings for Zeppelin Web Application
 */
public class CodeEditorWebSettings {
  private static Logger logger = LoggerFactory.getLogger(CodeEditorWebSettings.class);

  protected UserEditorSettingDao userEditorSettingDao;
  protected final String USER_EDITOR_SETTINGS_KEY_NAME = "editorInformation";

  private ZeppelinConfiguration conf;
  private Gson gson;

  public CodeEditorWebSettings(ZeppelinConfiguration conf) {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();

    this.conf = conf;
    userEditorSettingDao = new UserEditorSettingDao();
  }

  public List<EditorConfig> getUserEditorConfig(String userName) {
    logger.info("clover 1 {}", userEditorSettingDao.toString());
    return userEditorSettingDao.getUserSetting(userName);
  }

  public void setUserEditorConfig(String userName, List<EditorConfig> userEditorSetting ) {
    userEditorSettingDao.setUserSetting(userName, userEditorSetting);
  }

  public String getUserEditorSettingsKeyName() {
    return USER_EDITOR_SETTINGS_KEY_NAME;
  }

  public boolean deserialize() {
    try {
      String jsonString = loadFromFileToString(conf.getUserCodeWebEditorSettingPath());
      if (jsonString == null) {
        logger.info("code editor setting context is null");
        return false;
      }

      logger.info("load to User code editor setting", jsonString);
      Map<String, Map<String, List<EditorConfig>>> mapData = gson.fromJson(jsonString, Map.class);
      if (mapData == null) {
        logger.info("code editor setting is invalid json format - {}",
          conf.getUserCodeWebEditorSettingPath());
        return false;
      }
      userEditorSettingDao = new UserEditorSettingDao();
      if (mapData.get(getUserEditorSettingsKeyName()) != null) {
        userEditorSettingDao.setEditorInformation(mapData.get(getUserEditorSettingsKeyName()));
      } else {
        logger.info("Exists to code editor setting, but can not load to {} key",
          getUserEditorSettingsKeyName());
      }

    } catch (IOException ex) {
      logger.error("can not read code editor setting file - {}",
        conf.getUserCodeWebEditorSettingPath());
      return false;
    }

    return true;
  }

  public boolean serialize() {
    try {
      saveToJsonFile(conf.getUserCodeWebEditorSettingPath());
    } catch (IOException ex) {
      logger.error("can not write code editor setting file - {}",
        conf.getUserCodeWebEditorSettingPath());
      return false;
    }
    return true;
  }

  protected String loadFromFileToString(String configPath) throws IOException {
    File settingFile = new File(configPath);
    if (!settingFile.exists()) {
      // nothing to read
      return null;
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

    return sb.toString();
  }

  protected void saveToJsonFile(String configPath) throws IOException {
    String jsonString = gson.toJson(userEditorSettingDao,
      new TypeToken<UserEditorSettingDao>() {}.getType());

    File settingFile = new File(configPath);
    if (!settingFile.exists()) {
      settingFile.createNewFile();
    }

    FileOutputStream fos = new FileOutputStream(settingFile, false);
    OutputStreamWriter out = new OutputStreamWriter(fos);
    out.append(jsonString);
    out.close();
    fos.close();
  }
  /**
   * Code editor settings for Zeppelin Web Application
   * data access object
   */
  public class UserEditorSettingDao {
    public Map<String, List<EditorConfig>> editorInformation;

    public UserEditorSettingDao() {
      this.editorInformation = new HashMap<>();
    }

    public Map<String, List<EditorConfig>> getEditorInformation() {
      return this.editorInformation;
    }

    public void setEditorInformation(Map<String, List<EditorConfig>> editorInformation) {
      this.editorInformation = editorInformation;
    }

    public List<EditorConfig> getUserSetting(String userName) {
      return editorInformation.get(userName);
    }

    public void setUserSetting(String userName, List<EditorConfig> editorConfig) {
      this.editorInformation.put(userName, editorConfig);
    }

  }
  /**
   * Code editor settings for Zeppelin Web Application
   * editor config class
   */
  public class EditorConfig {
    private UserConfig userConfig;

    public UserConfig getUserConfig() {
      return this.userConfig;
    }

    public void setUserConfig(UserConfig userConfig) {
      this.userConfig = userConfig;
    }
  };

  /**
   * Code editor settings for Zeppelin Web Application
   * user config class
   */
  public class UserConfig {
    public String modeName;
    public String language;
    public String modeRegex;
    public Setting setting;

    public String getModeName() {
      return modeName;
    }

    public void setModeName(String modeName) {
      this.modeName = modeName;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }

    public String getModeRegex() {
      return modeRegex;
    }

    public void setModeRegex(String modeRegex) {
      this.modeRegex = modeRegex;
    }

    public Setting getSetting() {
      return setting;
    }

    public void setSetting(Setting setting) {
      this.setting = setting;
    }

    /**
     * Code editor settings for Zeppelin Web Application
     * setting sub class
     */
    public class Setting {
      public String theme;
      public Integer tabSize;
      public Integer showLineNumber;
      public boolean activeLine;
      public boolean liveAutoCompletion;
      public boolean showPrintMargin;
      public Integer showPrintMarginColumn;
      public Integer fontSize;

      public String getTheme() {
        return theme;
      }

      public void setTheme(String theme) {
        this.theme = theme;
      }

      public int getTabSize() {
        return tabSize;
      }

      public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
      }

      public int getShowLineNumber() {
        return showLineNumber;
      }

      public void setShowLineNumber(int showLineNumber) {
        this.showLineNumber = showLineNumber;
      }

      public boolean isActiveLine() {
        return activeLine;
      }

      public void setActiveLine(boolean activeLine) {
        this.activeLine = activeLine;
      }

      public boolean isLiveAutoCompletion() {
        return liveAutoCompletion;
      }

      public void setLiveAutoCompletion(boolean liveAutoCompletion) {
        this.liveAutoCompletion = liveAutoCompletion;
      }

      public boolean isShowPrintMargin() {
        return showPrintMargin;
      }

      public void setShowPrintMargin(boolean showPrintMargin) {
        this.showPrintMargin = showPrintMargin;
      }

      public int getShowPrintMarginColumn() {
        return showPrintMarginColumn;
      }

      public void setShowPrintMarginColumn(int showPrintMarginColumn) {
        this.showPrintMarginColumn = showPrintMarginColumn;
      }

      public int getFontSize() {
        return fontSize;
      }

      public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
      }
    }

  }
}
