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

angular.module('zeppelinWebApp').controller('NotenameCtrl', function($scope, $rootScope, $routeParams, websocketMsgSrv,
                                                                     $location) {
  var vm = this;
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
    var generatedName = vm.generateName();
    $scope.note.notename = 'Note ' + generatedName;
    vm.clone = clone;
    $scope.$apply();
  };

  vm.generateName = function () {
    var DICTIONARY = [ '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
        'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R',
        'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' ];
    var randIndex, name = '';
    for (var i = 0; i < 9; i++) {
      randIndex = Math.floor(Math.random() * 32);
      name += DICTIONARY[randIndex];
    }
    return name;
  };

});
