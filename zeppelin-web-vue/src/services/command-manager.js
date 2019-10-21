import { EventBus } from '@/services/event-bus'
import notebookUtils from '@/services/notebook-utils'

export default {
  setupCommands (store) {
    notebookUtils.setStore(store)

    this.setupTabCommands(store)

    this.setupNoteCommands(store)
    this.setupParagraphCommands(store)
  },

  openSystemTabs (tabType, store) {
    let tabName = tabType
      .replace('-', ' ')
      .replace(/\w+/g, function (w) {
        return w[0].toUpperCase() + w.slice(1).toLowerCase()
      })

    let tab = {
      id: 'zeppelin-system-' + tabName,
      name: tabName,
      type: tabType,
      path: '/zeppelin-system/' + tabName
    }

    store.dispatch('addTab', tab)
  },

  setupTabCommands (store) {
    EventBus.$on('tabs', (command, args) => {
      // command is always open for now
      let tabType = args.type

      switch (tabType) {
        case 'interpreters':
        case 'configurations':
        case 'credentials':
        case 'notebook-repository':
          this.openSystemTabs(tabType, store)
          break
        case 'note':
          notebookUtils.open(args.note)
          break
      }
    })
  },

  setupNoteCommands (store) {
    EventBus.$on('note', (command, args) => {
      let isActiveNote = (store.state.TabManagerStore.currentTab &&
                              store.state.TabManagerStore.currentTab.type === 'note')

      if (!(isActiveNote || ['show-create', 'create', 'rename', 'show-import', 'import-json',
        'move-to-trash', 'restore-note', 'delete-permanently'].indexOf(command) !== -1)
      ) {
        return
      }
      let note = store.state.TabManagerStore.currentTab

      switch (command) {
        case 'show-create':
          notebookUtils.showCreateModal()
          break
        case 'create':
          notebookUtils.create(args)
          break
        case 'show-import':
          notebookUtils.showImportModal()
          break
        case 'import-json':
          notebookUtils.importJSON(args)
          break
        case 'rename':
          notebookUtils.rename(args)
          break
        case 'clear-output':
          notebookUtils.clearAllOutputs(note.id)
          break
        case 'run-all':
          notebookUtils.runAll(note.id)
          break
        case 'run-before':
          break
        case 'run-focused':
          break
        case 'run-after':
          break
        case 'save':
          break
        case 'export-json':
          notebookUtils.exportJSON(note)
          break
        case 'print':
          break
        case 'move-to-trash':
          notebookUtils.moveToTrash(args || (note && note.id))
          break
        case 'restore-note':
          notebookUtils.restore(args || (note && note.id))
          break
        case 'delete-permanently':
          notebookUtils.deletePermanently(args || (note && note.id))
          break
        case 'show-clone':
          notebookUtils.showCloneModal(note.id)
          break
        case 'clone':
          notebookUtils.clone(args)
          break
        case 'reload':
          notebookUtils.reloadNote(note.id)
          break
        case 'show-toc':
          break
        case 'show-version-control':
          break
        case 'show-notbeook-info':
          break
        case 'find-and-replace':
          break
        case 'manage-permissions':
          break
        case 'toggle-code':
          break
        case 'toggle-output':
          break
        case 'toggle-line-numbers':
          break
      }
    })
  },

  setupParagraphCommands (store) {
    EventBus.$on('paragraph', (command, args) => {
      let isActiveNote = (store.state.TabManagerStore.currentTab &&
                              store.state.TabManagerStore.currentTab.type === 'note')

      if (!isActiveNote) {
        return
      }
      // let noteId = store.state.TabManagerStore.currentTab.id
      // let activeParagraph ==
      // let paragraphId = ??

      switch (command) {
        case 'new-text':
          break
        case 'new-code':
          break
        case 'run':
          break
        case 'disable-run':
          break
        case 'enable-run':
          break
        case 'clone':
          break
        case 'clear-output':
          break
        case 'delete':
          break
        case 'copy-id':
          break
        case 'toggle-code':
          break
        case 'toggle-output':
          break
        case 'toggle-line-numbers':
          break
      }
    })
  }
}
