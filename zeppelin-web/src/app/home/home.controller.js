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

  angular.module('zeppelinWebApp').controller('HomeCtrl', HomeCtrl);

  HomeCtrl.$inject = [
    '$scope',
    'noteListDataFactory',
    'websocketMsgSrv',
    '$rootScope',
    'arrayOrderingSrv',
    'ngToast',
    'noteActionSrv'
  ];

  function HomeCtrl($scope, noteListDataFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv,
                    ngToast, noteActionSrv) {
    ngToast.dismiss();
    var vm = this;
    vm.notes = noteListDataFactory;
    vm.websocketMsgSrv = websocketMsgSrv;
    vm.arrayOrderingSrv = arrayOrderingSrv;

    vm.notebookHome = false;
    if ($rootScope.ticket !== undefined) {
      vm.staticHome = false;
    } else {
      vm.staticHome = true;
    }

    $scope.isReloading = false;

    var initHome = function() {
      websocketMsgSrv.getHomeNote();
    };

    initHome();

    $scope.reloadNoteList = function() {
      websocketMsgSrv.reloadAllNotesFromRepo();
      $scope.isReloadingNotes = true;
    };

    $scope.toggleFolderNode = function(node) {
      node.hidden = !node.hidden;
    };

    angular.element('#loginModal').on('hidden.bs.modal', function(e) {
      $rootScope.$broadcast('initLoginValues');
    });

    /*
    ** $scope.$on functions below
    */

    $scope.$on('setNoteMenu', function(event, notes) {
      $scope.isReloadingNotes = false;
    });

    $scope.$on('setNoteContent', function(event, note) {
      if (note) {
        vm.note = note;

        // initialize look And Feel
        $rootScope.$broadcast('setLookAndFeel', 'home');

        // make it read only
        vm.viewOnly = true;

        vm.notebookHome = true;
        vm.staticHome = false;
      } else {
        vm.staticHome = true;
        vm.notebookHome = false;
      }
    });

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
