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

import './note-rename.css'

angular.module('zeppelinWebApp').controller('NoteRenameCtrl', NoteRenameController)

function NoteRenameController($scope) {
  'ngInject'

  let self = this

  $scope.params = {newName: ''}
  $scope.isValid = true

  $scope.rename = function () {
    angular.element('#noteRenameModal').modal('hide')
    self.callback($scope.params.newName)
  }

  $scope.$on('openRenameModal', function (event, options) {
    self.validator = options.validator || defaultValidator
    self.callback = options.callback || function () {}

    $scope.title = options.title || 'Rename'
    $scope.params.newName = options.oldName || ''
    $scope.validate = function () {
      $scope.isValid = self.validator($scope.params.newName)
    }

    angular.element('#noteRenameModal').modal('show')
  })

  function defaultValidator (str) {
    return !!str.trim()
  }
}
