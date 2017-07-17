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

import moment from 'moment'

import { ParagraphStatus, } from '../../notebook/paragraph/paragraph.status'

angular.module('zeppelinWebApp').controller('JobCtrl', JobCtrl)

function JobCtrl ($scope, $http, baseUrlSrv) {
  'ngInject'

  $scope.progressValue = 0

  $scope.getProgress = function () {
    let statusList = _.pluck($scope.notebookJob.paragraphs, 'status')
    let runningJob = _.countBy(statusList, status => {
      return status === ParagraphStatus.RUNNING || status === ParagraphStatus.FINISHED
        ? 'matchCount' : 'none'
    })
    let totalCount = statusList.length
    let runningJobCount = runningJob.matchCount
    let result = Math.ceil(runningJobCount / totalCount * 100)
    result = isNaN(result) ? 0 : result

    return `${result}%`
  }

  $scope.showPercentProgressBar = function() {
    return $scope.getProgress() > 0 && $scope.getProgress() < 100
  }

  $scope.runNotebookJob = function (notebookId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Run all paragraphs?',
      callback: function (result) {
        if (result) {
          $http({
            method: 'POST',
            url: baseUrlSrv.getRestApiBase() + '/notebook/job/' + notebookId,
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            }
          }).then(response => {
            // success
          }, response => {
            let errorMessage
            // eslint-disable-next-line no-extra-boolean-cast
            if (!!response.data.message) { errorMessage = response.data.message }
            showErrorDialog('Execution Failure', errorMessage)
          })
        }
      }
    })
  }

  $scope.stopNotebookJob = function (notebookId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Stop all paragraphs?',
      callback: function (result) {
        if (result) {
          $http({
            method: 'DELETE',
            url: baseUrlSrv.getRestApiBase() + '/notebook/job/' + notebookId,
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded'
            }
          }).then(response => {
            // success
          }, response => {
            let errorMessage
            // eslint-disable-next-line no-extra-boolean-cast
            if (!!response.data.message) { errorMessage = response.data.message }
            showErrorDialog('Stop Failure', errorMessage)
          })
        }
      }
    })
  }

  function showErrorDialog(title, errorMessage) {
    if (!errorMessage) { errorMessage = 'SERVER ERROR' }
    BootstrapDialog.alert({
      closable: true,
      title: title,
      message: errorMessage
    })
  }

  $scope.lastExecuteTime = function (unixtime) {
    return moment.unix(unixtime / 1000).fromNow()
  }

  $scope.getInterpreterName = function() {
    return typeof $scope.notebookJob.interpreter === 'undefined'
      ? 'interpreter is not set' : $scope.notebookJob.interpreter
  }

  $scope.getInterpreterNameStyle = function() {
    return typeof $scope.notebookJob.interpreter === 'undefined'
      ? { color: 'gray' } : { color: 'black' }
  }

  $scope.getJobTypeIcon = function() {
    const noteType = $scope.notebookJob.noteType
    if (noteType === 'normal') {
      return 'icon-doc'
    } else if (noteType === 'cron') {
      return 'icon-clock'
    } else {
      return 'icon-question'
    }
  }
}
