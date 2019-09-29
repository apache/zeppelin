import api from '@/services/api.js'

export default {
  state: {
    credentials: []
  },
  getters: {
    getAllCredentials: (state) => {
      return state.credentials
    }
  },
  mutations: {
    saveCredentials (state, data) {
      state.credentials = data
    }
  },
  actions: {
    getCredentials (context, data) {
      return fetch(api.getRestApiBase() + '/credential', {
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
          context.commit('saveCredentials', result.body)
        })
    },
    createCredential (context, data) {
      // return fetch(api.getRestApiBase() + '/credential', {
      // PUT
      // params:
      //     entity: "b"
      //     password: "b"
      //     username: "b"
    },
    removeCredential (context, data) {
      // http://localhost:8080/api/credential/<<key>>
      // DELETE
    },
    updateCredential (context, data) {
      //
    }
  }
}
