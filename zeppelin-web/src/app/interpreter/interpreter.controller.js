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
                                                                         $http, baseUrlSrv) {
  var interpreterSettingsTmp = [];
  $scope.interpreterSettings = [];
  $scope.availableInterpreters = {};
  $scope.showAddNewSetting = false;
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

  var removeTMPSettings = function(index) {
    interpreterSettingsTmp.splice(index, 1);
  };

  $scope.copyOriginInterpreterSettingProperties = function(settingId) {
    var index = _.findIndex($scope.interpreterSettings, { 'id': settingId });
    interpreterSettingsTmp[index] = angular.copy($scope.interpreterSettings[index]);
  };

  $scope.updateInterpreterSetting = function(settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to update this interpreter and restart with new settings?',
      callback: function(result) {
        if (result) {
          var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
          var request = {
            properties: angular.copy($scope.interpreterSettings[index].properties),
          };

          $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId, request).
            success(function (data, status, headers, config) {
              $scope.interpreterSettings[index] = data.body;
              removeTMPSettings(index);
            }).
            error(function (data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
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
          value : intpInfo[key].defaultValue,
          description : intpInfo[key].description
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

    var newSetting = angular.copy($scope.newInterpreterSetting);

    for (var p in $scope.newInterpreterSetting.properties) {
      newSetting.properties[p] = $scope.newInterpreterSetting.properties[p].value;
    }

    $http.post(baseUrlSrv.getRestApiBase()+'/interpreter/setting', newSetting).
    success(function(data, status, headers, config) {
      $scope.resetNewInterpreterSetting();
      getInterpreterSettings();
      $scope.showAddNewSetting = false;
    }).
    error(function(data, status, headers, config) {
      console.log('Error %o %o', status, data.message);
    });
  };

  $scope.cancelInterpreterSetting = function() {
    $scope.showAddNewSetting = false;
  };

  $scope.resetNewInterpreterSetting = function() {
    $scope.newInterpreterSetting = {
      name : undefined,
      group : undefined,
      properties : {}
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

  var init = function() {
    $scope.resetNewInterpreterSetting();
    getInterpreterSettings();
    getAvailableInterpreters();
  };

  init();
});
