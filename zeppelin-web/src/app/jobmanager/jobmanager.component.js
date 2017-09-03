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
import { JobManagerFilter } from './jobmanager.filter'
import { JobManagerService} from './jobmanager.service'

import { getJobIconByStatus, getJobColorByStatus } from './job-status'

angular.module('zeppelinWebApp')
  .controller('JobManagerCtrl', JobManagerController)
  .filter('JobManager', JobManagerFilter)
  .service('JobManagerService', JobManagerService)

const JobDateSorter = {
  RECENTLY_UPDATED: 'Recently Update',
  OLDEST_UPDATED: 'Oldest Updated',
}

function JobManagerController($scope, ngToast, JobManagerFilter, JobManagerService) {
  'ngInject'

  $scope.isFilterLoaded = false
  $scope.jobs = []
  $scope.sorter = {
    availableDateSorter: Object.keys(JobDateSorter).map(key => { return JobDateSorter[key] }),
    currentDateSorter: JobDateSorter.RECENTLY_UPDATED,
  }
  $scope.filteredJobs = $scope.jobs
  $scope.filterConfig = {
    isRunningAlwaysTop: true,
    noteNameFilterValue: '',
    interpreterFilterValue: '*',
    isSortByAsc: true,
  }

  $scope.pagination = {
    currentPage: 1,
    itemsPerPage: 10,
    maxPageCount: 5,
  }

  ngToast.dismiss()
  init()

  /** functions */

  $scope.setJobDateSorter = function(dateSorter) {
    $scope.sorter.currentDateSorter = dateSorter
  }

  $scope.getJobsInCurrentPage = function(jobs) {
    const cp = $scope.pagination.currentPage
    const itp = $scope.pagination.itemsPerPage
    return jobs.slice((cp - 1) * itp, (cp * itp))
  }

  let asyncNotebookJobFilter = function (jobs, filterConfig) {
    return new Promise((resolve, reject) => {
      $scope.filteredJobs = JobManagerFilter(jobs, filterConfig)
      resolve($scope.filteredJobs)
    })
  }

  $scope.$watch('sorter.currentDateSorter', function() {
    $scope.filterConfig.isSortByAsc =
      $scope.sorter.currentDateSorter === JobDateSorter.OLDEST_UPDATED
    asyncNotebookJobFilter($scope.jobs, $scope.filterConfig)
  })

  $scope.getJobIconByStatus = getJobIconByStatus
  $scope.getJobColorByStatus = getJobColorByStatus

  $scope.filterJobs = function (jobs, filterConfig) {
    asyncNotebookJobFilter(jobs, filterConfig)
      .then(() => {
        $scope.isFilterLoaded = true
      })
      .catch(error => {
        console.error('Failed to search jobs from server', error)
      })
  }

  $scope.filterValueToName = function (filterValue, maxStringLength) {
    if (typeof $scope.defaultInterpreters === 'undefined') {
      return
    }

    let index = $scope.defaultInterpreters.findIndex(intp => intp.value === filterValue)
    if (typeof $scope.defaultInterpreters[index].name !== 'undefined') {
      if (typeof maxStringLength !== 'undefined' &&
        maxStringLength > $scope.defaultInterpreters[index].name) {
        return $scope.defaultInterpreters[index].name.substr(0, maxStringLength - 3) + '...'
      }
      return $scope.defaultInterpreters[index].name
    } else {
      return 'NONE'
    }
  }

  $scope.setFilterValue = function (filterValue) {
    $scope.filterConfig.interpreterFilterValue = filterValue
    $scope.filterJobs($scope.jobs, $scope.filterConfig)
  }

  $scope.setJobs = function(jobs) {
    $scope.jobs = jobs
    let interpreters = $scope.jobs
      .filter(j => typeof j.interpreter !== 'undefined')
      .map(j => j.interpreter)
    interpreters = [...new Set(interpreters)] // remove duplicated interpreters

    $scope.defaultInterpreters = [ { name: 'ALL', value: '*' } ]
    for (let i = 0; i < interpreters.length; i++) {
      $scope.defaultInterpreters.push({ name: interpreters[i], value: interpreters[i] })
    }
  }

  function init() {
    JobManagerService.getJobs()
    JobManagerService.subscribeSetJobs($scope, setJobsCallback)
    JobManagerService.subscribeUpdateJobs($scope, updateJobsCallback)

    $scope.$on('$destroy', function () {
      JobManagerService.disconnect()
    })
  }

  /*
   ** $scope.$on functions below
   */

  function setJobsCallback(event, response) {
    const jobs = response.jobs
    $scope.setJobs(jobs)
    $scope.filterJobs($scope.jobs, $scope.filterConfig)
  }

  function updateJobsCallback(event, response) {
    let jobs = $scope.jobs
    let jobByNoteId = jobs.reduce((acc, j) => {
      const noteId = j.noteId
      acc[noteId] = j
      return acc
    }, {})

    let updatedJobs = response.jobs
    updatedJobs.map(updatedJob => {
      if (typeof jobByNoteId[updatedJob.noteId] === 'undefined') {
        let newItem = angular.copy(updatedJob)
        jobs.push(newItem)
        jobByNoteId[updatedJob.noteId] = newItem
      } else {
        let job = jobByNoteId[updatedJob.noteId]

        if (updatedJob.isRemoved === true) {
          delete jobByNoteId[updatedJob.noteId]
          let removeIndex = jobs.findIndex(j => j.noteId === updatedJob.noteId)
          if (removeIndex) {
            jobs.splice(removeIndex, 1)
          }
        } else {
          // update the job
          job.isRunningJob = updatedJob.isRunningJob
          job.noteName = updatedJob.noteName
          job.noteType = updatedJob.noteType
          job.interpreter = updatedJob.interpreter
          job.unixTimeLastRun = updatedJob.unixTimeLastRun
          job.paragraphs = updatedJob.paragraphs
        }
      }
    })
    $scope.filterJobs(jobs, $scope.filterConfig)
  }
}
