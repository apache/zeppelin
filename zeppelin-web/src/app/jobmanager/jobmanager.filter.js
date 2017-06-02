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

angular.module('zeppelinWebApp').filter('jobManager', jobManagerFilter)

function jobManagerFilter () {
  function filterContext (jobItems, filterConfig) {
    let filterValueInterpreter = filterConfig.filterValueInterpreter
    let filterValueNotebookName = filterConfig.filterValueNotebookName
    let isSortByAsc = filterConfig.isSortByAsc
    let filterItems = jobItems

    if (filterValueInterpreter === undefined) {
      filterItems = filterItems.filter((jobItem) => {
        return jobItem.interpreter === undefined
      })
    } else if (filterValueInterpreter !== '*') {
      filterItems = _.where(filterItems, {interpreter: filterValueInterpreter})
    }

    if (filterValueNotebookName !== '') {
      filterItems = filterItems.filter((jobItem) => {
        let lowerFilterValue = filterValueNotebookName.toLocaleLowerCase()
        let lowerNotebookName = jobItem.noteName.toLocaleLowerCase()
        return lowerNotebookName.match(new RegExp('.*' + lowerFilterValue + '.*'))
      })
    }

    filterItems = filterItems.sort((jobItem) => {
      return jobItem.noteName.toLowerCase()
    })

    filterItems = filterItems.sort((x, y) => {
      if (isSortByAsc) {
        return x.unixTimeLastRun - y.unixTimeLastRun
      } else {
        return y.unixTimeLastRun - x.unixTimeLastRun
      }
    })

    return filterItems
  }
  return filterContext
}
