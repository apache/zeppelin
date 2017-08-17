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

export function JobManagerFilter() {
  function filterContext (jobs, filterConfig) {
    let interpreter = filterConfig.interpreterFilterValue
    let noteName = filterConfig.noteNameFilterValue
    let isSortByAsc = filterConfig.isSortByAsc
    let filteredJobs = jobs

    if (typeof interpreter === 'undefined') {
      filteredJobs = filteredJobs.filter((jobItem) => {
        return typeof jobItem.interpreter === 'undefined'
      })
    } else if (interpreter !== '*') {
      filteredJobs = filteredJobs.filter(j => j.interpreter === interpreter)
    }

    // filter by note name
    if (noteName !== '') {
      filteredJobs = filteredJobs.filter((jobItem) => {
        let lowerFilterValue = noteName.toLocaleLowerCase()
        let lowerNotebookName = jobItem.noteName.toLocaleLowerCase()
        return lowerNotebookName.match(new RegExp('.*' + lowerFilterValue + '.*'))
      })
    }

    // sort by name
    filteredJobs = filteredJobs.sort((jobItem) => {
      return jobItem.noteName.toLowerCase()
    })

    // sort by timestamp
    filteredJobs = filteredJobs.sort((x, y) => {
      if (isSortByAsc) {
        return x.unixTimeLastRun - y.unixTimeLastRun
      } else {
        return y.unixTimeLastRun - x.unixTimeLastRun
      }
    })

    return filteredJobs
  }
  return filterContext
}
