/* global confirm:false, alert:false */
/* jshint loopfunc: true */
/* Copyright 2014 NFLabs
 *
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

/**
 * @ngdoc function
 * @name zeppelinWebApp.controller:InterpreterCtrl
 * @description
 * # InterpreterCtrl
 * Controller of interpreter, manage the note (update)
 */
angular.module('zeppelinWebApp').controller('InterpreterCtrl', function($scope, $route, $routeParams, $location, $rootScope, $http) {

  var remoteSettingToLocalSetting = function(settingId, setting) {
    var property = {};
    for (var key in setting.properties) {
      property[key] = {
        value : setting.properties[key]
      };
    }
    return {
      id : settingId,
      name : setting.name,
      group : setting.group,
      properties : property,
      interpreters : setting.interpreterGroup
    };
  };

  var getInterpreterSettings = function() {
    $http.get(getRestApiBase()+'/interpreter/setting').
      success(function(data, status, headers, config) {
        var interpreterSettings = [];
        //console.log("getInterpreterSettings=%o", data);

        for (var settingId in data.body) {
          var setting = data.body[settingId];
          interpreterSettings.push(remoteSettingToLocalSetting(setting.id, setting));
        }
        $scope.interpreterSettings = interpreterSettings;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  var getAvailableInterpreters = function() {
    $http.get(getRestApiBase()+'/interpreter').
      success(function(data, status, headers, config) {
        var groupedInfo = {};
        var info;
        for (var k in data.body) {
          info = data.body[k];
          if (!groupedInfo[info.group]) {
            groupedInfo[info.group] = [];
          }
          groupedInfo[info.group].push({
            name : info.name,
            className : info.className,
            properties : info.properties
          });
        }

        $scope.availableInterpreters = groupedInfo;
        //console.log("getAvailableInterpreters=%o", data);
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.copyOriginInterpreterSettingProperties = function(settingId) {
    $scope.interpreterSettingProperties = {};
    for (var i=0; i < $scope.interpreterSettings.length; i++) {
      var setting = $scope.interpreterSettings[i];
      if(setting.id === settingId) {
        angular.copy(setting.properties, $scope.interpreterSettingProperties);
        break;
      }
    }
    console.log('%o, %o', $scope.interpreterSettings[i], $scope.interpreterSettingProperties);
  };

  $scope.updateInterpreterSetting = function(settingId) {
    var result = confirm('Do you want to update this interpreter and restart with new settings?');
    if (!result) {
      return;
    }

    $scope.addNewInterpreterProperty(settingId);

    var properties = {};
    for (var i=0; i < $scope.interpreterSettings.length; i++) {
      var setting = $scope.interpreterSettings[i];
      if(setting.id === settingId) {
        for (var p in setting.properties) {
          properties[p] = setting.properties[p].value;
        }
        break;
      }
    }

    $http.put(getRestApiBase()+'/interpreter/setting/'+settingId, properties).
    success(function(data, status, headers, config) {
      for (var i=0; i < $scope.interpreterSettings.length; i++) {
        var setting = $scope.interpreterSettings[i];
        if (setting.id === settingId) {
          $scope.interpreterSettings.splice(i, 1);
          $scope.interpreterSettings.splice(i, 0, remoteSettingToLocalSetting(settingId, data.body));
          break;
        }
      }
    }).
    error(function(data, status, headers, config) {
      console.log('Error %o %o', status, data.message);
    });
  };

  $scope.resetInterpreterSetting = function(settingId){
    for (var i=0; i<$scope.interpreterSettings.length; i++) {
      var setting = $scope.interpreterSettings[i];
      if (setting.id ===settingId) {
        angular.copy($scope.interpreterSettingProperties, setting.properties);
        break;
      }
    }
  };

  $scope.removeInterpreterSetting = function(settingId) {
    var result = confirm('Do you want to delete this interpreter setting?');
    if (!result) {
      return;
    }

    console.log('Delete setting %o', settingId);
    $http.delete(getRestApiBase()+'/interpreter/setting/'+settingId).
      success(function(data, status, headers, config) {
        for (var i=0; i < $scope.interpreterSettings.length; i++) {
          var setting = $scope.interpreterSettings[i];
          if (setting.id === settingId) {
            $scope.interpreterSettings.splice(i, 1);
            break;
          }
        }
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.newInterpreterGroupChange = function() {
    var property = {};
    var intpGroupInfo = $scope.availableInterpreters[$scope.newInterpreterSetting.group];
    for (var i=0; i<intpGroupInfo.length; i++) {
      var intpInfo = intpGroupInfo[i];
      for (var key in intpInfo.properties) {
        property[key] = {
          value : intpInfo.properties[key].defaultValue,
          description : intpInfo.properties[key].description
        };
      }
    }
    $scope.newInterpreterSetting.properties = property;
  };

  $scope.restartInterpreterSetting = function(settingId) {
    var result = confirm('Do you want to restart this interpreter?');
    if (!result) {
      return;
    }

    $http.put(getRestApiBase()+'/interpreter/setting/restart/'+settingId).
      success(function(data, status, headers, config) {
        for (var i=0; i < $scope.interpreterSettings.length; i++) {
          var setting = $scope.interpreterSettings[i];
          if (setting.id === settingId) {
            $scope.interpreterSettings.splice(i, 1);
            $scope.interpreterSettings.splice(i, 0, remoteSettingToLocalSetting(settingId, data.body));
            break;
          }
        }
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.addNewInterpreterSetting = function() {
    if (!$scope.newInterpreterSetting.name || !$scope.newInterpreterSetting.group) {
      alert('Please determine name and interpreter');
      return;
    }

    for (var i=0; i<$scope.interpreterSettings.length; i++) {
      var setting = $scope.interpreterSettings[i];
      if (setting.name === $scope.newInterpreterSetting.name) {
        alert('Name ' + setting.name + ' already exists');
        return;
      }
    }

    $scope.addNewInterpreterProperty();

    var newSetting = {
      name : $scope.newInterpreterSetting.name,
      group : $scope.newInterpreterSetting.group,
      properties : {}
    };

    for (var p in $scope.newInterpreterSetting.properties) {
      newSetting.properties[p] = $scope.newInterpreterSetting.properties[p].value;
    }

    $http.post(getRestApiBase()+'/interpreter/setting', newSetting).
      success(function(data, status, headers, config) {
        $scope.resetNewInterpreterSetting();
        getInterpreterSettings();
        $scope.showAddNewSetting = false;
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };


  $scope.resetNewInterpreterSetting = function() {
    $scope.newInterpreterSetting = {
      name : undefined,
      group : undefined,
      properties : {}
    };
    $scope.newInterpreterSetting.propertyValue = '';
    $scope.newInterpreterSetting.propertyKey = '';
  };

  $scope.removeInterpreterProperty = function(key, settingId) {
    if (settingId === undefined) {
      delete $scope.newInterpreterSetting.properties[key];
    }
    else {
      for (var i=0; i < $scope.interpreterSettings.length; i++) {
        var setting = $scope.interpreterSettings[i];
        if (setting.id === settingId) {
          delete $scope.interpreterSettings[i].properties[key]
          break;
        }
      }
    }
  };

  $scope.addNewInterpreterProperty = function(settingId) {
    if(settingId === undefined) {
      if (!$scope.newInterpreterSetting.propertyKey || $scope.newInterpreterSetting.propertyKey === '') {
        return;
      }
      $scope.newInterpreterSetting.properties[$scope.newInterpreterSetting.propertyKey] = { value : $scope.newInterpreterSetting.propertyValue};
      $scope.newInterpreterSetting.propertyValue = '';
      $scope.newInterpreterSetting.propertyKey = '';
    }
    else {
      for (var i=0; i < $scope.interpreterSettings.length; i++) {
        var setting = $scope.interpreterSettings[i];
        if (setting.id === settingId){
          if (!setting.propertyKey || setting.propertyKey === '') {
            return;
          }
          setting.properties[setting.propertyKey] = { value : setting.propertyValue };
          setting.propertyValue = '';
          setting.propertyKey = '';
          break;
        }
      }
    }
  };

  var init = function() {
    // when interpreter page opened after seeing non-default looknfeel note, the css remains unchanged. that's what interpreter page want. Force set default looknfeel.
    $rootScope.$emit('setLookAndFeel', 'default');
    $scope.interpreterSettings = [];
    $scope.availableInterpreters = {};
    $scope.resetNewInterpreterSetting();

    getInterpreterSettings();
    getAvailableInterpreters();
  };

  init();
});
