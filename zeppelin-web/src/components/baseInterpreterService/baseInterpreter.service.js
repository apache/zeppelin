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

angular.module('zeppelinWebApp').service('baseInterpreterService', function($http, baseUrlSrv, $q) {

  this.getInterpreterSettings = function() {
    return $q(function(resolve, reject) {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting').
      success(function(data, status, headers, config) {
        resolve(data.body);
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
        reject();
      });
    });
  };

  this.restartInterpreterSetting = function(settingId) {
    return $q(function(resolve, reject) {
      $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/restart/' + settingId).
      success(function(data, status, headers, config) {
        resolve(data.body);
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
        reject();
      });
    });

  };

});
