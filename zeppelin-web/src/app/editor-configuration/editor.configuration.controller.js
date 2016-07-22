/*
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

angular.module('zeppelinWebApp').controller('EditorConfigurationCtrl', function($scope, $route, $routeParams, $location,
                                                                                $rootScope, $http, baseUrlSrv, $timeout,
                                                                                editorConfigSrv, websocketMsgSrv) {
  $scope.configrations = [];
  $scope._ = _;

  $scope.dumyParagraphInformation = {
    config: {looknfeel: 'default'},
    id: 'previewDumyNote',
    paragraphs: [{
      id: '0',
      text: '%spark',
      config: {
        colWidth: 12,
        editorMode: 'ace/mode/scala',
        enabled: true,
        graph: {
          'mode': 'table',
          'height': 300.0,
          'optionOpen': false,
          'keys': [],
          'values': [],
          'groups': [],
          'scatter': {}
        }
      }
    }]
  };

  $scope.isChangedConfigurationValue = false;

  $scope.updateEditorSettings = function() {
    $rootScope.$broadcast('updateEditorSettings');
  };

  $scope.startSaveTimer = function() {
    $scope.killSaveTimer();
    $scope.isNoteDirty = false;
    $scope.saveTimer = $timeout(function() {
    }, 10000);
  };

  $scope.killSaveTimer = function() {
    if ($scope.saveTimer) {
      $timeout.cancel($scope.saveTimer);
      $scope.saveTimer = null;
    }
  };

  $scope.codeEditorConfigurations = angular.copy(editorConfigSrv.getEditorConfigLists());
  $scope.editorThemeList = editorConfigSrv.getThemeSupportLists();
  $scope.editorLanguageList = editorConfigSrv.getLanguageSupportLists();

  $scope.isChangedConfiguration = function(isChanged) {
    $scope.isChangedConfigurationValue = isChanged;
  };

  $scope.changeLanguage = function(languageType) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.language = languageType;
    $scope.updateEditorSettings();
  };

  $scope.changeTheme = function(newTheme) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.theme = newTheme.themeValue;
    $scope.updateEditorSettings();
  };

  $scope.changeTabSize = function(tabSize) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.tabSize = tabSize;
    $scope.updateEditorSettings();
  };

  $scope.changeFontSize = function(fontSize) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.fontSize = fontSize;
    $scope.updateEditorSettings();
  };

  $scope.changeShowLineNumber = function(isShowLineNumber) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.showLineNumber = isShowLineNumber;
    $scope.updateEditorSettings();
  };

  $scope.changeShowFocusLine = function(isShowActiveLine) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.activeLine = isShowActiveLine;
    $scope.updateEditorSettings();
  };

  $scope.changeLiveAutoCompletion = function(isLiveAutoCompletion) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.liveAutoCompletion = isLiveAutoCompletion;
    $scope.updateEditorSettings();
  };

  $scope.changeShowPrintMargin = function(isShowPrintMargin) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.showPrintMargin = isShowPrintMargin;
    $scope.updateEditorSettings();
  };

  $scope.changeShowPrintMarginColumn = function(marginColumn) {
    $scope.isChangedConfiguration(true);
    $scope.modifyTargetEditorSetting.setting.showPrintMarginColumn = marginColumn;
    $scope.updateEditorSettings();
  };

  $scope.saveConfiguration = function() {
    $scope.isChangedConfiguration(false);
    $scope.restoreEditorConfig.userConfig = angular.copy($scope.modifyTargetEditorSetting);
    websocketMsgSrv.saveUserCodeEditorSetting(editorConfigSrv.getEditorConfigLists());
  };

  $scope.restoreConfiguration = function() {
    if ($scope.selectedEditorConfigIndex !== undefined && $scope.restoreEditorConfig !== undefined) {
      editorConfigSrv.setEditorConfig($scope.selectedEditorConfigIndex, $scope.restoreEditorConfig);
      $scope.updateEditorSettings();
    }
  };

  $scope.changeEditorTargetFromName = function(editorModeName) {
    var config = editorConfigSrv.findGetEditorConfigFromName(editorModeName);
    if (config !== undefined) {
      $scope.restoreEditorConfig = angular.copy(config.data);
      $scope.selectedEditorConfigIndex = config.index;
      $scope.modifyTargetEditorSetting = config.data.getUserConfig();
    }
  };

  $scope.changeEditorTargetFromIndex = function(index) {
    var config = editorConfigSrv.getEditorConfig(index);
    if (config !== undefined) {
      $scope.restoreEditorConfig = angular.copy(config);
      $scope.selectedEditorConfigIndex = index;
      $scope.modifyTargetEditorSetting = config.getUserConfig();
    }
  };

  $scope.showRemoveConfigurationDialog = function() {
    BootstrapDialog.confirm({
      closable: true,
      type: BootstrapDialog.TYPE_DANGER,
      title: 'DELETE',
      message: 'Do you want to delete this editor configuration?',
      callback: function(result) {
        if (result) {
          $scope.isChangedConfiguration(true);
          $scope.removeConfiguration($scope.selectedEditorConfigIndex);
        }
      }
    });
  };

  $scope.showCreateConfigurationDialog = function() {
    BootstrapDialog.show({
      title: 'Please Input to New &lt;User Editor Setting&gt; Name',
      message: jQuery('<form> ' +
        '<input class="form-control" ' +
        'type="text" id="newEditorConfigName" ' +
        'value="" ' +
        'placeholder="new Interpreter Configure Name"/>' +
        '</form>'),
      buttons: [{
        label: 'Create',
        cssClass: 'btn-primary',
        action: function(itSelf) {
          var configName = angular.element('#newEditorConfigName').val();
          if (configName !== undefined || configName !== '') {
            $scope.createConfiguration(configName);
            itSelf.close();
          }
        }
      }]
    });
  };

  $scope.removeConfiguration = function(index) {
    editorConfigSrv.removeEditorConfig(index);
    init();
  };

  $scope.userSelectConfigTarget = function(index) {
    $scope.restoreConfiguration();
    $scope.changeEditorTargetFromIndex(index);
  };

  $scope.createConfiguration = function(configName) {
    var newConfigName = configName;
    var newConfigRegex = '^%' + configName + '$';
    var newConfigIndex = editorConfigSrv.newEditorConfig(newConfigName, newConfigRegex);
    init(newConfigIndex);
  };

  var init = function(index) {
    var targetIndex = index ? index : 0;
    $scope.codeEditorConfigurations = angular.copy(editorConfigSrv.getEditorConfigLists());
    $scope.editorThemeList = editorConfigSrv.getThemeSupportLists();
    $scope.editorLanguageList = editorConfigSrv.getLanguageSupportLists();
    $scope.changeEditorTargetFromIndex(targetIndex);
  };

  init();

  $rootScope.$on('receiveEditorSettings', function() {
    init();
  });

  $scope.$on('$destroy', function() {
    $scope.restoreConfiguration();
  });

});
