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

angular.module('zeppelinWebApp').service('websocketMsgSrv', WebsocketMessageService)

function WebsocketMessageService ($rootScope, websocketEvents) {
  'ngInject'

  return {

    getHomeNote: function () {
      websocketEvents.sendNewEvent({op: 'GET_HOME_NOTE'})
    },

    createNotebook: function (noteName, defaultInterpreterId) {
      websocketEvents.sendNewEvent({
        op: 'NEW_NOTE',
        data: {
          name: noteName,
          defaultInterpreterId: defaultInterpreterId
        }
      })
    },

    moveNoteToTrash: function (noteId) {
      websocketEvents.sendNewEvent({op: 'MOVE_NOTE_TO_TRASH', data: {id: noteId}})
    },

    moveFolderToTrash: function (folderId) {
      websocketEvents.sendNewEvent({op: 'MOVE_FOLDER_TO_TRASH', data: {id: folderId}})
    },

    restoreNote: function (noteId) {
      websocketEvents.sendNewEvent({op: 'RESTORE_NOTE', data: {id: noteId}})
    },

    restoreFolder: function (folderId) {
      websocketEvents.sendNewEvent({op: 'RESTORE_FOLDER', data: {id: folderId}})
    },

    restoreAll: function () {
      websocketEvents.sendNewEvent({op: 'RESTORE_ALL'})
    },

    deleteNote: function (noteId) {
      websocketEvents.sendNewEvent({op: 'DEL_NOTE', data: {id: noteId}})
    },

    removeFolder: function (folderId) {
      websocketEvents.sendNewEvent({op: 'REMOVE_FOLDER', data: {id: folderId}})
    },

    emptyTrash: function () {
      websocketEvents.sendNewEvent({op: 'EMPTY_TRASH'})
    },

    cloneNote: function (noteIdToClone, newNoteName) {
      websocketEvents.sendNewEvent({op: 'CLONE_NOTE', data: {id: noteIdToClone, name: newNoteName}})
    },

    getNoteList: function () {
      websocketEvents.sendNewEvent({op: 'LIST_NOTES'})
    },

    reloadAllNotesFromRepo: function () {
      websocketEvents.sendNewEvent({op: 'RELOAD_NOTES_FROM_REPO'})
    },

    getNote: function (noteId) {
      websocketEvents.sendNewEvent({op: 'GET_NOTE', data: {id: noteId}})
    },

    updateNote: function (noteId, noteName, noteConfig) {
      websocketEvents.sendNewEvent({op: 'NOTE_UPDATE', data: {id: noteId, name: noteName, config: noteConfig}})
    },

    updatePersonalizedMode: function (noteId, modeValue) {
      websocketEvents.sendNewEvent({op: 'UPDATE_PERSONALIZED_MODE', data: {id: noteId, personalized: modeValue}})
    },

    renameNote: function (noteId, noteName) {
      websocketEvents.sendNewEvent({op: 'NOTE_RENAME', data: {id: noteId, name: noteName}})
    },

    renameFolder: function (folderId, folderName) {
      websocketEvents.sendNewEvent({op: 'FOLDER_RENAME', data: {id: folderId, name: folderName}})
    },

    moveParagraph: function (paragraphId, newIndex) {
      websocketEvents.sendNewEvent({op: 'MOVE_PARAGRAPH', data: {id: paragraphId, index: newIndex}})
    },

    insertParagraph: function (newIndex) {
      websocketEvents.sendNewEvent({op: 'INSERT_PARAGRAPH', data: {index: newIndex}})
    },

    copyParagraph: function (newIndex, paragraphTitle, paragraphData,
                            paragraphConfig, paragraphParams) {
      websocketEvents.sendNewEvent({
        op: 'COPY_PARAGRAPH',
        data: {
          index: newIndex,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams
        }
      })
    },

    updateAngularObject: function (noteId, paragraphId, name, value, interpreterGroupId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_UPDATED',
        data: {
          noteId: noteId,
          paragraphId: paragraphId,
          name: name,
          value: value,
          interpreterGroupId: interpreterGroupId
        }
      })
    },

    clientBindAngularObject: function (noteId, name, value, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_BIND',
        data: {
          noteId: noteId,
          name: name,
          value: value,
          paragraphId: paragraphId
        }
      })
    },

    clientUnbindAngularObject: function (noteId, name, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_UNBIND',
        data: {
          noteId: noteId,
          name: name,
          paragraphId: paragraphId
        }
      })
    },

    cancelParagraphRun: function (paragraphId) {
      websocketEvents.sendNewEvent({op: 'CANCEL_PARAGRAPH', data: {id: paragraphId}})
    },

    paragraphExecutedBySpell: function (paragraphId, paragraphTitle,
                                        paragraphText, paragraphResultsMsg,
                                        paragraphStatus, paragraphErrorMessage,
                                        paragraphConfig, paragraphParams,
                                        paragraphDateStarted, paragraphDateFinished) {
      websocketEvents.sendNewEvent({
        op: 'PARAGRAPH_EXECUTED_BY_SPELL',
        data: {
          id: paragraphId,
          title: paragraphTitle,
          paragraph: paragraphText,
          results: {
            code: paragraphStatus,
            msg: paragraphResultsMsg.map(dataWithType => {
              let serializedData = dataWithType.data
              return { type: dataWithType.type, data: serializedData, }
            })
          },
          status: paragraphStatus,
          errorMessage: paragraphErrorMessage,
          config: paragraphConfig,
          params: paragraphParams,
          dateStarted: paragraphDateStarted,
          dateFinished: paragraphDateFinished,
        }
      })
    },

    runParagraph: function (paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
      websocketEvents.sendNewEvent({
        op: 'RUN_PARAGRAPH',
        data: {
          id: paragraphId,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams
        }
      })
    },

    runAllParagraphs: function (noteId, paragraphs) {
      websocketEvents.sendNewEvent({
        op: 'RUN_ALL_PARAGRAPHS',
        data: {
          noteId: noteId,
          paragraphs: JSON.stringify(paragraphs)
        }
      })
    },

    removeParagraph: function (paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_REMOVE', data: {id: paragraphId}})
    },

    clearParagraphOutput: function (paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_OUTPUT', data: {id: paragraphId}})
    },

    clearAllParagraphOutput: function (noteId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_ALL_OUTPUT', data: {id: noteId}})
    },

    completion: function (paragraphId, buf, cursor) {
      websocketEvents.sendNewEvent({
        op: 'COMPLETION',
        data: {
          id: paragraphId,
          buf: buf,
          cursor: cursor
        }
      })
    },

    commitParagraph: function (paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams, noteId) {
      return websocketEvents.sendNewEvent({
        op: 'COMMIT_PARAGRAPH',
        data: {
          id: paragraphId,
          noteId: noteId,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams
        }
      })
    },

    importNote: function (note) {
      websocketEvents.sendNewEvent({
        op: 'IMPORT_NOTE',
        data: {
          note: note
        }
      })
    },

    checkpointNote: function (noteId, commitMessage) {
      websocketEvents.sendNewEvent({
        op: 'CHECKPOINT_NOTE',
        data: {
          noteId: noteId,
          commitMessage: commitMessage
        }
      })
    },

    setNoteRevision: function (noteId, revisionId) {
      websocketEvents.sendNewEvent({
        op: 'SET_NOTE_REVISION',
        data: {
          noteId: noteId,
          revisionId: revisionId
        }
      })
    },

    listRevisionHistory: function (noteId) {
      websocketEvents.sendNewEvent({
        op: 'LIST_REVISION_HISTORY',
        data: {
          noteId: noteId
        }
      })
    },

    getNoteByRevision: function (noteId, revisionId) {
      websocketEvents.sendNewEvent({
        op: 'NOTE_REVISION',
        data: {
          noteId: noteId,
          revisionId: revisionId
        }
      })
    },

    getEditorSetting: function (paragraphId, replName) {
      websocketEvents.sendNewEvent({
        op: 'EDITOR_SETTING',
        data: {
          paragraphId: paragraphId,
          magic: replName
        }
      })
    },

    isConnected: function () {
      return websocketEvents.isConnected()
    },

    getJobs: function () {
      websocketEvents.sendNewEvent({op: 'LIST_NOTE_JOBS'})
    },

    disconnectJobEvent: function () {
      websocketEvents.sendNewEvent({op: 'UNSUBSCRIBE_UPDATE_NOTE_JOBS'})
    },

    getUpdateNoteJobsList: function (lastUpdateServerUnixTime) {
      websocketEvents.sendNewEvent(
        {op: 'LIST_UPDATE_NOTE_JOBS', data: {lastUpdateUnixTime: lastUpdateServerUnixTime * 1}}
      )
    },

    getInterpreterBindings: function (noteId) {
      websocketEvents.sendNewEvent({op: 'GET_INTERPRETER_BINDINGS', data: {noteId: noteId}})
    },

    saveInterpreterBindings: function (noteId, selectedSettingIds) {
      websocketEvents.sendNewEvent({op: 'SAVE_INTERPRETER_BINDINGS',
        data: {noteId: noteId, selectedSettingIds: selectedSettingIds}})
    },

    listConfigurations: function () {
      websocketEvents.sendNewEvent({op: 'LIST_CONFIGURATIONS'})
    },

    getInterpreterSettings: function () {
      websocketEvents.sendNewEvent({op: 'GET_INTERPRETER_SETTINGS'})
    },

  }
}
