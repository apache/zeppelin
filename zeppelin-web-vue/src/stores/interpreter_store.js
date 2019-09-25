import api from '@/services/api.js'

export default {
  state: {
    interpreters: [],
    interpreter: null
  },
  getters: {
    getAllInterpreters: (state) => {
      return state.interpreters
    }
  },
  mutations: {
    mutateInterpreters (state, data) {
      state.interpreters = data
    }
  },
  actions: {
    getInterpreters (context, data) {
      return fetch(api.getRestApiBase() + '/interpreter', {
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
          context.commit('mutateInterpreters', result.body)
        })
    },
    updateInterpreter (context, data) {
      // PUT with options
      // http://localhost:8080/api/interpreter/setting/restart/<<key>>
      // message: ""
      // status: "OK"
      // body
    },
    restartInterpreter (context, data) {
      // PUT without options
      // http://localhost:8080/api/interpreter/setting/restart/<<key>>
      // message: ""
      // status: "OK"
      // body
    },
    createInterpreter (context, data) {
      // POST
      // http://localhost:8080/api/interpreter/setting
      // message: ""
      // status: "OK"
      // body
    },
    removeInterpreter (context, data) {
      // DELETE
      // http://localhost:8080/api/interpreter/setting/<<key>>
      // status: "OK"
    }
  }
}
