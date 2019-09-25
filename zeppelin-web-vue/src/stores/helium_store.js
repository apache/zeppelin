import api from '@/services/api.js'

export default {
  state: {
    packages: []
  },
  getters: {
    getAllPackages: (state) => {
      return state.packages
    }
  },
  mutations: {
    savePackages (state, data) {
      state.packages = data
    },
    enablePackage (state, pkg) {
      // Pending
    },
    disablePackage (state, pkg) {
      // Pending
    }
  },
  actions: {
    getPackages (context, data) {
      return fetch(api.getRestApiBase() + '/helium/package', {
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
          context.commit('savePackages', result.body)
        })
    },
    // http://localhost:8080/api/helium/enable/sogou-map-vis
    enablePackage (context, data) {
      return fetch(api.getRestApiBase() + '/helium/enable/' + data.id, {
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
          // Pending
        })
    }
  }
}
