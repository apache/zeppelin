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

angular.module('zeppelinWebApp').controller('CredentialCtrl', function($scope, $route, $routeParams, $location, $rootScope,
                                                                         $http, baseUrlSrv) {
  $scope._ = _;

  $scope.updateCredentials = function() {
    $http.put(baseUrlSrv.getRestApiBase() + '/credential',
      { 'datasource': $scope.dataSource,
        'username': $scope.credentialUsername,
        'password': $scope.credentialPassword
      } ).
    success(function (data, status, headers, config) {
      alert('Successfully saved credentials');
      $scope.dataSource = '';
      $scope.credentialUsername = '';
      $scope.credentialPassword = '';
      console.log('Success %o %o', status, data.message);
    }).
    error(function (data, status, headers, config) {
      alert('Error saving credentials');
      console.log('Error %o %o', status, data.message);
    });
  };

});
