import Vue from 'vue'

export default {
  state: {
    notebooks: [],
    notebook: null,
    paragraphs: null,
    isListLoading: true,
    nav: {
      isOpen: true
    },
    activeParagraph: null
  },
  getters: {
    getNotebook: (state) => (notebookId) => {
      let filteredNotebook = state.notebooks.find(n => n.id === notebookId)
      return filteredNotebook
    },
    getAllNotebooks: (state) => {
      return state.notebooks
    },
    getActiveParagraph: (state) => {
      return state.activeParagraph
    },
    getNavBarState: (state) => {
      return state.nav
    },
    getParagraphById: (state) => (id, notebookId) => {
      let filteredNotebook = state.notebooks.find(n => n.id === notebookId)
      return filteredNotebook.paragraphs.find(p => p.id === id)
    },
    getAllParagraphs: (state, notebookId) => {
      let filteredNotebook = state.notebooks.find(n => n.id === notebookId)
      return filteredNotebook && filteredNotebook.paragraphs
    },
    isParagraphLoading: (state) => (id, notebookId) => {
      let filteredNotebook = state.notebooks.find(n => n.id === notebookId)

      let paragraph = filteredNotebook && filteredNotebook.paragraphs.find(p => p.id === id)
      let status = paragraph.status.toLowerCase()

      if (status === 'pending' || status === 'running') {
        return true
      }

      return false
    },
    isParagraphCreating: (state) => (id, notebookId) => {
      let filteredNotebook = state.notebooks.find(n => n.id === notebookId)
      let paragraph = filteredNotebook && filteredNotebook.paragraphs.find(p => p.id === id)
      let status = paragraph && paragraph.status.toLowerCase()

      if (status === 'init') {
        return true
      }

      return false
    }
  },
  mutations: {
    mutateActiveParagraph (state, data) {
      state.activeParagraph = data
    },
    mutateNavBar (state, data) {
      state.nav = data
    },
    mutateNotes (state, data) {
      state.isListLoading = false
      state.notebooks = data.notes
    },
    mutateNotebook (state, noteObj) {
      let index = state.notebooks.map(function (n) { return n.id }).indexOf(noteObj.note.id)
      let newObj = Object.assign(state.notebooks[index], noteObj.note)
      Vue.set(state.notebooks, index, newObj)

      // set paragraphs state
      // state.activeParagraph = paragraphs[0]
      return state
    },
    addParagraph (state, data) {
      let filteredNotebook = state.notebooks.find(n => n.id === data.notebookId)
      let paragraphs = filteredNotebook && filteredNotebook.paragraphs

      paragraphs.splice(data.index, 0, data.paragraph)
      filteredNotebook.paragraphs = paragraphs
    },
    removeParagraph (state, data) {
      let filteredNotebook = state.notebooks.find(n => n.id === data.notebookId)
      let paragraphs = filteredNotebook && filteredNotebook.paragraphs
      let index = paragraphs.map(function (p) { return p.id }).indexOf(data.id)
      paragraphs.splice(index, 1)
      filteredNotebook.paragraphs = paragraphs
    },
    mutateParagraphs (state, data) {
      let filteredNotebook = state.notebooks.find(n => n.id === data.notebookId)
      let paragraphs = filteredNotebook && filteredNotebook.paragraphs

      let id = data.paragraph.id
      let index = paragraphs.map(function (p) { return p.id }).indexOf(id)

      let paragraph = paragraphs[index]

      // if paragraph do not exist
      // it means it was not added from this context
      // some one else has opened the same notebook and
      // is adding paragraph

      if (!paragraph && data.index) {
        Vue.set(filteredNotebook.paragraphs, data.index, data.paragraph)
        return
      }

      if (!paragraph.forceEditorShow) {
        // Vue.set(state.paragraphs, index, data.paragraph)
      }
    },
    mutateParagraphsWithNewProps (state, data) {
      let id = data.id
      let filteredNotebook = state.notebooks.find(n => n.id === data.notebookId)
      let paragraph = filteredNotebook.paragraphs.find(p => p.id === id)

      Vue.set(paragraph, data.prop.name, data.prop.value)
      // Vue.set(state.paragraphs, index, paragraph)
    },
    moveParagraph (state, data) {
      let newIndex = data.newIndex
      let index = data.index
      let paragraphs = state.paragraphs

      // remove old index
      paragraphs.splice(index, 1)
      paragraphs.splice(newIndex, 0, data.paragraph)

      state.paragraphs = paragraphs
    },
    setParagraphOutput (state, data) {
      let paraId = data.paragraphId
      let filteredNotebook = state.notebooks.find(n => n.id === data.notebookId)
      let paragraph = filteredNotebook.paragraphs.find(p => p.id === paraId)
      // let paraIndex = state.paragraphs.map(function (p) { return p.id }).indexOf(paraId)

      if (paragraph.results) {
        paragraph.results.msg[0].data = data.data
      } else {
        Vue.set(paragraph, 'results', {
          msg: [
            {
              data: data.data,
              type: 'TEXT'
            }
          ]
        })
      }
    }
  },
  actions: {
    moveParagraphByIndex (context, data) {
      context.commit('moveParagraph', data)
    },
    setActiveParagraph (context, data) {
      context.commit('mutateActiveParagraph', data)
    },
    setNavBar (context, data) {
      context.commit('mutateNavBar', data)
    },
    setNoteMenu (context, data) {
      context.commit('mutateNotes', data)
    },
    setNotebookContent (context, data) {
      context.commit('mutateNotebook', data)
    },
    setParagraph (context, data) {
      context.commit('mutateParagraphs', data)
    },
    setParagraphOutput (context, data) {
      context.commit('setParagraphOutput', data)
    },
    setParagraphProp (context, data) {
      context.commit('mutateParagraphsWithNewProps', data)
    },
    addParagraph (context, data) {
      let paragraph = {
        config: {},
        id: data.paragraphId,
        jobName: '',
        progressUpdateIntervalMs: 1000,
        settings: {
          forms: {},
          params: {}
        },
        status: 'INIT',
        text: '',
        version: 'v0'
      }

      if (data.isMd) {
        paragraph.config.editorHide = true
        paragraph.text = '%md\n'
        paragraph.forceEditorShow = true
        paragraph.result = {
          code: 'SUCCESS',
          msg: [
            {
              data: paragraph.text,
              type: 'HTML'
            }
          ]
        }
      }

      context.commit('addParagraph', {
        index: data.index,
        paragraph: paragraph,
        notebookId: data.notebookId
      })
    },
    removeParagraph (context, data) {
      context.commit('removeParagraph', data)
    }
  }
}
