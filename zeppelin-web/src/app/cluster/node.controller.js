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

angular.module('zeppelinWebApp').controller('NodeCtrl', NodeCtrl);

function NodeCtrl($scope, $routeParams, $http, baseUrlSrv, ngToast) {
  'ngInject';
  $scope.nodeName = $routeParams.nodeName;
  $scope.intpName = $routeParams.intpName;
  $scope.intpProcesses = [];
  $scope.pagination = {
    currentPage: 1,
    itemsPerPage: 10,
    maxPageCount: 5,
  };
  if ($scope.intpName !== null && $scope.intpName !== '' && $scope.intpName !== 'all') {
    $scope.searchNode = $scope.intpName;
  } else {
    $scope.searchNode = '';
  }
  $scope.filteredProcesses=$scope.intpProcesses;
  $scope.nodeFilter = function(intpProcess) {
    return intpProcess.properties.INTP_PROCESS_NAME.indexOf($scope.searchNode) !== -1;
  };
  $scope._ = _;
  ngToast.dismiss();

  $scope.getProgressInCurrentPage = function(pros) {
    $scope.filteredProcesses = pros;
    const cp = $scope.pagination.currentPage;
    const itp = $scope.pagination.itemsPerPage;
    return pros.slice((cp - 1) * itp, (cp * itp));
  };

  let init = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/cluster/node/' + $scope.nodeName + '/' + $scope.intpName)
      .success(function(data, status, headers, config) {
        $scope.intpProcesses = data.body;
        console.log('scope.intpProcesses.length='+$scope.intpProcesses.length);
        console.log('scope.intpProcesses='+$scope.intpProcesses);
        console.log(JSON.stringify($scope.intpProcesses));
      })
      .error(function(data, status, headers, config) {
        if (status === 401) {
          ngToast.danger({
            content: 'You don\'t have permission on this page',
            verticalPosition: 'bottom',
            timeout: '3000',
          });
          setTimeout(function() {
            window.location = baseUrlSrv.getBase();
          }, 3000);
        }
        console.log('Error %o %o', status, data.message);
      });
  };

  init();
}
