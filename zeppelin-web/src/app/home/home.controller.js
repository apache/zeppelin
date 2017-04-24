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

angular.module('zeppelinWebApp').controller('HomeCtrl', HomeCtrl)

function HomeCtrl ($scope, noteListDataFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv,
                  ngToast, noteActionSrv, TRASH_FOLDER_ID) {
  'ngInject'

  ngToast.dismiss()
  let vm = this
  vm.notes = noteListDataFactory
  vm.websocketMsgSrv = websocketMsgSrv
  vm.arrayOrderingSrv = arrayOrderingSrv

  vm.notebookHome = false
  vm.noteCustomHome = true
  if ($rootScope.ticket !== undefined) {
    vm.staticHome = false
  } else {
    vm.staticHome = true
  }

  $scope.isReloading = false
  $scope.TRASH_FOLDER_ID = TRASH_FOLDER_ID
  $scope.query = {q: ''}

  $scope.initHome = function () {
    websocketMsgSrv.getHomeNote()
    vm.noteCustomHome = false
  }

  $scope.reloadNoteList = function () {
    websocketMsgSrv.reloadAllNotesFromRepo()
    $scope.isReloadingNotes = true
  }

  $scope.toggleFolderNode = function (node) {
    node.hidden = !node.hidden
  }

  angular.element('#loginModal').on('hidden.bs.modal', function (e) {
    $rootScope.$broadcast('initLoginValues')
  })

  /*
   ** $scope.$on functions below
   */

  $scope.$on('setNoteMenu', function (event, notes) {
    $scope.isReloadingNotes = false
  })

  $scope.$on('setNoteContent', function (event, note) {
    if (vm.noteCustomHome) {
      return
    }

    if (note) {
      vm.note = note

      // initialize look And Feel
      $rootScope.$broadcast('setLookAndFeel', 'home')

      // make it read only
      vm.viewOnly = true

      vm.notebookHome = true
      vm.staticHome = false
    } else {
      vm.staticHome = true
      vm.notebookHome = false
    }
  })

  $scope.renameNote = function (nodeId, nodePath) {
    noteActionSrv.renameNote(nodeId, nodePath)
  }

  $scope.moveNoteToTrash = function (noteId) {
    noteActionSrv.moveNoteToTrash(noteId, false)
  }

  $scope.moveFolderToTrash = function (folderId) {
    noteActionSrv.moveFolderToTrash(folderId)
  }

  $scope.restoreNote = function (noteId) {
    websocketMsgSrv.restoreNote(noteId)
  }

  $scope.restoreFolder = function (folderId) {
    websocketMsgSrv.restoreFolder(folderId)
  }

  $scope.restoreAll = function () {
    noteActionSrv.restoreAll()
  }

  $scope.renameFolder = function (node) {
    noteActionSrv.renameFolder(node.id)
  }

  $scope.removeNote = function (noteId) {
    noteActionSrv.removeNote(noteId, false)
  }

  $scope.removeFolder = function (folderId) {
    noteActionSrv.removeFolder(folderId)
  }

  $scope.emptyTrash = function () {
    noteActionSrv.emptyTrash()
  }

  $scope.clearAllParagraphOutput = function (noteId) {
    noteActionSrv.clearAllParagraphOutput(noteId)
  }

  $scope.isFilterNote = function (note) {
    if (!$scope.query.q) {
      return true
    }

    let noteName = note.name
    if (noteName.toLowerCase().indexOf($scope.query.q.toLowerCase()) > -1) {
      return true
    }
    return false
  }

  $scope.getNoteName = function (note) {
    return arrayOrderingSrv.getNoteName(note)
  }

  $scope.noteComparator = function (note1, note2) {
    return arrayOrderingSrv.noteComparator(note1, note2)
  }
}
