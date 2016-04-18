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

  return {

    getHomeNotebook: function() {
      websocketEvents.sendNewEvent({op: 'GET_HOME_NOTE'});
    },

    createNotebook: function(noteName) {
      websocketEvents.sendNewEvent({op: 'NEW_NOTE',data: {name: noteName}});
    },

    deleteNotebook: function(noteId) {
      websocketEvents.sendNewEvent({op: 'DEL_NOTE', data: {id: noteId}});
    },

    cloneNotebook: function(noteIdToClone, newNoteName ) {
      websocketEvents.sendNewEvent({op: 'CLONE_NOTE', data: {id: noteIdToClone, name: newNoteName}});
    },

    getNotebookList: function() {
      websocketEvents.sendNewEvent({op: 'LIST_NOTES'});
    },

    reloadAllNotesFromRepo: function() {
      websocketEvents.sendNewEvent({op: 'RELOAD_NOTES_FROM_REPO'});
    },

    getNotebook: function(noteId) {
      websocketEvents.sendNewEvent({op: 'GET_NOTE', data: {id: noteId}});
    },

    updateNotebook: function(noteId, noteName, noteConfig) {
      websocketEvents.sendNewEvent({op: 'NOTE_UPDATE', data: {id: noteId, name: noteName, config : noteConfig}});
    },

    moveParagraph: function(paragraphId, newIndex) {
      websocketEvents.sendNewEvent({ op: 'MOVE_PARAGRAPH', data : {id: paragraphId, index: newIndex}});
    },

    insertParagraph: function(newIndex) {
      websocketEvents.sendNewEvent({ op: 'INSERT_PARAGRAPH', data : {index: newIndex}});
    },

    updateAngularObject: function(noteId, paragraphId, name, value, interpreterGroupId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_UPDATED',
        data: {
          noteId: noteId,
          paragraphId: paragraphId,
          name: name,
          value: value,
          interpreterGroupId: interpreterGroupId
        }
      });
    },

    clientBindAngularObject: function(noteId, name, value, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_BIND',
        data: {
          noteId: noteId,
          name: name,
          value: value,
          paragraphId: paragraphId
        }
      });
    },

    clientUnbindAngularObject: function(noteId, name, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_UNBIND',
        data: {
          noteId: noteId,
          name: name,
          paragraphId: paragraphId
        }
      });
    },

    cancelParagraphRun: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'CANCEL_PARAGRAPH', data: {id: paragraphId}});
    },

    runParagraph: function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
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
    },

    removeParagraph: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_REMOVE', data: {id: paragraphId}});
    },

    clearParagraphOutput: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_OUTPUT', data: {id: paragraphId}});
    },

    completion: function(paragraphId, buf, cursor) {
      websocketEvents.sendNewEvent({
        op : 'COMPLETION',
        data : {
          id : paragraphId,
          buf : buf,
          cursor : cursor
        }
      });
    },

    commitParagraph: function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
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
    },

    importNotebook: function(notebook) {
      websocketEvents.sendNewEvent({
        op: 'IMPORT_NOTE',
        data: {
          notebook: notebook
        }
      });
    },

    checkpointNotebook: function(noteId, commitMessage) {
      websocketEvents.sendNewEvent({
        op: 'CHECKPOINT_NOTEBOOK',
        data: {
          noteId: noteId,
          commitMessage: commitMessage
        }
      });
    },

    isConnected: function(){
      return websocketEvents.isConnected();
    }

  };

});
