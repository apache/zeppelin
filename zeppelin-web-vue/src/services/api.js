export default {
  getWebsocketUrl () {
    let wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    return wsProtocol + '//' + location.hostname + ':' + this.getPort() + '/ws'
  },

  getPort () {
    let port = Number(process.env.VUE_APP_SERVER_PORT)
    if (!port) {
      port = 80

      if (location.protocol === 'https:') {
        port = process.env.VUE_APP_SERVER_SSL_PORT
      }
    }

    return port
  },

  getRestApiBase () {
    return location.protocol + '//' + location.hostname + ':' + this.getPort() + '/api'
  }
}
