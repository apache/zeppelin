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

  angular.module('zeppelinWebApp').directive('expandCollapse', expandCollapse);

  function expandCollapse() {
    return {
      restrict: 'EA',
      link: function(scope, element, attrs) {
        angular.element(element).click(function(event) {
          if (angular.element(element).find('.expandable:visible').length > 1) {
            angular.element(element).find('.expandable:visible').slideUp('slow');
            angular.element(element).find('i.icon-folder-alt').toggleClass('icon-folder icon-folder-alt');
          } else {
            angular.element(element).find('.expandable').first().slideToggle('200',function() {
              angular.element(element).find('i').first().toggleClass('icon-folder icon-folder-alt');
            });
          }
          event.stopPropagation();
        });
      }
    };
  }

})();
