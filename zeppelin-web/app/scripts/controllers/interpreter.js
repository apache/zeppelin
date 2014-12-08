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
  var getInterpreterSettings = function() {
    $http.get(getRestApiBase()+"/interpreter/settings/list").
      success(function(data, status, headers, config) {
        var interpreterSettings = [];
        console.log("data=%o", data);
        for (var settingId in data.body) {
          var setting = data.body[settingId];
          interpreterSettings.push({
              id : settingId,
              name : setting.name,
              group : setting.group,
              interpreters : setting.interpreterGroup
          });
        }

        $scope.interpreterSettings = interpreterSettings;
      }).
      error(function(data, status, headers, config) {
        console.log("Error %o %o", status, data.message);
      })
  };

  var init = function() {
    $scope.interpreterSettings = [];
    console.log("Get interp settings");
    getInterpreterSettings();
  };

  init();



});
