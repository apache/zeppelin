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

  angular.module('zeppelinWebApp')
    .controller('JobmanagerCtrl', JobmanagerCtrl);

  JobmanagerCtrl.$inject = ['$scope', 'websocketMsgSrv', '$interval', 'ngToast', '$q', '$timeout', 'jobManagerFilter'];

  function JobmanagerCtrl($scope, websocketMsgSrv, $interval, ngToast, $q, $timeout, jobManagerFilter) {
    ngToast.dismiss();
    var asyncNotebookJobFilter = function(jobInfomations, filterConfig) {
      return $q(function(resolve, reject) {
        $scope.JobInfomationsByFilter = $scope.jobTypeFilter(jobInfomations, filterConfig);
        resolve($scope.JobInfomationsByFilter);
      });
    };

    $scope.doFiltering = function(jobInfomations, filterConfig) {
      asyncNotebookJobFilter(jobInfomations, filterConfig).then(
        function() {
          // success
          $scope.isLoadingFilter = false;
        },
        function() {
          // failed
        });
    };

    $scope.filterValueToName = function(filterValue, maxStringLength) {
      if ($scope.activeInterpreters === undefined) {
        return;
      }
      var index = _.findIndex($scope.activeInterpreters, {value: filterValue});
      if ($scope.activeInterpreters[index].name !== undefined) {
        if (maxStringLength !== undefined && maxStringLength > $scope.activeInterpreters[index].name) {
          return $scope.activeInterpreters[index].name.substr(0, maxStringLength - 3) + '...';
        }
        return $scope.activeInterpreters[index].name;
      } else {
        return 'Interpreter is not set';
      }
    };

    $scope.setFilterValue = function(filterValue) {
      $scope.filterConfig.filterValueInterpreter = filterValue;
      $scope.doFiltering($scope.jobInfomations, $scope.filterConfig);
    };

    $scope.onChangeRunJobToAlwaysTopToggle = function() {
      $scope.filterConfig.isRunningAlwaysTop = !$scope.filterConfig.isRunningAlwaysTop;
      $scope.doFiltering($scope.jobInfomations, $scope.filterConfig);
    };

    $scope.onChangeSortAsc = function() {
      $scope.filterConfig.isSortByAsc = !$scope.filterConfig.isSortByAsc;
      $scope.doFiltering($scope.jobInfomations, $scope.filterConfig);
    };

    $scope.doFilterInputTyping = function(keyEvent, jobInfomations, filterConfig) {
      var RETURN_KEY_CODE = 13;
      $timeout.cancel($scope.dofilterTimeoutObject);
      $scope.isActiveSearchTimer = true;
      $scope.dofilterTimeoutObject = $timeout(function() {
        $scope.doFiltering(jobInfomations, filterConfig);
        $scope.isActiveSearchTimer = false;
      }, 10000);
      if (keyEvent.which === RETURN_KEY_CODE) {
        $timeout.cancel($scope.dofilterTimeoutObject);
        $scope.doFiltering(jobInfomations, filterConfig);
        $scope.isActiveSearchTimer = false;
      }
    };

    $scope.doForceFilterInputTyping = function(keyEvent, jobInfomations, filterConfig) {
      $timeout.cancel($scope.dofilterTimeoutObject);
      $scope.doFiltering(jobInfomations, filterConfig);
      $scope.isActiveSearchTimer = false;
    };

    $scope.init = function() {
      $scope.isLoadingFilter = true;
      $scope.jobInfomations = [];
      $scope.JobInfomationsByFilter = $scope.jobInfomations;
      $scope.filterConfig = {
        isRunningAlwaysTop: true,
        filterValueNotebookName: '',
        filterValueInterpreter: '*',
        isSortByAsc: true
      };
      $scope.jobTypeFilter = jobManagerFilter;

      websocketMsgSrv.getNoteJobsList();

      $scope.$on('$destroy', function() {
        websocketMsgSrv.unsubscribeJobManager();
      });
    };

    /*
     ** $scope.$on functions below
     */

    $scope.$on('setNoteJobs', function(event, responseData) {
      $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
      $scope.jobInfomations = responseData.jobs;
      $scope.jobInfomationsIndexs = $scope.jobInfomations ? _.indexBy($scope.jobInfomations, 'noteId') : {};
      $scope.jobTypeFilter($scope.jobInfomations, $scope.filterConfig);
      $scope.activeInterpreters = [
        {
          name: 'ALL',
          value: '*'
        }
      ];
      var interpreterLists = _.uniq(_.pluck($scope.jobInfomations, 'interpreter'), false);
      for (var index = 0, length = interpreterLists.length; index < length; index++) {
        $scope.activeInterpreters.push({
          name: interpreterLists[index],
          value: interpreterLists[index]
        });
      }
      $scope.doFiltering($scope.jobInfomations, $scope.filterConfig);
    });

    $scope.$on('setUpdateNoteJobs', function(event, responseData) {
      var jobInfomations = $scope.jobInfomations;
      var indexStore = $scope.jobInfomationsIndexs;
      $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
      var notes = responseData.jobs;
      notes.map(function(changedItem) {
        if (indexStore[changedItem.noteId] === undefined) {
          var newItem = angular.copy(changedItem);
          jobInfomations.push(newItem);
          indexStore[changedItem.noteId] = newItem;
        } else {
          var changeOriginTarget = indexStore[changedItem.noteId];

          if (changedItem.isRemoved !== undefined && changedItem.isRemoved === true) {

            // remove Item.
            var removeIndex = _.findIndex(indexStore, changedItem.noteId);
            if (removeIndex > -1) {
              indexStore.splice(removeIndex, 1);
            }

            removeIndex = _.findIndex(jobInfomations, {'noteId': changedItem.noteId});
            if (removeIndex) {
              jobInfomations.splice(removeIndex, 1);
            }

          } else {
            // change value for item.
            changeOriginTarget.isRunningJob = changedItem.isRunningJob;
            changeOriginTarget.noteName = changedItem.noteName;
            changeOriginTarget.noteType = changedItem.noteType;
            changeOriginTarget.interpreter = changedItem.interpreter;
            changeOriginTarget.unixTimeLastRun = changedItem.unixTimeLastRun;
            changeOriginTarget.paragraphs = changedItem.paragraphs;
          }
        }
      });
      $scope.doFiltering(jobInfomations, $scope.filterConfig);
    });
  }

})();
