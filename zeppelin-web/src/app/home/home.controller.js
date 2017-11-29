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

function HomeCtrl ($scope, noteListFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv,
                  ngToast, noteActionService, TRASH_FOLDER_ID) {
  'ngInject'

  ngToast.dismiss()
  let vm = this
  vm.notes = noteListFactory
  vm.recentNotes = []
  vm.websocketMsgSrv = websocketMsgSrv
  vm.arrayOrderingSrv = arrayOrderingSrv
  vm.noteActionService = noteActionService
  vm.numberOfNotesDisplayed = window.innerHeight / 20

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
    $scope.initRecentList()
    vm.noteCustomHome = false
  }

  $scope.initRecentList = function() {
    if ($rootScope.ticket !== undefined) {
      websocketMsgSrv.getRecentNotes()
    }
  }

  $scope.reloadNoteList = function () {
    websocketMsgSrv.reloadAllNotesFromRepo()
    $scope.isReloadingNotes = true
  }

  $scope.toggleFolderNode = function (node) {
    node.hidden = !node.hidden
  }

  $scope.clearRecent = function() {
    websocketMsgSrv.clearRecent()
  }

  angular.element('#loginModal').on('hidden.bs.modal', function (e) {
    $rootScope.$broadcast('initLoginValues')
  })

  /*
   ** $scope.$on functions below
   */

  $scope.$on('updateRecentList', function(event, recentNotes) {
    vm.recentNotes = []
    for (let i = 0; i < recentNotes.length; ++i) {
      let note = recentNotes[i]
      note.isTrash = note.name
        ? note.name.split('/')[0] === TRASH_FOLDER_ID : false
      note.fromRecentList = true
      vm.recentNotes.push(note)
    }
    vm.recentNotes.reverse()
  })

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

  $scope.loadMoreNotes = function () {
    vm.numberOfNotesDisplayed += 10
  }

  $scope.renameNote = function (nodeId, nodePath) {
    vm.noteActionService.renameNote(nodeId, nodePath)
  }

  $scope.moveNoteToTrash = function (noteId) {
    vm.noteActionService.moveNoteToTrash(noteId, false)
  }

  $scope.moveFolderToTrash = function (folderId) {
    vm.noteActionService.moveFolderToTrash(folderId)
  }

  $scope.restoreNote = function (noteId) {
    websocketMsgSrv.restoreNote(noteId)
  }

  $scope.restoreFolder = function (folderId) {
    websocketMsgSrv.restoreFolder(folderId)
  }

  $scope.restoreAll = function () {
    vm.noteActionService.restoreAll()
  }

  $scope.renameFolder = function (node) {
    vm.noteActionService.renameFolder(node.id)
  }

  $scope.removeNote = function (noteId) {
    vm.noteActionService.removeNote(noteId, false)
  }

  $scope.removeFromRecent = function (noteId) {
    vm.noteActionService.removeFromRecent(noteId)
  }

  $scope.removeFolder = function (folderId) {
    vm.noteActionService.removeFolder(folderId)
  }

  $scope.emptyTrash = function () {
    vm.noteActionService.emptyTrash()
  }

  $scope.clearAllParagraphOutput = function (noteId) {
    vm.noteActionService.clearAllParagraphOutput(noteId)
  }

  $scope.isFilterNote = function (note) {
    if (!$scope.query.q || note.fromRecentList) {
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
