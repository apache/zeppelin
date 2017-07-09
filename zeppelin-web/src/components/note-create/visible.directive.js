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

angular.module('zeppelinWebApp').directive('modalvisible', modalvisible)

function modalvisible () {
  return {
    restrict: 'A',
    scope: {
      preVisibleCallback: '&previsiblecallback',
      postVisibleCallback: '&postvisiblecallback',
      targetinput: '@targetinput'
    },
    link: function (scope, element, attrs) {
      // Add some listeners
      let previsibleMethod = scope.preVisibleCallback
      let postVisibleMethod = scope.postVisibleCallback
      element.on('show.bs.modal', function (e) {
        let relatedTarget = angular.element(e.relatedTarget)
        let clone = relatedTarget.data('clone')
        let sourceNoteName = relatedTarget.data('source-note-name')
        let path = relatedTarget.data('path')
        let cloneNote = clone ? true : false
        previsibleMethod()(cloneNote, sourceNoteName, path)
      })
      element.on('shown.bs.modal', function (e) {
        if (scope.targetinput) {
          angular.element(e.target).find('input#' + scope.targetinput).select()
        }
        postVisibleMethod()
      })
    }
  }
}
