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
import { getJobColorByStatus, getJobIconByStatus } from '../job-status'

import jobTemplate from './job.html'
import './job.css'

class JobController {
  constructor($http, JobManagerService) {
    'ngInject'
    this.$http = $http
    this.JobManagerService = JobManagerService
  }

  isRunning() {
    return this.note.isRunningJob
  }

  getParagraphs() {
    return this.note.paragraphs
  }

  getNoteId() {
    return this.note.noteId
  }

  getNoteName() {
    return this.note.noteName
  }

  runJob() {
    BootstrapDialog.confirm({
      closable: true,
      title: 'Job Dialog',
      message: 'Run all paragraphs?',
      callback: clickOk => {
        if (!clickOk) { return }

        const noteId = this.getNoteId()
        // if the request is handled successfully, the job page will get updated using websocket
        this.JobManagerService.sendRunJobRequest(noteId)
          .catch(response => {
            let message = (response.data && response.data.message)
              ? response.data.message : 'SERVER ERROR'
            this.showErrorDialog('Execution Failure', message)
          })
      }
    })
  }

  stopJob() {
    BootstrapDialog.confirm({
      closable: true,
      title: 'Job Dialog',
      message: 'Stop all paragraphs?',
      callback: clickOk => {
        if (!clickOk) { return }

        const noteId = this.getNoteId()
        // if the request is handled successfully, the job page will get updated using websocket
        this.JobManagerService.sendStopJobRequest(noteId)
          .catch(response => {
            let message = (response.data && response.data.message)
              ? response.data.message : 'SERVER ERROR'
            this.showErrorDialog('Stop Failure', message)
          })
      }
    })
  }

  showErrorDialog(title, errorMessage) {
    if (!errorMessage) { errorMessage = 'SERVER ERROR' }
    BootstrapDialog.alert({
      closable: true,
      title: title,
      message: errorMessage
    })
  }

  lastExecuteTime() {
    const timestamp = this.note.unixTimeLastRun
    return moment.unix(timestamp / 1000).fromNow()
  }

  getInterpreterName() {
    return typeof this.note.interpreter === 'undefined'
      ? 'interpreter is not set' : this.note.interpreter
  }

  getInterpreterNameStyle() {
    return typeof this.note.interpreter === 'undefined'
      ? { color: 'gray' } : { color: 'black' }
  }

  getJobTypeIcon() {
    const noteType = this.note.noteType
    if (noteType === 'normal') {
      return 'icon-doc'
    } else if (noteType === 'cron') {
      return 'icon-clock'
    } else {
      return 'icon-question'
    }
  }

  getJobColorByStatus(status) {
    return getJobColorByStatus(status)
  }

  getJobIconByStatus(status) {
    return getJobIconByStatus(status)
  }

  getProgress() {
    const paragraphs = this.getParagraphs()
    let paragraphStatuses = paragraphs.map(p => p.status)
    let runningOrFinishedParagraphs = paragraphStatuses.filter(status => {
      return status === ParagraphStatus.RUNNING || status === ParagraphStatus.FINISHED
    })

    let totalCount = paragraphStatuses.length
    let runningCount = runningOrFinishedParagraphs.length
    let result = Math.ceil(runningCount / totalCount * 100)
    result = isNaN(result) ? 0 : result

    return `${result}%`
  }

  showPercentProgressBar() {
    return this.getProgress() > 0 && this.getProgress() < 100
  }
}

export const JobComponent = {
  bindings: {
    note: '<',
  },
  template: jobTemplate,
  controller: JobController,
}

export const JobModule = angular
  .module('zeppelinWebApp')
  .component('job', JobComponent)
  .name
