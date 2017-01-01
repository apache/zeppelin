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
    $scope.packageInfos = {};
    $scope.defaultVersions = {};

    var buildDefaultVersionListToDisplay = function(packageInfos) {
      var defaultVersions = {};
      // show enabled version if any version of package is enabled
      for (var name in packageInfos) {
        var pkgs = packageInfos[name];
        for (var pkg in pkgs) {
          if (pkg.enabled) {
            defaultVersions[name] = pkg;
          }
        }

        // show first available version if package is not enabled
        if (!defaultVersions[name]) {
          defaultVersions[name] = pkgs[0];
        }
      }
      $scope.defaultVersions = defaultVersions;
    };

    var getAllPackageInfo = function() {
      $http.get(baseUrlSrv.getRestApiBase() + '/helium/all').
        success(function(data, status) {
          $scope.packageInfos = data.body;
          buildDefaultVersionListToDisplay($scope.packageInfos);
        }).
        error(function(data, status) {
          console.log('Can not load package info %o %o', status, data);
        });
    };

    var init = function() {
      getAllPackageInfo();
    };

    init();

    $scope.enable = function(name, artifact) {
      $http.post(baseUrlSrv.getRestApiBase() + '/helium/enable/' + name, artifact).
        success(function(data, status) {
          getAllPackageInfo();
        }).
        error(function(data, status) {
          console.log('Failed to enable package %o %o', name, artifact);
        });
    };

    $scope.disable = function(name) {
      $http.post(baseUrlSrv.getRestApiBase() + '/helium/disable/' + name).
        success(function(data, status) {
          getAllPackageInfo();
        }).
        error(function(data, status) {
          console.log('Failed to disable package %o', name);
        });
    };
  }
})();
