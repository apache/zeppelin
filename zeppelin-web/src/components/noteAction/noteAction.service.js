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

  angular.module('zeppelinWebApp').service('noteActionSrv', noteActionSrv);

  noteActionSrv.$inject = ['websocketMsgSrv', '$location', 'renameSrv', 'noteListDataFactory'];

  function noteActionSrv(websocketMsgSrv, $location, renameSrv, noteListDataFactory) {
    this.removeNote = function(noteId, redirectToHome) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to delete this note?',
        callback: function(result) {
          if (result) {
            websocketMsgSrv.deleteNote(noteId);
            if (redirectToHome) {
              $location.path('/');
            }
          }
        }
      });
    };

    this.clearAllParagraphOutput = function(noteId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to clear all output?',
        callback: function(result) {
          if (result) {
            websocketMsgSrv.clearAllParagraphOutput(noteId);
          }
        }
      });
    };

    this.renameNote = function(noteId, notePath) {
      renameSrv.openRenameModal({
        title: 'Rename note',
        oldName: notePath,
        callback: function(newName) {
          websocketMsgSrv.renameNote(noteId, newName);
        }
      });
    };

    this.renameFolder = function(folderId) {
      renameSrv.openRenameModal({
        title: 'Rename folder',
        oldName: folderId,
        callback: function(newName) {
          var newFolderId = normalizeFolderId(newName);
          if (_.has(noteListDataFactory.flatFolderMap, newFolderId)) {
            BootstrapDialog.confirm({
              type: BootstrapDialog.TYPE_WARNING,
              closable: true,
              title: 'WARNING! The folder will be MERGED',
              message: 'The folder will be merged into <strong>' + newFolderId + '</strong>. Are you sure?',
              callback: function(result) {
                if (result) {
                  websocketMsgSrv.renameFolder(folderId, newFolderId);
                }
              }
            });
          } else {
            websocketMsgSrv.renameFolder(folderId, newFolderId);
          }
        }
      });
    };

    function normalizeFolderId(folderId) {
      folderId = folderId.trim();

      while (folderId.contains('\\')) {
        folderId = folderId.replace('\\', '/');
      }

      while (folderId.contains('///')) {
        folderId = folderId.replace('///', '/');
      }

      folderId = folderId.replace('//', '/');

      if (folderId === '/') {
        return '/';
      }

      if (folderId[0] === '/') {
        folderId = folderId.substring(1);
      }

      if (folderId.slice(-1) === '/') {
        folderId = folderId.slice(0, -1);
      }

      return folderId;
    }
  }
})();
