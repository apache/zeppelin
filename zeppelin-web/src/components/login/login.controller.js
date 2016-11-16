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
(function() {

  angular.module('zeppelinWebApp').controller('LoginCtrl', LoginCtrl);

  LoginCtrl.$inject = ['$scope', '$rootScope', '$http', '$httpParamSerializer', 'baseUrlSrv'];
  function LoginCtrl($scope, $rootScope, $http, $httpParamSerializer, baseUrlSrv) {
    $scope.SigningIn = false;
    $scope.loginParams = {};
    $scope.login = function() {

      $scope.SigningIn = true;
      $http({
        method: 'POST',
        url: baseUrlSrv.getRestApiBase() + '/login',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        data: $httpParamSerializer({
          'userName': $scope.loginParams.userName,
          'password': $scope.loginParams.password
        })
      }).then(function successCallback(response) {
        $rootScope.ticket = response.data.body;
        angular.element('#loginModal').modal('toggle');
        $rootScope.$broadcast('loginSuccess', true);
        $rootScope.userName = $scope.loginParams.userName;
      }, function errorCallback(errorResponse) {
        $scope.loginParams.errorText = 'The username and password that you entered don\'t match.';
        $scope.SigningIn = false;
      });

    };

    var initValues = function() {
      $scope.loginParams = {
        userName: '',
        password: ''
      };
    };

    /*
    ** $scope.$on functions below
    */

    $scope.$on('initLoginValues', function() {
      initValues();
    });
  }

})();
