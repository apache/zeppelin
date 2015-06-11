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

angular.module('zeppelinWebApp').service('websocketMsgSrv', function($rootScope, websocketEvents) {

  this.createNotebook = function() {
    websocketEvents.sendNewEvent({op: 'NEW_NOTE'});
  };

  this.deleteNotebook = function(noteId) {
    websocketEvents.sendNewEvent({op: 'DEL_NOTE', data: {id: noteId}});
  };

  this.getNotebookList = function() {
    websocketEvents.sendNewEvent({op: 'LIST_NOTES'});
  };

  this.getNotebook = function(noteId) {
    websocketEvents.sendNewEvent({op: 'GET_NOTE', data: {id: noteId}});
  };

  this.updateNotebook = function(noteId, noteName, noteConfig) {
    websocketEvents.sendNewEvent({op: 'NOTE_UPDATE', data: {id: noteId, name: noteName, config : noteConfig}});
  };

  this.moveParagraph = function(paragraphId, newIndex) {
    websocketEvents.sendNewEvent({ op: 'MOVE_PARAGRAPH', data : {id: paragraphId, index: newIndex}});
  };

  this.insertParagraph = function(newIndex) {
    websocketEvents.sendNewEvent({ op: 'INSERT_PARAGRAPH', data : {index: newIndex}});
  };

  this.updateAngularObject = function(noteId, name, value, interpreterGroupId) {
    websocketEvents.sendNewEvent({
      op: 'ANGULAR_OBJECT_UPDATED',
      data: {
        noteId: noteId,
        name: name,
        value: value,
        interpreterGroupId: interpreterGroupId
      }
    });
  };

  this.cancelParagraphRun = function(paragraphId) {
    websocketEvents.sendNewEvent({op: 'CANCEL_PARAGRAPH', data: {id: paragraphId}});
  };

  this.runParagraph = function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
    websocketEvents.sendNewEvent({
      op: 'RUN_PARAGRAPH',
      data: {
        id: paragraphId,
        title: paragraphTitle,
        paragraph: paragraphData,
        config: paragraphConfig,
        params: paragraphParams
      }
    });
  };

  this.removeParagraph = function(paragraphId) {
    websocketEvents.sendNewEvent({op: 'PARAGRAPH_REMOVE', data: {id: paragraphId}});
  };

  this.completion = function(paragraphId, buf, cursor) {
    websocketEvents.sendNewEvent({
      op : 'COMPLETION',
      data : {
        id : paragraphId,
        buf : buf,
        cursor : cursor
      }
    });
  };

  this.commitParagraph = function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
    websocketEvents.sendNewEvent({
      op: 'COMMIT_PARAGRAPH',
      data: {
        id: paragraphId,
        title : paragraphTitle,
        paragraph: paragraphData,
        config: paragraphConfig,
        params: paragraphParams
      }
    });
  };

});
