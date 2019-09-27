import WsConnection from '@/classes/web-socket.js'

let wsConn = {}

export default {
  initNoteConnection (noteId, store) {
    wsConn[noteId] = new WsConnection(store, store.state.ticket, noteId)
  },

  getConn (noteId) {
    return wsConn[noteId]
  },

  closeConn (noteId) {
    wsConn[noteId].ws.close()
  }
}
