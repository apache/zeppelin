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

angular.module('zeppelinWebApp').service('noteActionService', noteActionService)

function noteActionService(websocketMsgSrv, $location, noteRenameService, noteListFactory) {
  'ngInject'

  this.moveNoteToTrash = function (noteId, redirectToHome) {
    BootstrapDialog.confirm({
      closable: true,
      title: 'Move this note to trash?',
      message: 'This note will be moved to <strong>trash</strong>.',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.moveNoteToTrash(noteId)
          if (redirectToHome) {
            $location.path('/')
          }
        }
      }
    })
  }

  this.moveFolderToTrash = function (folderId) {
    BootstrapDialog.confirm({
      closable: true,
      title: 'Move this folder to trash?',
      message: 'This folder will be moved to <strong>trash</strong>.',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.moveFolderToTrash(folderId)
        }
      }
    })
  }

  this.removeNote = function (noteId, redirectToHome) {
    BootstrapDialog.confirm({
      type: BootstrapDialog.TYPE_WARNING,
      closable: true,
      title: 'WARNING! This note will be removed permanently',
      message: 'This cannot be undone. Are you sure?',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.deleteNote(noteId)
          if (redirectToHome) {
            $location.path('/')
          }
        }
      }
    })
  }

  this.removeFolder = function (folderId) {
    BootstrapDialog.confirm({
      type: BootstrapDialog.TYPE_WARNING,
      closable: true,
      title: 'WARNING! This folder will be removed permanently',
      message: 'This cannot be undone. Are you sure?',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.removeFolder(folderId)
        }
      }
    })
  }

  this.restoreAll = function () {
    BootstrapDialog.confirm({
      closable: true,
      title: 'Are you sure want to restore all notes in the trash?',
      message: 'Folders and notes in the trash will be ' +
      '<strong>merged</strong> into their original position.',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.restoreAll()
        }
      }
    })
  }

  this.emptyTrash = function () {
    BootstrapDialog.confirm({
      type: BootstrapDialog.TYPE_WARNING,
      closable: true,
      title: 'WARNING! Notes under trash will be removed permanently',
      message: 'This cannot be undone. Are you sure?',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.emptyTrash()
        }
      }
    })
  }

  this.clearAllParagraphOutput = function (noteId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to clear all output?',
      callback: function (result) {
        if (result) {
          websocketMsgSrv.clearAllParagraphOutput(noteId)
        }
      }
    })
  }

  this.renameNote = function (noteId, notePath) {
    noteRenameService.openRenameModal({
      title: 'Rename note',
      oldName: notePath,
      callback: function (newName) {
        websocketMsgSrv.renameNote(noteId, newName)
      }
    })
  }

  this.renameFolder = function (folderId) {
    noteRenameService.openRenameModal({
      title: 'Rename folder',
      oldName: folderId,
      callback: function (newName) {
        let newFolderId = normalizeFolderId(newName)
        if (_.has(noteListFactory.flatFolderMap, newFolderId)) {
          BootstrapDialog.confirm({
            type: BootstrapDialog.TYPE_WARNING,
            closable: true,
            title: 'WARNING! The folder will be MERGED',
            message: 'The folder will be merged into <strong>' + newFolderId + '</strong>. Are you sure?',
            callback: function (result) {
              if (result) {
                websocketMsgSrv.renameFolder(folderId, newFolderId)
              }
            }
          })
        } else {
          websocketMsgSrv.renameFolder(folderId, newFolderId)
        }
      }
    })
  }

  function normalizeFolderId (folderId) {
    folderId = folderId.trim()

    while (folderId.indexOf('\\') > -1) {
      folderId = folderId.replace('\\', '/')
    }

    while (folderId.indexOf('///') > -1) {
      folderId = folderId.replace('///', '/')
    }

    folderId = folderId.replace('//', '/')

    if (folderId === '/') {
      return '/'
    }

    if (folderId[0] === '/') {
      folderId = folderId.substring(1)
    }

    if (folderId.slice(-1) === '/') {
      folderId = folderId.slice(0, -1)
    }

    return folderId
  }
}
