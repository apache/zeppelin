/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

angular.module('zeppelinWebApp').service('editorConfigSrv', function($q) {

  var builtInConfig = {
    editorSettings: [
      {
        userConfig:
        {
          modeName: 'Scala Spark',
          language: 'scala',
          modeRegex: '^%(spark)(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      },
      {
        userConfig:
        {
          modeName: 'Python',
          language: 'python',
          modeRegex: '^%(pyspark|python)(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      },
      {
        userConfig: {
          modeName: 'R',
          language: 'r',
          modeRegex: '^%(r|sparkr|knitr)(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      },
      {
        userConfig: {
          modeName: 'SQL',
          language: 'sql',
          modeRegex: '^%(jdbc|sql)(\(.+\))*(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      },
      {
        userConfig:
        {
          modeName: 'Markdown',
          language: 'markdown',
          modeRegex: '^%(md)(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      },
      {
        userConfig:
        {
          modeName: 'Sh',
          language: 'sh',
          modeRegex: '^%(sh)(\ |\n)+.*',
          setting: {
            theme: 'chrome',
            tabSize: 4,
            showLineNumber: false,
            activeLine: false,
            liveAutoCompletion: false,
            showPrintMargin: false,
            showPrintMarginColumn: 100,
            fontSize: 10
          }
        }
      }
    ]
  };

  var EditorConfigObject = {
    userConfig: {},
    setUserConfig: function(newConfig) {
      this.userConfig = newConfig;
    },
    getUserConfig: function() {
      return this.userConfig;
    },
    getLanguage: function() {
      return this.userConfig.language;
    },
    getModeName: function() {
      return this.userConfig.modeName;
    },
    getMode: function() {
      return 'ace/mode/' + this.userConfig.language;
    },
    getModeRegex: function() {
      return this.userConfig.modeRegex;
    },
    getThemeName: function() {
      return this.userConfig.setting.theme;
    },
    getTheme: function() {
      return 'ace/theme/' + this.userConfig.setting.theme;
    },
    getTabSize: function() {
      return this.userConfig.setting.tabSize;
    },
    getShowPrintMarginColumn: function() {
      return this.userConfig.setting.showPrintMarginColumn;
    },
    getFontFamily: function() {
      return this.userConfig.setting.fontFamily;
    },
    getFontSize: function() {
      return this.userConfig.setting.fontSize;
    },
    getFontSizeHtmlFormat: function() {
      return this.userConfig.setting.fontSize + 'pt';
    },
    isShowNumberLine: function() {
      return this.userConfig.setting.showLineNumber;
    },
    isActiveLine: function() {
      return this.userConfig.setting.activeLine;
    },
    isShowPrintMargin: function() {
      return this.userConfig.setting.showPrintMargin;
    },
    isLiveAutoCompletion: function() {
      return this.userConfig.setting.liveAutoCompletion;
    },
    isCorrectEditorFromInterpreterTag: function(context) {
      var checkRegex = new RegExp(this.getModeRegex());
      return checkRegex.test(context);
    }
  };

  var themeSupportLists = [
    {'themeValue': 'chrome'}
    ];

  var languageSupportLists = [
    {languageType: 'scala'},
    {languageType: 'sh'},
    {languageType: 'java'},
    {languageType: 'sql'},
    {languageType: 'r'},
    {languageType: 'markdown'},
    {languageType: 'python'},
    {languageType: 'javascript'},
    {languageType: 'mysql'},
    {languageType: 'pgsql'}
  ];

  var defaultModeValue = 'Scala Spark';
  var isServerReceivedValue = false;
  var editorConfigList = [];

  function initializeEditorSettings(userEditorSettings) {
    return $q(function(success, fail) {
      if (userEditorSettings.length > 0) {
        editorConfigList.splice(0, editorConfigList.length);
        userEditorSettings.map(function(editorConfig) {
          var configItem =  angular.copy(EditorConfigObject);
          configItem.setUserConfig(editorConfig.userConfig);
          editorConfigList.push(configItem);
        });
      }
      if (success !== undefined) {
        success();
      }
    });
  }

  this.initConfig = initializeEditorSettings;

  this.initConfig(builtInConfig.editorSettings);

  this.isServerReceived = function() {
    return isServerReceivedValue;
  };

  this.setServerReceived = function(status) {
    isServerReceivedValue = status;
  };

  this.getEditorConfigLists = function() {
    return editorConfigList;
  };

  this.setEditorConfig = function(index, data) {
    editorConfigList.splice(index, 1, data);
  };

  this.getEditorConfig = function(index) {
    return editorConfigList[index];
  };

  this.newEditorConfig = function(configName, configRegex) {
    console.log(configName);
    var newConfig = {
      modeName: configName,
      language: 'scala',
      modeRegex: configRegex,
      setting: {
        theme: 'chrome',
        tabSize: 4,
        showLineNumber: false,
        activeLine: false,
        liveAutoCompletion: false,
        showPrintMargin: false,
        showPrintMarginColumn: 100,
        fontSize: 10
      }
    };
    var configItem =  angular.copy(EditorConfigObject);
    configItem.setUserConfig(newConfig);
    console.log(configItem);
    editorConfigList.push(configItem);
    return editorConfigList.length - 1;
  };

  this.removeEditorConfig = function(index) {
    editorConfigList.splice(index, 1);
  };

  this.getThemeSupportLists = function() {
    return angular.copy(themeSupportLists);
  };

  this.getLanguageSupportLists = function() {
    return angular.copy(languageSupportLists);
  };

  this.findGetEditorConfigFromName = function(modeName) {
    var index = _.findIndex(editorConfigList, function(config) {
      return config.getModeName() === modeName;
    });
    return index < 0 ? undefined : {index: index, data: editorConfigList[index]};
  };

  this.findGetEditorConfigFromInterpreterTag = function(interpreterTag) {
    var index = _.findIndex(editorConfigList, function(config) {
      return config.isCorrectEditorFromInterpreterTag(interpreterTag);
    });
    return editorConfigList[index];
  };

  this.getDefaultModeName = function() {
    return defaultModeValue;
  };

  this.getDefaultEditorSetting = function() {
    var defaultConfigIndex = _.findIndex(editorConfigList, function(config) {
      if (config === undefined) {
        return false;
      }
      return config.getModeName() === defaultModeValue;
    });

    if (defaultConfigIndex >= 0) {
      return editorConfigList[defaultConfigIndex];
    } else {
      return editorConfigList ? editorConfigList[0] : undefined;
    }
  };

});
