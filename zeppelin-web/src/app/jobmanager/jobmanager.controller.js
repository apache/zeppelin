/*jshint loopfunc: true, unused:false */
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

angular.module('zeppelinWebApp')
  .filter('myJob', function() {

    function filterContext(jobItems, filterConfig) {
      var FILTER_VALUE_INTERPRETER = filterConfig.FILTER_VALUE_INTERPRETER;
      var FILTER_VALUE_NOTEBOOK_NAME = filterConfig.FILTER_VALUE_NOTEBOOK_NAME;
      var RUNNING_ALWAYS_TOP = filterConfig.RUNNING_ALWAYS_TOP;

      var filterItems = jobItems;

      if (RUNNING_ALWAYS_TOP === true) {
        var runningJobList = _.where(filterItems, {isRunningJob : true});
        filterItems = _.reject(filterItems, {isRunningJob : true});
        runningJobList.map(function (runningJob) {
          filterItems.splice(0,0, runningJob);
        });
      }

      if (FILTER_VALUE_INTERPRETER === undefined) {
        filterItems = _.filter(filterItems, function (jobItem) {
          return jobItem.interpreter === undefined? true : false;
        });
      } else if (FILTER_VALUE_INTERPRETER !== '*') {
        filterItems = _.where(filterItems, {interpreter : FILTER_VALUE_INTERPRETER});
      }

      if (FILTER_VALUE_NOTEBOOK_NAME !== '') {
        filterItems = _.filter(filterItems, function(jobItem){
          var lowerFilterValue = FILTER_VALUE_NOTEBOOK_NAME.toLocaleLowerCase();
          var lowerNotebookName = jobItem.notebookName.toLocaleLowerCase();
          return lowerNotebookName.match(new RegExp('.*'+lowerFilterValue+'.*'));
		    });
      }
      return filterItems;
    }
    return filterContext;
  })
  .controller('JobmanagerCtrl',
    function($scope, $route, $routeParams, $location, $rootScope, $http,
             websocketMsgSrv, baseUrlSrv, $interval, $timeout, SaveAsService, myJobFilter) {

      $scope.$on('setNotebookJobs', function(event, responseData) {
        $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
        $scope.jobInfomations = responseData.jobs;
        $scope.jobInfomationsIndexs = $scope.jobInfomations? _.indexBy($scope.jobInfomations, 'notebookId') : {};
        $scope.JobInfomationsByFilter = $scope.jobTypeFilter($scope.jobInfomations, $scope.filterConfig);
        $scope.ACTIVE_INTERPRETERS = [
          {
            name : 'ALL',
            value : '*'
          }
        ];
        var interpreterLists = _.uniq(_.pluck($scope.jobInfomations, 'interpreter'), false);
        for (var index = 0, length = interpreterLists.length; index < length; index++) {
          $scope.ACTIVE_INTERPRETERS.push({
            name : interpreterLists[index],
            value : interpreterLists[index]
          });
        }
      });

      $scope.$on('setUpdateNotebookJobs', function(event, responseData) {
        var jobInfomations = $scope.jobInfomations;
        var indexStore = $scope.jobInfomationsIndexs;
        $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime;
        var notes = responseData.jobs;
        notes.map(function (changedItem) {
          if (indexStore[changedItem.notebookId] === undefined) {
            var newItem = angular.copy(changedItem);
            jobInfomations.push(newItem);
            indexStore[changedItem.notebookId] = newItem;
            console.log('111111111111111111111111');
          } else {
            console.log('222222222222222222222222');
            var changeOriginTarget = indexStore[changedItem.notebookId];
            changeOriginTarget.isRunningJob = changedItem.isRunningJob;
            changeOriginTarget.notebookName = changedItem.notebookName;
            changeOriginTarget.notebookType = changedItem.notebookType;
            changeOriginTarget.unixTimeLastRun = changedItem.unixTimeLastRun;
            changeOriginTarget.paragraphs = changedItem.paragraphs;
          }
          $scope.doFiltering(jobInfomations, $scope.filterConfig);
        });
      });

      $scope.doFiltering = function (jobInfomations, filterConfig) {
        $scope.JobInfomationsByFilter = $scope.jobTypeFilter(jobInfomations, filterConfig);
      };

      $scope.onChangeRunJobToAlwaysTopToggle = function (jobInfomations, filterConfig) {
        $scope.isRunJobToAlwaysTopToggle = !$scope.isRunJobToAlwaysTopToggle;
        $scope.doFiltering(jobInfomations, filterConfig);
      };

      $scope.doFilterInputTyping = function (keyEvent, jobInfomations, filterConfig) {
        var returnKey = 13;
        console.log('key ', keyEvent.which);
        $timeout.cancel($scope.dofilterTimeoutObject);
        $scope.dofilterTimeoutObject = $timeout(function(){$scope.doFiltering(jobInfomations, filterConfig);}, 1000);
        if (keyEvent.which === returnKey) {
          $timeout.cancel($scope.dofilterTimeoutObject);
          $scope.doFiltering(jobInfomations, filterConfig);
        }
      };

      $scope.init = function () {
        $scope.filterConfig = {
          RUNNING_ALWAYS_TOP : true,
          FILTER_VALUE_NOTEBOOK_NAME : '',
          FILTER_VALUE_INTERPRETER : '*'
        };
        $scope.jobTypeFilter = myJobFilter;
        $scope.jobInfomations = [];
        $scope.JobInfomationsByFilter = $scope.jobInfomations;

        websocketMsgSrv.getNotebookJobsList();
        var refreshObj = $interval(function () {
          websocketMsgSrv.getUpdateNotebookJobsList($scope.lastJobServerUnixTime);
        }, 1000);
        $scope.$on('$destroy', function() {
          $interval.cancel(refreshObj);
        });

      };
});
