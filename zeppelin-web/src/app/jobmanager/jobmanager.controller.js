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

    function filterContext(JobItems, FILTER_VALUE_INTERPRETER, FILTER_VALUE_NOTEBOOK_NAME) {
      var filterItems = JobItems;
      if (FILTER_VALUE_INTERPRETER !== '*') {
        filterItems = _.where(filterItems, {interpreter : FILTER_VALUE_INTERPRETER});
        console.log(filterItems);
      }

      if (FILTER_VALUE_NOTEBOOK_NAME !== '') {

        filterItems = _.filter(filterItems, function(jobItem){
          var lowerFilterValue = FILTER_VALUE_NOTEBOOK_NAME.toLocaleLowerCase();
          var lowerNotebookName = jobItem.notebookName.toLocaleLowerCase();
          return lowerNotebookName.match(new RegExp('.*'+lowerFilterValue+'.*'));
		  });
      }

      console.log(_.sortBy(filterItems, 'isRunningJob'));

      return _.sortBy(filterItems, 'isRunningJob').reverse();
    }

    return filterContext;
  })
  .controller('JobmanagerCtrl',
    function($scope, $route, $routeParams, $location, $rootScope, $http,
             websocketMsgSrv, baseUrlSrv, $timeout, SaveAsService, myJobFilter) {


      $scope.doFiltering = function (jobInfomations, FILTER_VALUE_INTERPRETER, FILTER_VALUE_NOTEBOOK_NAME) {
        $scope.JobInfomationsByFilter = $scope.jobTypeFilter(jobInfomations, FILTER_VALUE_INTERPRETER, FILTER_VALUE_NOTEBOOK_NAME);
      };

      $scope.init = function () {

        $scope.FILTER_VALUE_NOTEBOOK_NAME = '';
        $scope.FILTER_VALUE_INTERPRETER = '*';
        $scope.jobTypeFilter = myJobFilter;
        $scope.ACTIVE_INTERPRETERS = [
          {
            name : 'ALL',
            value : '*'
          }
        ];
        $scope.jobInfomations = initTestPacket();
        $scope.JobInfomationsByFilter = $scope.jobInfomations;
        // initialize packet
        $scope.JobInfomationsByFilter = $scope.jobTypeFilter(
          $scope.jobInfomations, $scope.FILTER_VALUE_INTERPRETER, $scope.FILTER_VALUE_NOTEBOOK_NAME
        );
        var interpreterLists = _.pluck($scope.jobInfomations, 'interpreter');
        for (var index = 0, length = interpreterLists.length; index < length; index++) {
          $scope.ACTIVE_INTERPRETERS.push({
            name : interpreterLists[index],
            value : interpreterLists[index]
          });
        }
      };

      var initTestPacket = function () {
        return [
          {
            notebookId: '2BMFSNAAF',
            notebookName: 'myNotebook',
            notebookType: 'normal',
            interpreter: 'mySpark',
            isRunningJob: true,
            unixTimeLastRun: 1463561562,
            paragraphs: [
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'RUNNING'
              },

              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'PENDING'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'PENDING'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              }
            ]
          },
          {
            notebookId: '2BMFSNAAF',
            notebookName: 'hiveNotebook',
            notebookType: 'normal',
            interpreter: 'hive',
            isRunningJob: false,
            unixTimeLastRun: 1463561562,
            paragraphs: [
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },

              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'ABORT'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'PENDING'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              }
            ]
          },
          {
            notebookId: '2BMFSNAAF',
            notebookName: 'R Notebook',
            notebookType: 'cron',
            interpreter: 'r',
            isRunningJob: true,
            unixTimeLastRun: 1463561562,
            paragraphs: [
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'FINISHED'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'RUNNING'
              },

              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'PENDING'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'ERROR'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'ERROR'
              }
            ]
          },
          {
            notebookId: '2BMFSNAAF',
            notebookName: 'myNotebook',
            notebookType: 'normal',
            interpreter: 'mySpark',
            isRunningJob: false,
            unixTimeLastRun: 1463561562,
            paragraphs: [
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              },
              {
                id: '20160509-112757_1030139246',
                name: '20160509-112757_1030139246',
                status: 'READY'
              }
            ]
          }
        ];
      };
});
