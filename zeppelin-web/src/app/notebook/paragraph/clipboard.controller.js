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
angular.module('zeppelinWebApp').controller('clipboardCtrl', ClipboardController)

function ClipboardController ($scope) {
  'ngInject'

  $scope.complete = function (e) {
    $scope.copied = true
    $scope.tooltip = 'Copied!'
    setTimeout(function () {
      $scope.tooltip = 'Copy to clipboard'
    }, 400)
  }
  $scope.$watch('input', function () {
    $scope.copied = false
    $scope.tooltip = 'Copy to clipboard'
  })
  $scope.clipError = function (e) {
    console.log('Error: ' + e.name + ' - ' + e.message)
    $scope.tooltip = 'Not supported browser'
  }
}
