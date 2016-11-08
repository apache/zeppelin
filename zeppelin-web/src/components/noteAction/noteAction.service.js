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

  noteActionSrv.$inject = ['websocketMsgSrv', '$location'];

  function noteActionSrv(websocketMsgSrv, $location) {
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
  }
})();
