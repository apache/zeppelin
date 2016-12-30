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

(function() {

  angular.module('zeppelinWebApp').controller('HeliumCtrl', HeliumCtrl);

  HeliumCtrl.$inject = ['$scope', '$rootScope', '$http', 'baseUrlSrv', 'ngToast'];

  function HeliumCtrl($scope, $rootScope, $http, baseUrlSrv, ngToast) {
    $scope.packageInfos = [];

    var getAllPackageInfo = function() {
      $http.get(baseUrlSrv.getRestApiBase() + '/helium/all').
        success(function(data, status) {
          console.log('Packages %o', data);
          $scope.packageInfos = data.body;
        }).
        error(function(data, status) {
          console.log('Can not load package info %o %o', status, data);
        });
    };

    var init = function() {
      getAllPackageInfo();
    };

    init();
  }
})();
