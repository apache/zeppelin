/* jshint loopfunc: true */
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

angular.module('zeppelinWebApp').controller('InterpreterCtrl', function($scope, $route, $routeParams, $location, $rootScope,
                                                                         $http, baseUrlSrv, ngToast) {
  var interpreterSettingsTmp = [];
  $scope.interpreterSettings = [];
  $scope.availableInterpreters = {};
  $scope.showAddNewSetting = false;
  $scope.showRepositoryInfo = false;
  $scope._ = _;

  var getInterpreterSettings = function() {
    $http.get(baseUrlSrv.getRestApiBase()+'/interpreter/setting').
      success(function(data, status, headers, config) {
        $scope.interpreterSettings = data.body;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  var getAvailableInterpreters = function() {
    $http.get(baseUrlSrv.getRestApiBase()+'/interpreter').
      success(function(data, status, headers, config) {
        $scope.availableInterpreters = data.body;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  var emptyNewProperty = function(object) {
    angular.extend(object, {propertyValue: '', propertyKey: ''});
  };

  var emptyNewDependency = function(object) {
    angular.extend(object, {depArtifact: '', depExclude: ''});
  };

  var removeTMPSettings = function(index) {
    interpreterSettingsTmp.splice(index, 1);
  };

  $scope.copyOriginInterpreterSettingProperties = function(settingId) {
    var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
    interpreterSettingsTmp[index] = angular.copy($scope.interpreterSettings[index]);
  };

  $scope.setSessionOption = function(settingId, sessionOption) {
    var option;
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option;
    } else {
      var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
      var setting = $scope.interpreterSettings[index];
      option = setting.option;
    }

    if (sessionOption === 'isolated') {
      option.perNoteSession = false;
      option.perNoteProcess = true;
    } else if (sessionOption === 'scoped') {
      option.perNoteSession = true;
      option.perNoteProcess = false;
    } else {
      option.perNoteSession = false;
      option.perNoteProcess = false;
    }
  };

  $scope.getSessionOption = function(settingId) {
    var option;
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option;
    } else {
      var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
      var setting = $scope.interpreterSettings[index];
      option = setting.option;
    }

    if (option.perNoteSession) {
      return 'scoped';
    } else if (option.perNoteProcess) {
      return 'isolated';
    } else {
      return 'shared';
    }
  };

  $scope.updateInterpreterSetting = function(form, settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to update this interpreter and restart with new settings?',
      callback: function (result) {
        if (result) {
          var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
          var setting = $scope.interpreterSettings[index];
          if (setting.propertyKey !== '' || setting.propertyKey) {
            $scope.addNewInterpreterProperty(settingId);
          }
          if (setting.depArtifact !== '' || setting.depArtifact) {
            $scope.addNewInterpreterDependency(settingId);
          }
          // add missing field of option
          if (!setting.option) {
            setting.option = {};
          }
          if (setting.option.remote === undefined) {
            // remote always true for now
            setting.option.remote = true;
          }
          var request = {
            option: angular.copy(setting.option),
            properties: angular.copy(setting.properties),
            dependencies: angular.copy(setting.dependencies)
          };

          $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId, request).
            success(function (data, status, headers, config) {
              $scope.interpreterSettings[index] = data.body;
              removeTMPSettings(index);
            }).
            error(function (data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
              ngToast.danger({content: data.message, verticalPosition: 'bottom'});
              form.$show();
            });
        }
      }
    });
  };

  $scope.resetInterpreterSetting = function(settingId){
    var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });

    // Set the old settings back
    $scope.interpreterSettings[index] = angular.copy(interpreterSettingsTmp[index]);
    removeTMPSettings(index);
  };

  $scope.removeInterpreterSetting = function(settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to delete this interpreter setting?',
      callback: function(result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId).
            success(function(data, status, headers, config) {

              var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
              $scope.interpreterSettings.splice(index, 1);
            }).
            error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
        }
      }
    });
  };

  $scope.newInterpreterGroupChange = function() {
    var el = _.pluck(_.filter($scope.availableInterpreters, { 'group': $scope.newInterpreterSetting.group }), 'properties');

    var properties = {};
    for (var i=0; i < el.length; i++) {
      var intpInfo = el[i];
      for (var key in intpInfo) {
        properties[key] = {
          value: intpInfo[key].defaultValue,
          description: intpInfo[key].description
        };
      }
    }

    $scope.newInterpreterSetting.properties = properties;
  };

  $scope.restartInterpreterSetting = function(settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to restart this interpreter?',
      callback: function(result) {
        if (result) {
          $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/restart/' + settingId).
            success(function(data, status, headers, config) {
              var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
              $scope.interpreterSettings[index] = data.body;
            }).
            error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
        }
      }
    });
  };

  $scope.addNewInterpreterSetting = function() {
    if (!$scope.newInterpreterSetting.name || !$scope.newInterpreterSetting.group) {
      BootstrapDialog.alert({
        closable: true,
        title: 'Add interpreter',
        message: 'Please determine name and interpreter'
      });
      return;
    }

    if (_.findIndex($scope.interpreterSettings, { 'name': $scope.newInterpreterSetting.name }) >= 0) {
      BootstrapDialog.alert({
        closable: true,
        title: 'Add interpreter',
        message: 'Name ' + $scope.newInterpreterSetting.name + ' already exists'
      });
      return;
    }

    var newSetting = $scope.newInterpreterSetting;
    if (newSetting.propertyKey !== '' || newSetting.propertyKey) {
      $scope.addNewInterpreterProperty();
    }
    if (newSetting.depArtifact !== '' || newSetting.depArtifact) {
      $scope.addNewInterpreterDependency();
    }

    var request = angular.copy($scope.newInterpreterSetting);

    // Change properties to proper request format
    var newProperties = {};
    for (var p in newSetting.properties) {
      newProperties[p] = newSetting.properties[p].value;
    }
    request.properties = newProperties;

    $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/setting', request).
      success(function(data, status, headers, config) {
        $scope.resetNewInterpreterSetting();
        getInterpreterSettings();
        $scope.showAddNewSetting = false;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
        ngToast.danger({content: data.message, verticalPosition: 'bottom'});
      });
  };

  $scope.cancelInterpreterSetting = function() {
    $scope.showAddNewSetting = false;
    $scope.resetNewInterpreterSetting();
  };

  $scope.resetNewInterpreterSetting = function() {
    $scope.newInterpreterSetting = {
      name: undefined,
      group: undefined,
      properties: {},
      dependencies: [],
      option: {
        remote: true,
        perNoteSession: false,
        perNoteProcess: false
      }
    };
    emptyNewProperty($scope.newInterpreterSetting);
  };

  $scope.removeInterpreterProperty = function(key, settingId) {
    if (settingId === undefined) {
      delete $scope.newInterpreterSetting.properties[key];
    }
    else {
      var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
      delete $scope.interpreterSettings[index].properties[key];
    }
  };

  $scope.removeInterpreterDependency = function(artifact, settingId) {
    if (settingId === undefined) {
      $scope.newInterpreterSetting.dependencies = _.reject($scope.newInterpreterSetting.dependencies,
        function(el) {
          return el.groupArtifactVersion === artifact;
        });
    } else {
      var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
      $scope.interpreterSettings[index].dependencies = _.reject($scope.interpreterSettings[index].dependencies,
        function(el) {
          return el.groupArtifactVersion === artifact;
        });
    }
  };

  $scope.addNewInterpreterProperty = function(settingId) {
    if(settingId === undefined) {
      // Add new property from create form
      if (!$scope.newInterpreterSetting.propertyKey || $scope.newInterpreterSetting.propertyKey === '') {
        return;
      }

      $scope.newInterpreterSetting.properties[$scope.newInterpreterSetting.propertyKey] = {
        value: $scope.newInterpreterSetting.propertyValue
      };
      emptyNewProperty($scope.newInterpreterSetting);
    }
    else {
      // Add new property from edit form
      var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
      var setting = $scope.interpreterSettings[index];

      if (!setting.propertyKey || setting.propertyKey === '') {
        return;
      }
      setting.properties[setting.propertyKey] = setting.propertyValue;
      emptyNewProperty(setting);
    }
  };

  $scope.addNewInterpreterDependency = function(settingId) {
    if(settingId === undefined) {
      // Add new dependency from create form
      if (!$scope.newInterpreterSetting.depArtifact || $scope.newInterpreterSetting.depArtifact === '') {
        return;
      }

      // overwrite if artifact already exists
      var newSetting = $scope.newInterpreterSetting;
      for(var d in newSetting.dependencies) {
        if (newSetting.dependencies[d].groupArtifactVersion === newSetting.depArtifact) {
          newSetting.dependencies[d] = {
            'groupArtifactVersion': newSetting.depArtifact,
            'exclusions': newSetting.depExclude
          };
          newSetting.dependencies.splice(d, 1);
        }
      }

      newSetting.dependencies.push({
        'groupArtifactVersion': newSetting.depArtifact,
        'exclusions': (newSetting.depExclude === '')? []: newSetting.depExclude
      });
      emptyNewDependency(newSetting);
    }
    else {
      // Add new dependency from edit form
      var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
      var setting = $scope.interpreterSettings[index];
      if (!setting.depArtifact || setting.depArtifact === '') {
        return;
      }

      // overwrite if artifact already exists
      for(var dep in setting.dependencies) {
        if (setting.dependencies[dep].groupArtifactVersion === setting.depArtifact) {
          setting.dependencies[dep] = {
            'groupArtifactVersion': setting.depArtifact,
            'exclusions': setting.depExclude
          };
          setting.dependencies.splice(dep, 1);
        }
      }

      setting.dependencies.push({
        'groupArtifactVersion': setting.depArtifact,
        'exclusions': (setting.depExclude === '')? []: setting.depExclude
      });
      emptyNewDependency(setting);
    }
  };

  $scope.resetNewRepositorySetting = function() {
    $scope.newRepoSetting = {
      id: undefined,
      url: undefined,
      snapshot: false,
      username: undefined,
      password: undefined
    };
  };

  var getRepositories = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/repository').
      success(function(data, status, headers, config) {
        $scope.repositories = data.body;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.addNewRepository = function() {
    var request = angular.copy($scope.newRepoSetting);

    $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/repository', request).
      success(function(data, status, headers, config) {
        getRepositories();
        $scope.resetNewRepositorySetting();
        angular.element('#repoModal').modal('hide');
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', headers, config);
      });
  };

  $scope.removeRepository = function(repoId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to delete this repository?',
      callback: function(result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase()+'/interpreter/repository/' + repoId).
            success(function(data, status, headers, config) {
              var index = _.findIndex($scope.repositories, { 'id': repoId });
              $scope.repositories.splice(index, 1);
            }).
            error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
        }
      }
    });
  };

  $scope.isDefaultRepository = function(repoId) {
    if (repoId === 'central' || repoId === 'local') {
      return true;
    } else {
      return false;
    }
  };

  var init = function() {
    $scope.resetNewInterpreterSetting();
    $scope.resetNewRepositorySetting();
    getInterpreterSettings();
    getAvailableInterpreters();
    getRepositories();
  };

  init();
});
