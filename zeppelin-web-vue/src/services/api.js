export default {
  getWebsocketUrl () {
    let wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    return wsProtocol + '//' + location.hostname + ':' + this.getWSPort() + '/ws'
  },

  getWSPort () {
    return this.getPort()
  },

  getPort () {
    let port = Number(location.port - 1)
    if (!port) {
      port = 80
      if (location.protocol === 'https:') {
        port = 443
      }
    }

    // add a custom websocket port here

    return port
  },

  getRestApiBase () {
    return location.protocol + '//' + location.hostname + ':' + this.getPort() + '/api'
  }
}
