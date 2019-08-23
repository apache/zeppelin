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

import {getNodeColorByStatus, getNodeIconByStatus} from './node-status';

angular.module('zeppelinWebApp').controller('ClusterCtrl', ClusterController);

const NodeDateSorter = {
  Domain_ASC: 'Domain ASC',
  Domain_DSC: 'Domain DSC',
};

function ClusterController($scope, $rootScope, $http, baseUrlSrv, ngToast, websocketMsgSrv) {
  'ngInject';

  $scope.isFilterLoaded = false;
  $scope.nodes = [];
  $scope.sorter = {
    availableDateSorter: Object.keys(NodeDateSorter).map((key) => {
      return NodeDateSorter[key];
    }),
    currentDateSorter: NodeDateSorter.Domain_ASC,
  };
  $scope.filteredNodes = $scope.nodes;
  $scope.filterConfig = {
    isRunningAlwaysTop: true,
    nodeNameFilterValue: '',
    interpreterFilterValue: '*',
    isSortByAsc: true,
  };

  $scope.pagination = {
    currentPage: 1,
    itemsPerPage: 10,
    maxPageCount: 5,
  };

  ngToast.dismiss();
  init();

  /** functions */

  $scope.setNodeDateSorter = function(dateSorter) {
    $scope.sorter.currentDateSorter = dateSorter;
  };

  $scope.getNodesInCurrentPage = function(nodes) {
    const cp = $scope.pagination.currentPage;
    const itp = $scope.pagination.itemsPerPage;
    return nodes.slice((cp - 1) * itp, (cp * itp));
  };

  $scope.getNodeIconByStatus = getNodeIconByStatus;

  $scope.getNodeColorByStatus = getNodeColorByStatus;

  $scope.filterNodes = function(nodes, filterConfig) {
    return nodes;
  };

  function init() {
    $http.get(baseUrlSrv.getRestApiBase() + '/cluster/nodes')
      .success(function(data, status, headers, config) {
        $scope.nodes = data.body;
        $scope.filteredNodes = $scope.nodes;
        console.log(JSON.stringify($scope.nodes));
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
    $scope.isFilterLoaded = true;
  }
}
