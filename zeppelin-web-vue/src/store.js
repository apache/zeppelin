import Vue from 'vue'
import Vuex from 'vuex'
import 'es6-promise/auto'

import api from '@/services/api.js'

import TabManagerStore from '@/stores/tab_manager_store'
import NotebookStore from '@/stores/notebook_store'
import InterpreterStore from '@/stores/interpreter_store'
import HeliumStore from '@/stores/helium_store'

Vue.use(Vuex)

const store = new Vuex.Store({
  modules: {
    TabManagerStore,
    NotebookStore,
    InterpreterStore,
    HeliumStore
  },

  state: {
    ticket: null,

    selectedLeftNavTab: 'folders',
    showPrefererences: false,
    darkMode: false,
    webSocketStatus: 'disconnected',

    configurations: null,

    notebookRepos: [],

    isActivityLoading: true,
    activities: []
  },

  mutations: {
    mutateTicketData (state, data) {
      state.ticket = data
    },
    mutateSelectedTab (state, data) {
      state.selectedLeftNavTab = data
    },
    mutateShowPreferences (state, data) {
      state.showPrefererences = data
    },
    mutateDarkMode (state, newStatus) {
      state.darkMode = newStatus
    },
    updateWebSocketStatus (state, newStatus) {
      state.webSocketStatus = newStatus
    },

    saveConfiguration (state, data) {
      state.configurations = data
    },

    saveNotebookRepos (state, data) {
      state.notebookRepos = data
    },

    saveActivityList (state, data) {
      state.isActivityLoading = false
      state.activities = data
    }
  },

  actions: {
    updateTicketDate (context, data) {
      context.commit('mutateTicketData', data)
    },
    selectLeftNavTab (context, data) {
      context.commit('mutateSelectedTab', data)
    },
    togglePreferences (context, data) {
      context.commit('mutateShowPreferences', data)
    },
    toggleDarkMode (context, newStatus) {
      context.commit('mutateDarkMode', newStatus)
    },
    updateWebSocketStatus (context, newStatus) {
      context.commit('updateWebSocketStatus', newStatus)
    },

    saveActivityList (context, data) {
      context.commit('saveActivityList', data.noteJobs.jobs)
    },

    getConfiguration (context) {
      return fetch(api.getRestApiBase() + '/configurations/all', {
        method: 'GET',
        credentials: 'same-origin'
      })
        .then(function (response) {
          if (!response.ok) {
            if (response.error) {
              throw Error(response.error.error_message)
            } else {
              throw Error(response.statusText)
            }
          }

          return response.json()
        }).then(function (result) {
          context.commit('saveConfiguration', result.body)
        })
    },

    getNotebookRepos (context) {
      return fetch(api.getRestApiBase() + '/notebook-repositories', {
        method: 'GET',
        credentials: 'same-origin'
      })
        .then(function (response) {
          if (!response.ok) {
            if (response.error) {
              throw Error(response.error.error_message)
            } else {
              throw Error(response.statusText)
            }
          }

          return response.json()
        }).then(function (result) {
          context.commit('saveNotebookRepos', result.body)
        })
    },

    updateNotebookRepos (context, data) {
      return fetch(api.getRestApiBase() + '/notebook-repositories', {
        method: 'PUT',
        credentials: 'same-origin'
      })
    }

  }
})

export default store
