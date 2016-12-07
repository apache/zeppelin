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

  angular.module('zeppelinWebApp').controller('JobCtrl', JobCtrl);

  JobCtrl.$inject = ['$scope', '$http', 'baseUrlSrv'];

  function JobCtrl($scope, $http, baseUrlSrv) {
    $scope.init = function(jobInformation) {
      $scope.progressValue = 0;
    };

    $scope.getProgress = function() {
      var statusList = _.pluck($scope.notebookJob.paragraphs, 'status');
      var runningJob = _.countBy(statusList, function(status) {
        if (status === 'FINISHED' || status === 'RUNNING') {
          return 'matchCount';
        } else {
          return 'none';
        }
      });
      var totalCount = statusList.length;
      var runningJobCount = runningJob.matchCount;
      var result = Math.ceil(runningJobCount / totalCount * 100);
      return isNaN(result) ? 0 : result;
    };

    $scope.runNotebookJob = function(notebookId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Run all paragraphs?',
        callback: function(result) {
          if (result) {
            $http({
              method: 'POST',
              url: baseUrlSrv.getRestApiBase() + '/notebook/job/' + notebookId,
              headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
              }
            }).then(function successCallback(response) {
              // success
            }, function errorCallback(errorResponse) {
              var errorText = 'SERVER ERROR';
              if (errorResponse.data.message !== undefined) {
                errorText = errorResponse.data.message;
              }
              BootstrapDialog.alert({
                closable: true,
                title: 'Execution Failure',
                message: errorText
              });
            });
          }
        }
      });
    };

    $scope.stopNotebookJob = function(notebookId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Stop all paragraphs?',
        callback: function(result) {
          if (result === true) {
            $http({
              method: 'DELETE',
              url: baseUrlSrv.getRestApiBase() + '/notebook/job/' + notebookId,
              headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
              }
            }).then(function successCallback(response) {
              // success
            }, function errorCallback(errorResponse) {
              var errorText = 'SERVER ERROR';
              if (errorResponse.data.message !== undefined) {
                errorText = errorResponse.data.message;
              }
              BootstrapDialog.alert({
                closable: true,
                title: 'Stop Failure',
                message: errorText
              });
            });
          }
        }
      });
    };
    
    $scope.lastExecuteTime = function(unixtime) {
      return moment.unix(unixtime / 1000).fromNow();
    };

  }

})();
