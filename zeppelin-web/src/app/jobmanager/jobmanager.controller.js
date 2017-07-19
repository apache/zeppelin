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

import './job/job.component'

import { getJobIconByStatus, getJobColorByStatus } from './job-status'

angular.module('zeppelinWebApp')
  .controller('JobManagerCtrl', JobManagerController)

const JobDateSorter = {
  RECENTLY_UPDATED: 'Recently Update',
  OLDEST_UPDATED: 'Oldest Updated',
}

function JobManagerController($scope, websocketMsgSrv, ngToast, $q, jobManagerFilter) {
  'ngInject'

  $scope.pagination = {
    currentPage: 1,
    itemsPerPage: 10,
    maxPageCount: 5,
  }

  $scope.sorter = {
    AvailableDateSorter: Object.keys(JobDateSorter).map(key => { return JobDateSorter[key] }),
    currentDateSorter: JobDateSorter.RECENTLY_UPDATED,
  }

  $scope.setJobDateSorter = function(dateSorter) {
    $scope.sorter.currentDateSorter = dateSorter
  }

  $scope.getJobsInCurrentPage = function(jobs) {
    const cp = $scope.pagination.currentPage
    const itp = $scope.pagination.itemsPerPage
    return jobs.slice((cp - 1) * itp, (cp * itp))
  }

  ngToast.dismiss()
  let asyncNotebookJobFilter = function (jobInfomations, filterConfig) {
    return $q(function (resolve, reject) {
      $scope.JobInfomationsByFilter = $scope.jobTypeFilter(jobInfomations, filterConfig)
      resolve($scope.JobInfomationsByFilter)
    })
  }

  $scope.$watch('sorter.currentDateSorter', function() {
    $scope.filterConfig.isSortByAsc =
      $scope.sorter.currentDateSorter === JobDateSorter.OLDEST_UPDATED
    asyncNotebookJobFilter($scope.jobInfomations, $scope.filterConfig)
  })

  $scope.getJobIconByStatus = getJobIconByStatus
  $scope.getJobColorByStatus = getJobColorByStatus

  $scope.doFiltering = function (jobInfomations, filterConfig) {
    asyncNotebookJobFilter(jobInfomations, filterConfig)
      .then(
        () => { $scope.isLoadingFilter = false },
        (error) => {
          console.error('Failed to search jobs from server', error)
        }
      )
  }

  $scope.filterValueToName = function (filterValue, maxStringLength) {
    if ($scope.activeInterpreters === undefined) {
      return
    }

    let index = $scope.activeInterpreters.findIndex(intp => intp.value === filterValue)
    if (typeof $scope.activeInterpreters[index].name !== 'undefined') {
      if (typeof maxStringLength !== 'undefined' &&
        maxStringLength > $scope.activeInterpreters[index].name) {
        return $scope.activeInterpreters[index].name.substr(0, maxStringLength - 3) + '...'
      }
      return $scope.activeInterpreters[index].name
    } else {
      return 'NONE'
    }
  }

  $scope.setFilterValue = function (filterValue) {
    $scope.filterConfig.filterValueInterpreter = filterValue
    $scope.doFiltering($scope.jobInfomations, $scope.filterConfig)
  }

  $scope.init = function () {
    $scope.isLoadingFilter = true
    $scope.jobInfomations = []
    $scope.JobInfomationsByFilter = $scope.jobInfomations
    $scope.filterConfig = {
      isRunningAlwaysTop: true,
      filterValueNotebookName: '',
      filterValueInterpreter: '*',
      isSortByAsc: $scope.sorter.currentDateSorter === JobDateSorter.OLDEST_UPDATED,
    }
    $scope.sortTooltipMsg = 'Switch to sort by desc'
    $scope.jobTypeFilter = jobManagerFilter

    websocketMsgSrv.getNoteJobsList()

    $scope.$on('$destroy', function () {
      websocketMsgSrv.unsubscribeJobManager()
    })
  }

  /*
   ** $scope.$on functions below
   */

  $scope.$on('setNoteJobs', function (event, responseData) {
    $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime
    $scope.jobInfomations = responseData.jobs
    $scope.jobTypeFilter($scope.jobInfomations, $scope.filterConfig)
    $scope.activeInterpreters = [ { name: 'ALL', value: '*' } ]

    let interpreters = $scope.jobInfomations
      .filter(j => typeof j.interpreter !== 'undefined')
      .map(j => j.interpreter)
    interpreters = [...new Set(interpreters)] // remove duplicated interpreters

    for (let i = 0; i < interpreters.length; i++) {
      $scope.activeInterpreters.push({ name: interpreters[i], value: interpreters[i] })
    }
    $scope.doFiltering($scope.jobInfomations, $scope.filterConfig)
  })

  $scope.$on('setUpdateNoteJobs', function (event, responseData) {
    let jobInfos = $scope.jobInfomations
    let jobByNoteId = $scope.jobInfomations.reduce((acc, j) => {
      const noteId = j.noteId
      acc[noteId] = j
      return acc
    }, {})
    $scope.lastJobServerUnixTime = responseData.lastResponseUnixTime

    let notes = responseData.jobs
    notes.map(changedItem => {
      if (typeof jobByNoteId[changedItem.noteId] === 'undefined') {
        let newItem = angular.copy(changedItem)
        jobInfos.push(newItem)
        jobByNoteId[changedItem.noteId] = newItem
      } else {
        let changeOriginTarget = jobByNoteId[changedItem.noteId]

        if (changedItem.isRemoved === true) {
          delete jobByNoteId[changedItem.noteId]
          let removeIndex = jobInfos.findIndex(j => j.noteId === changedItem.noteId)
          if (removeIndex) {
            jobInfos.splice(removeIndex, 1)
          }
        } else {
          // change value for item.
          changeOriginTarget.isRunningJob = changedItem.isRunningJob
          changeOriginTarget.noteName = changedItem.noteName
          changeOriginTarget.noteType = changedItem.noteType
          changeOriginTarget.interpreter = changedItem.interpreter
          changeOriginTarget.unixTimeLastRun = changedItem.unixTimeLastRun
          changeOriginTarget.paragraphs = changedItem.paragraphs
        }
      }
    })
    $scope.doFiltering(jobInfos, $scope.filterConfig)
  })
}
