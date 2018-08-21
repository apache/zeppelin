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

import './note-create.css';

angular.module('zeppelinWebApp').controller('NoteCreateCtrl', NoteCreateCtrl);

function NoteCreateCtrl($scope, noteListFactory, $routeParams, websocketMsgSrv) {
  'ngInject';

  let vm = this;
  vm.clone = false;
  vm.notes = noteListFactory;
  vm.websocketMsgSrv = websocketMsgSrv;
  $scope.note = {};
  $scope.interpreterSettings = {};
  $scope.note.defaultInterpreter = null;

  vm.createNote = function() {
    if (!vm.clone) {
      let defaultInterpreterGroup = '';
      if ($scope.note.defaultInterpreter !== null) {
        defaultInterpreterGroup = $scope.note.defaultInterpreter.name;
      }
      vm.websocketMsgSrv.createNotebook($scope.note.notename, defaultInterpreterGroup);
      $scope.note.defaultInterpreter = $scope.interpreterSettings[0];
    } else {
      let noteId = $routeParams.noteId;
      vm.websocketMsgSrv.cloneNote(noteId, $scope.note.notename);
    }
  };

  vm.handleNameEnter = function() {
    angular.element('#noteCreateModal').modal('toggle');
    vm.createNote();
  };

  vm.preVisible = function(clone, sourceNoteName, path) {
    vm.clone = clone;
    vm.sourceNoteName = sourceNoteName;
    $scope.note.notename = vm.clone ? vm.cloneNoteName() : vm.newNoteName(path);
    $scope.$apply();
  };

  vm.newNoteName = function(path) {
    let newCount = 1;
    angular.forEach(vm.notes.flatList, function(noteName) {
      noteName = noteName.name;
      if (noteName.match(/^Untitled Note [0-9]*$/)) {
        let lastCount = noteName.substr(14) * 1;
        if (newCount <= lastCount) {
          newCount = lastCount + 1;
        }
      }
    });
    return (path ? path + '/' : '') + 'Untitled Note ' + newCount;
  };

  vm.cloneNoteName = function() {
    let copyCount = 1;
    let newCloneName = '';
    let lastIndex = vm.sourceNoteName.lastIndexOf(' ');
    let endsWithNumber = !!vm.sourceNoteName.match('^.+?\\s\\d$');
    let noteNamePrefix = endsWithNumber ? vm.sourceNoteName.substr(0, lastIndex) : vm.sourceNoteName;
    let regexp = new RegExp('^' + noteNamePrefix + ' .+');

    angular.forEach(vm.notes.flatList, function(noteName) {
      noteName = noteName.name;
      if (noteName.match(regexp)) {
        let lastCopyCount = noteName.substr(lastIndex).trim();
        newCloneName = noteNamePrefix;
        lastCopyCount = parseInt(lastCopyCount);
        if (copyCount <= lastCopyCount) {
          copyCount = lastCopyCount + 1;
        }
      }
    });

    if (!newCloneName) {
      newCloneName = vm.sourceNoteName;
    }
    return newCloneName + ' ' + copyCount;
  };

  vm.getInterpreterSettings = function() {
    vm.websocketMsgSrv.getInterpreterSettings();
  };

  $scope.$on('interpreterSettings', function(event, data) {
    $scope.interpreterSettings = data.interpreterSettings;

    // initialize default interpreter with Spark interpreter
    $scope.note.defaultInterpreter = data.interpreterSettings[0];
  });
}
