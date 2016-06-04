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
      websocketEvents.sendNewEvent({op: 'GET_HOME_NOTE'}, 'notebookServer');
    },

    createNotebook: function(noteName) {
      websocketEvents.sendNewEvent({op: 'NEW_NOTE',data: {name: noteName}}, 'notebookServer');
    },

    deleteNotebook: function(noteId) {
      websocketEvents.sendNewEvent({op: 'DEL_NOTE', data: {id: noteId}}, 'notebookServer');
    },

    cloneNotebook: function(noteIdToClone, newNoteName ) {
      websocketEvents.sendNewEvent({op: 'CLONE_NOTE', data: {id: noteIdToClone, name: newNoteName}}, 'notebookServer');
    },

    getNotebookList: function() {
      websocketEvents.sendNewEvent({op: 'LIST_NOTES'}, 'notebookServer');
    },

    reloadAllNotesFromRepo: function() {
      websocketEvents.sendNewEvent({op: 'RELOAD_NOTES_FROM_REPO'}, 'notebookServer');
    },

    getNotebook: function(noteId) {
      websocketEvents.sendNewEvent({op: 'GET_NOTE', data: {id: noteId}}, 'notebookServer');
    },

    updateNotebook: function(noteId, noteName, noteConfig) {
      websocketEvents.sendNewEvent({op: 'NOTE_UPDATE', data: {id: noteId, name: noteName, config : noteConfig}}, 'notebookServer');
    },

    moveParagraph: function(paragraphId, newIndex) {
      websocketEvents.sendNewEvent({ op: 'MOVE_PARAGRAPH', data : {id: paragraphId, index: newIndex}}, 'notebookServer');
    },

    insertParagraph: function(newIndex) {
      websocketEvents.sendNewEvent({ op: 'INSERT_PARAGRAPH', data : {index: newIndex}}, 'notebookServer');
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
      }, 'notebookServer');
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
      }, 'notebookServer');
    },

    clientUnbindAngularObject: function(noteId, name, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_UNBIND',
        data: {
          noteId: noteId,
          name: name,
          paragraphId: paragraphId
        }
      }, 'notebookServer');
    },

    cancelParagraphRun: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'CANCEL_PARAGRAPH', data: {id: paragraphId}}, 'notebookServer');
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
      }, 'notebookServer');
    },

    removeParagraph: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_REMOVE', data: {id: paragraphId}}, 'notebookServer');
    },

    clearParagraphOutput: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_OUTPUT', data: {id: paragraphId}}, 'notebookServer');
    },

    completion: function(paragraphId, buf, cursor) {
      websocketEvents.sendNewEvent({
        op : 'COMPLETION',
        data : {
          id : paragraphId,
          buf : buf,
          cursor : cursor
        }
      }, 'notebookServer');
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
      }, 'notebookServer');
    },

    importNotebook: function(notebook) {
      websocketEvents.sendNewEvent({
        op: 'IMPORT_NOTE',
        data: {
          notebook: notebook
        }
      }, 'notebookServer');
    },

    checkpointNotebook: function(noteId, commitMessage) {
      websocketEvents.sendNewEvent({
        op: 'CHECKPOINT_NOTEBOOK',
        data: {
          noteId: noteId,
          commitMessage: commitMessage
        }
      }, 'notebookServer');
    },

    isConnected: function(){
      return websocketEvents.isConnected();
    },

    getNotebookJobsList: function() {
      websocketEvents.sendNewEvent({op: 'LIST_NOTEBOOK_JOBS'});
    },

    getUpdateNotebookJobsList: function(lastUpdateServerUnixTime) {
      websocketEvents.sendNewEvent(
        {op: 'LIST_UPDATE_NOTEBOOK_JOBS', data : {lastUpdateUnixTime : lastUpdateServerUnixTime*1}},
        'jobManagerServer'
      );
    },

    unsubscribeJobManager: function() {
      websocketEvents.sendNewEvent({op: 'UNSUBSCRIBE_JOBMANAGER'});
    },

  };

});
