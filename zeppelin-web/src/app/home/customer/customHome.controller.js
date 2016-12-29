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

(function() {

  angular.module('zeppelinWebApp').controller('CustomHomeCtrl', CustomHomeCtrl);

  CustomHomeCtrl.$inject = [
    '$scope',
    'noteListDataFactory',
    'websocketMsgSrv',
    '$rootScope',
    'arrayOrderingSrv',
    'noteActionSrv'
  ];

  function CustomHomeCtrl($scope, noteListDataFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv,
                    noteActionSrv) {
    var vm = this;
    vm.notes = noteListDataFactory;
    vm.websocketMsgSrv = websocketMsgSrv;
    vm.arrayOrderingSrv = arrayOrderingSrv;

    $scope.isReloading = false;

    $scope.reloadNoteList = function() {
      websocketMsgSrv.reloadAllNotesFromRepo();
      $scope.isReloadingNotes = true;
    };

    $scope.toggleFolderNode = function(node) {
      node.hidden = !node.hidden;
    };

    $scope.renameNote = function(node) {
      noteActionSrv.renameNote(node.id, node.path);
    };

    $scope.renameFolder = function(node) {
      noteActionSrv.renameFolder(node.id);
    };

    $scope.removeNote = function(noteId) {
      noteActionSrv.removeNote(noteId, false);
    };

    $scope.clearAllParagraphOutput = function(noteId) {
      noteActionSrv.clearAllParagraphOutput(noteId);
    };
  }
})();
