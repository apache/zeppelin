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

/*
$scope.headerHtml: 'render header html',
$scope.bodyHtml: 'render body html',
$scope.footerHtml: render footer html,
$scope.iframeUrl : render iframe in separate body
*/

  angular.module('zeppelinWebApp').controller('Modalcontroller', Modalcontroller);
  function Modalcontroller($scope, $element, $sce) {
    $scope.iframeUrl = $scope.$parent.modalAttr.path;
    $scope.modalClose = function() {
      $scope.$parent.modalAttr.showmodal = false;
      $scope.$parent.modalAttr.path = '';
      const el = angular.element('body');
      el.removeClass('modal-open');
      el.removeAttr('style');
      el.find('.modal-backdrop').remove();
    };
  }
