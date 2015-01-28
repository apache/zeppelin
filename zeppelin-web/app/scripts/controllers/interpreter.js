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
    return {
      id : settingId,
      name : setting.name,
      group : setting.group,
      properties : setting.properties,
      interpreters : setting.interpreterGroup
    }
  };

  var getInterpreterSettings = function() {
    $http.get(getRestApiBase()+"/interpreter/setting").
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
        console.log("Error %o %o", status, data.message);
      });
  };


  var getAvailableInterpreters = function() {
    $http.get(getRestApiBase()+"/interpreter").
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
        console.log("Error %o %o", status, data.message);
      });
  };

  $scope.removeInterpreterSetting = function(settingId) {
    var result = confirm('Do you want to delete this interpreter setting?');
    if (!result) {
      return;
    }

    console.log("Delete setting %o", settingId);
    $http.delete(getRestApiBase()+"/interpreter/setting/"+settingId).
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
        console.log("Error %o %o", status, data.message);
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
        }
      }
    }
    $scope.newInterpreterSetting.properties = property;
  };

  $scope.restartInterpreterSetting = function(settingId) {
    var result = confirm('Do you want to restart this interpreter?');
    if (!result) {
      return;
    }

    $http.put(getRestApiBase()+"/interpreter/setting/restart/"+settingId).
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
        console.log("Error %o %o", status, data.message);
      });

  };

  $scope.addNewInterpreterSetting = function() {
    if (!$scope.newInterpreterSetting.name || !$scope.newInterpreterSetting.group) {
      alert("Please determine name and interpreter");
      return;
    }

    for (var i=0; i<$scope.interpreterSettings.length; i++) {
      var setting = $scope.interpreterSettings[i];
      if (setting.name === $scope.newInterpreterSetting.name) {
        alert("Name '"+setting.name+"' already exists");
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
    };

    $http.post(getRestApiBase()+"/interpreter/setting", newSetting).
      success(function(data, status, headers, config) {
        $scope.resetNewInterpreterSetting();
        getInterpreterSettings();
        alert("Interpreter setting created");
        $scope.showAddNewSetting = false;
      }).
      error(function(data, status, headers, config) {
        console.log("Error %o %o", status, data.message);
      });
  };


  $scope.resetNewInterpreterSetting = function() {
    $scope.newInterpreterSetting = {
      name : undefined,
      group : undefined,
      properties : {}
    };
    $scope.newInterpreterSetting.propertyValue = "";
    $scope.newInterpreterSetting.propertyKey = "";
  }

  $scope.removeNewInterpreterProperty = function(key) {
    delete $scope.newInterpreterSetting.properties[key];
  }

  $scope.addNewInterpreterProperty = function() {
    if (!$scope.newInterpreterSetting.propertyKey || $scope.newInterpreterSetting.propertyKey === "") {
      return;
    }

    $scope.newInterpreterSetting.properties[$scope.newInterpreterSetting.propertyKey] = { value : $scope.newInterpreterSetting.propertyValue};
    $scope.newInterpreterSetting.propertyValue = "";
    $scope.newInterpreterSetting.propertyKey = "";
  };

  var init = function() {
    // when interpeter page opened after seeing non-default looknfeel note, the css remains unchanged. that's what intepreter page want. Force set default looknfeel.
    $rootScope.$emit('setLookAndFeel', 'default');
    $scope.interpreterSettings = [];
    $scope.availableInterpreters = {};
    $scope.resetNewInterpreterSetting();

    getInterpreterSettings();
    getAvailableInterpreters();
  };

  init();
});
