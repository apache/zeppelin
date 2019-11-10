import { EventBus } from '@/services/event-bus'

import wsHelper from '@/services/ws-helper'
import wsFactory from '@/services/ws-factory'

export default {
  setStore (store) {
    this.store = store
  },

  reloadList () {
    wsHelper.getConn().send({ op: 'LIST_NOTES' })
  },

  getFileName (path) {
    return path.substr(path.lastIndexOf('/') + 1)
  },

  reloadNote (noteId) {
    wsFactory.getConn(noteId).send({
      op: 'GET_NOTE',
      data: {
        id: noteId
      }
    })
  },

  showCreateModal () {
    EventBus.$emit('showCreateNoteDialog', true)
  },

  create (params) {
    wsHelper.getConn().send({
      op: 'NEW_NOTE',
      data: {
        name: params.name,
        defaultInterpreterGroup: params.defaultInterpreter
      }
    })

    // Reload the left sidebar
    this.reloadList()

    // Pending - open the note after create
  },

  rename (params) {
    wsHelper.getConn().send({
      op: 'NOTE_RENAME',
      data: {
        id: params.sourceNoteId,
        name: params.newNoteName
      }
    })

    // Reload the left sidebar will happen automatically as it will return the full list as the response
  },

  open (note) {
    wsFactory.initNoteConnection(note.id, this.store)

    note.name = this.getFileName(note.path)
    note.type = 'note'
    this.store.dispatch('addTab', note)
  },

  showCloneModal (id) {
    EventBus.$emit('showCloneNoteDialog', id)
  },

  clone (params) {
    wsFactory.getConn(params.sourceNoteId).send({
      op: 'CLONE_NOTE',
      data: {
        id: params.sourceNoteId,
        name: params.newNoteName
      }
    })

    // Reload the left sidebar
    this.reloadList()

    // Pending - open the note after clone
  },

  showImportModal () {
    EventBus.$emit('showImportNoteDialog', true)
  },

  importJSON (note) {
    wsHelper.getConn().send({
      op: 'IMPORT_NOTE',
      data: {
        note: note
      }
    })
  },

  exportJSON (note) {
    let exportObj = note
    let exportName = note.id
    // Pending - check huge data size
    // Pending - check browser compatibility
    var dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(exportObj))
    var downloadAnchorNode = document.createElement('a')
    downloadAnchorNode.setAttribute('href', dataStr)
    downloadAnchorNode.setAttribute('download', exportName + '.json')
    document.body.appendChild(downloadAnchorNode) // required for firefox
    downloadAnchorNode.click()
    downloadAnchorNode.remove()
  },

  runAll (noteId) {

  },

  clearAllOutputs (noteId) {
    wsFactory.getConn(noteId).send({
      op: 'PARAGRAPH_CLEAR_ALL_OUTPUT',
      data: {
        id: noteId
      }
    })
  },

  moveToTrash (noteId) {
    wsHelper.getConn().send({
      op: 'MOVE_NOTE_TO_TRASH',
      data: {
        id: noteId
      }
    })

    // Remove the tab
    let currentTab = this.store.state.TabManagerStore.currentTab
    if (currentTab && currentTab.id === noteId) {
      this.store.dispatch('removeTab', this.store.state.TabManagerStore.currentTab)
    }
  },

  deletePermanently (noteId) {
    wsHelper.getConn().send({
      op: 'DEL_NOTE',
      data: {
        id: noteId
      }
    })
  },

  restore (noteId) {
    wsHelper.getConn().send({
      op: 'RESTORE_NOTE',
      data: {
        id: noteId
      }
    })
  }
}
