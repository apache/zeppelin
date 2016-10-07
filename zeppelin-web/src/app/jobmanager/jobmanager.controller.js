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

  angular.module('zeppelinWebApp').controller('JobmanagerCtrl', JobmanagerCtrl);

  JobmanagerCtrl.$inject = ['$scope', 'websocketMsgSrv', '$interval'];

  function JobmanagerCtrl($scope, websocketMsgSrv, $interval) {
    $scope.filterValueToName = function(filterValue) {
      var index = _.findIndex($scope.ACTIVE_INTERPRETERS, {value: filterValue});

      if ($scope.ACTIVE_INTERPRETERS[index].name !== undefined) {
        return $scope.ACTIVE_INTERPRETERS[index].name;
      } else {
        return 'undefined';
      }
    };

    $scope.init = function() {
      $scope.jobInfomations = [];
      $scope.JobInfomationsByFilter = $scope.jobInfomations;

      websocketMsgSrv.getNotebookJobsList();

      $scope.$on('$destroy', function() {
        websocketMsgSrv.unsubscribeJobManager();
      });
    };

    /*
    ** $scope.$on functions below
    */

    $scope.$on('setNotebookJobs', function(event, responseData) {
      $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
      $scope.jobInfomations = responseData.jobs;
      $scope.jobInfomationsIndexs = $scope.jobInfomations ? _.indexBy($scope.jobInfomations, 'notebookId') : {};
    });

    $scope.$on('setUpdateNotebookJobs', function(event, responseData) {
      var jobInfomations = $scope.jobInfomations;
      var indexStore = $scope.jobInfomationsIndexs;
      $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
      var notes = responseData.jobs;
      notes.map(function(changedItem) {
        if (indexStore[changedItem.notebookId] === undefined) {
          var newItem = angular.copy(changedItem);
          jobInfomations.push(newItem);
          indexStore[changedItem.notebookId] = newItem;
        } else {
          var changeOriginTarget = indexStore[changedItem.notebookId];

          if (changedItem.isRemoved !== undefined && changedItem.isRemoved === true) {

            // remove Item.
            var removeIndex = _.findIndex(indexStore, changedItem.notebookId);
            if (removeIndex > -1) {
              indexStore.splice(removeIndex, 1);
            }

            removeIndex = _.findIndex(jobInfomations, {'notebookId': changedItem.notebookId});
            if (removeIndex) {
              jobInfomations.splice(removeIndex, 1);
            }

          } else {
            // change value for item.
            changeOriginTarget.isRunningJob = changedItem.isRunningJob;
            changeOriginTarget.notebookName = changedItem.notebookName;
            changeOriginTarget.notebookType = changedItem.notebookType;
            changeOriginTarget.interpreter = changedItem.interpreter;
            changeOriginTarget.unixTimeLastRun = changedItem.unixTimeLastRun;
            changeOriginTarget.paragraphs = changedItem.paragraphs;
          }
        }
      });
    });
  }

})();
