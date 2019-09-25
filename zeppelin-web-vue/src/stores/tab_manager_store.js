export default {
  state: {
    tabs: [],
    currentTab: null
  },
  getters: {
    getTabsCount: (state) => {
      return state.tabs.length
    },
    getCurrentTab: (state) => {
      return state.currentTab
    }
  },
  mutations: {
    addTab (state, data) {
      let isExist = state.tabs.filter(t => (t.path && t.path === data.path) || (!t.path && t.type === data.type))
      state.currentTab = data
      if (isExist.length === 0) {
        state.tabs.push(data)
      }
      return state
    },
    removeTab (state, data) {
      let tabs = state.tabs
      let index = tabs.map((t) => { return t.name }).indexOf(data.name)

      // Remove the tab
      tabs.splice(index, 1)
      state.tabs = tabs

      return state
    },
    setActiveTab (state, data) {
      state.currentTab = data
      return state
    }
  },
  actions: {
    addTab (context, data) {
      context.commit('addTab', data)
    },
    removeTab (context, data) {
      let state = context.state
      let tabs = state.tabs
      let index = tabs.map((t) => { return t.name }).indexOf(data.name)

      // Update the active tab
      if (tabs.length > 1) {
        switch (index) {
          // First tab will be deleted
          case 0:
            context.commit('setActiveTab', tabs[1])
            break
          // Last tab will be deleted
          case tabs.length - 1:
            context.commit('setActiveTab', tabs[tabs.length - 2])
            break
          // Any in between tabs were deleted
          default:
            context.commit('setActiveTab', tabs[index - 1])
        }
      } else {
        // No tabs are opened
        context.commit('setActiveTab', null)
      }

      context.commit('removeTab', data)
    },
    setActiveTab (context, data) {
      context.commit('setActiveTab', data)
    }
  }
}
