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

import './expand-collapse.css'

angular.module('zeppelinWebApp').directive('expandCollapse', expandCollapseDirective)

function expandCollapseDirective() {
  return {
    restrict: 'EA',
    link: function (scope, element, attrs) {
      angular.element(element).click(function (event) {
        if (angular.element(element).next('.expandable:visible').length > 1) {
          angular.element(element).next('.expandable:visible').slideUp('slow')
          angular.element(element).find('i.fa-folder-open').toggleClass('fa-folder fa-folder-open')
        } else {
          angular.element(element).next('.expandable').first().slideToggle('200', function () {
            // do not toggle trash folder
            if (angular.element(element).find('.fa-trash-o').length === 0) {
              angular.element(element).find('i').first().toggleClass('fa-folder fa-folder-open')
            }
          })
        }

        let target = event.target

        // add note
        if (target.classList !== undefined && target.classList.contains('fa-plus') &&
          target.tagName.toLowerCase() === 'i') {
          return
        }

        event.stopPropagation()
      })
    }
  }
}
