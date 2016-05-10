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
  .controller('JobmanagerCtrl',
    function($scope, $route, $routeParams, $location, $rootScope, $http,
             websocketMsgSrv, baseUrlSrv, $timeout, SaveAsService) {

      $scope.debugPrint = function (value) {
        console.log(value);
      };

      $scope.init = function () {
        $scope.FILTER_VALUE_INTERPRETER = /.*/;
        $scope.ACTIVE_INTERPRETERS = [
          {
            name : 'ALL',
            value : /.*/
          }
        ];
        $scope.jobInfomations = initTestPacket();

        var interpreterLists = _.pluck($scope.jobInfomations, 'interpreter');
        for (var index = 0, length = interpreterLists.length; index < length; index++) {
          $scope.ACTIVE_INTERPRETERS.push({
            name : interpreterLists[index],
            value : interpreterLists[index]
          })
        }
      };

      var initTestPacket = function () {
        return [
          {
            notebookId: "2BMFSNAAF",
            notebookName: "myNotebook",
            notebookType: "normal",
            interpreter: "mySpark",
            isRunningJob: true,
            paragraphs: [
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "RUNNING"
              },

              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "PENDING"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "PENDING"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              }
            ]
          },
          {
            notebookId: "2BMFSNAAF",
            notebookName: "hiveNotebook",
            notebookType: "normal",
            interpreter: "hive",
            isRunningJob: false,
            paragraphs: [
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },

              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "ABORT"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "PENDING"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              }
            ]
          },
          {
            notebookId: "2BMFSNAAF",
            notebookName: "R Notebook",
            notebookType: "cron",
            interpreter: "r",
            isRunningJob: true,
            paragraphs: [
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "FINISHED"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "RUNNING"
              },

              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "PENDING"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "ERROR"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "ERROR"
              }
            ]
          },
          {
            notebookId: "2BMFSNAAF",
            notebookName: "myNotebook",
            notebookType: "normal",
            interpreter: "mySpark",
            isRunningJob: false,
            paragraphs: [
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              },
              {
                id: "20160509-112757_1030139246",
                name: "20160509-112757_1030139246",
                status: "READY"
              }
            ]
          }
        ];
      };
});
