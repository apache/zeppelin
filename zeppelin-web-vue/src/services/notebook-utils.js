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

  reloadNotebook (notebookId) {
    wsFactory.getConn(notebookId).send({
      op: 'GET_NOTE',
      data: {
        id: notebookId
      }
    })
  },

  showCreateModal () {
    EventBus.$emit('showCreateNotebookDialog', true)
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

    // Pending - open the notebook after create
  },

  open (notebook) {
    wsFactory.initNotebookConnection(notebook.id, this.store)

    notebook.name = this.getFileName(notebook.path)
    notebook.type = 'notebook'
    this.store.dispatch('addTab', notebook)
  },

  showCloneModal (id) {
    EventBus.$emit('showCloneNotebookDialog', id)
  },

  clone (params) {
    wsFactory.getConn(params.sourceNotebookId).send({
      op: 'CLONE_NOTE',
      data: {
        id: params.sourceNotebookId,
        name: params.newNotebookName
      }
    })

    // Reload the left sidebar
    this.reloadList()

    // Pending - open the notebook after clone
  },

  showImportModal () {
    EventBus.$emit('showImportNotebookDialog', true)
  },

  importJSON (notebook) {
    wsHelper.getConn().send({
      op: 'IMPORT_NOTE',
      data: {
        note: notebook
      }
    })
  },

  exportJSON (notebook) {
    let exportObj = notebook
    let exportName = notebook.id
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

  clearAllOutputs (notebookId) {
    wsFactory.getConn(notebookId).send({
      op: 'PARAGRAPH_CLEAR_ALL_OUTPUT',
      data: {
        id: notebookId
      }
    })
  },

  deleteTemporary (notebookId) {
    wsHelper.getConn().send({
      op: 'MOVE_NOTE_TO_TRASH',
      data: {
        id: notebookId
      }
    })

    // Remove the tab
    this.store.dispatch('removeTab', this.store.state.TabManagerStore.currentTab)

    // Reload the notebook list
    this.reloadList()
  },

  deletePermanently (notebookId) {
    wsHelper.getConn().send({
      op: 'DEL_NOTE',
      data: {
        id: notebookId
      }
    })

    // Reload the notebook list
    this.reloadList()
  },

  restore (notebookId) {
    wsHelper.getConn().send({
      op: 'RESTORE_NOTE',
      data: {
        id: notebookId
      }
    })

    // Reload the notebook list
    this.reloadList()
  }
}
