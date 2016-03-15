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

angular.module('zeppelinWebApp').controller('NotenameCtrl', function($scope, notebookListDataFactory,
                                                             $rootScope, $routeParams, websocketMsgSrv) {
  var vm = this;
  vm.clone = false;
  vm.notes = notebookListDataFactory;
  vm.websocketMsgSrv = websocketMsgSrv;
  $scope.note = {};

  vm.createNote = function() {
      if (!vm.clone) {
        vm.websocketMsgSrv.createNotebook($scope.note.notename);
      } else {
       var noteId = $routeParams.noteId;
       vm.websocketMsgSrv.cloneNotebook(noteId, $scope.note.notename);
      }
  };

  vm.handleNameEnter = function(){
    angular.element('#noteNameModal').modal('toggle');
    vm.createNote();
  };

  vm.preVisible = function(clone) {
    $scope.note.notename = vm.newNoteName();
    vm.clone = clone;
    $scope.$apply();
  };

  vm.newNoteName = function () {
    var newCount = 1;
    angular.forEach(vm.notes.list, function (noteName) {
      noteName = noteName.name;
      if (noteName.match(/^Untitled Note [0-9]*$/)) {
        var lastCount = noteName.substr(14) * 1;
        if (newCount <= lastCount) {
          newCount = lastCount + 1;
        }
      }
    });
    return 'Untitled Note ' + newCount;
  };

});
