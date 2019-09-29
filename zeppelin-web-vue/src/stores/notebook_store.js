import Vue from 'vue'

export default {
  state: {
    notes: [],
    note: null,
    paragraphs: null,
    isListLoading: true,
    nav: {
      isOpen: true
    },
    activeParagraph: null
  },
  getters: {
    getNote: (state) => (noteId) => {
      let filteredNote = state.notes.find(n => n.id === noteId)
      return filteredNote
    },
    getAllNotes: (state) => {
      return state.notes
    },
    getActiveParagraph: (state) => {
      return state.activeParagraph
    },
    getNavBarState: (state) => {
      return state.nav
    },
    getParagraphById: (state) => (id, noteId) => {
      let filteredNote = state.notes.find(n => n.id === noteId)
      return filteredNote.paragraphs.find(p => p.id === id)
    },
    getAllParagraphs: (state, noteId) => {
      let filteredNote = state.notes.find(n => n.id === noteId)
      return filteredNote && filteredNote.paragraphs
    },
    isParagraphLoading: (state) => (id, noteId) => {
      let filteredNote = state.notes.find(n => n.id === noteId)

      let paragraph = filteredNote && filteredNote.paragraphs.find(p => p.id === id)
      let status = paragraph.status.toLowerCase()

      if (status === 'pending' || status === 'running') {
        return true
      }

      return false
    },
    isParagraphCreating: (state) => (id, noteId) => {
      let filteredNote = state.notes.find(n => n.id === noteId)
      let paragraph = filteredNote && filteredNote.paragraphs.find(p => p.id === id)
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
      state.notes = data.notes
    },
    mutateNote (state, noteObj) {
      let index = state.notes.map(function (n) { return n.id }).indexOf(noteObj.note.id)
      let newObj = Object.assign(state.notes[index], noteObj.note)
      Vue.set(state.notes, index, newObj)

      // set paragraphs state
      // state.activeParagraph = paragraphs[0]
      return state
    },
    addParagraph (state, data) {
      let filteredNote = state.notes.find(n => n.id === data.noteId)
      let paragraphs = filteredNote && filteredNote.paragraphs

      paragraphs.splice(data.index, 0, data.paragraph)
      filteredNote.paragraphs = paragraphs
    },
    removeParagraph (state, data) {
      let filteredNote = state.notes.find(n => n.id === data.noteId)
      let paragraphs = filteredNote && filteredNote.paragraphs
      let index = paragraphs.map(function (p) { return p.id }).indexOf(data.id)
      paragraphs.splice(index, 1)
      filteredNote.paragraphs = paragraphs
    },
    mutateParagraphs (state, data) {
      let filteredNote = state.notes.find(n => n.id === data.noteId)
      let paragraphs = filteredNote && filteredNote.paragraphs

      let id = data.paragraph.id
      let index = paragraphs.map(function (p) { return p.id }).indexOf(id)

      let paragraph = paragraphs[index]

      // if paragraph do not exist
      // it means it was not added from this context
      // some one else has opened the same note and
      // is adding paragraph

      if (!paragraph && data.index) {
        Vue.set(filteredNote.paragraphs, data.index, data.paragraph)
        return
      }

      if (!paragraph.forceEditorShow) {
        // Vue.set(state.paragraphs, index, data.paragraph)
      }
    },
    mutateParagraphsWithNewProps (state, data) {
      let id = data.id
      let filteredNote = state.notes.find(n => n.id === data.noteId)
      let paragraph = filteredNote.paragraphs.find(p => p.id === id)

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
      let filteredNote = state.notes.find(n => n.id === data.noteId)
      let paragraph = filteredNote.paragraphs.find(p => p.id === paraId)
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
    setNoteContent (context, data) {
      context.commit('mutateNote', data)
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
        noteId: data.noteId
      })
    },
    removeParagraph (context, data) {
      context.commit('removeParagraph', data)
    }
  }
}
