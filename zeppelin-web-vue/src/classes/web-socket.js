import api from '@/services/api.js'

export default class WsConnection {
  conn = {}
  listLoaded = false

  constructor (store, ticket, noteId) {
    this.store = store
    this.ticket = ticket

    if (noteId) {
      this.noteId = noteId
    }

    this.establishConnection()
  }

  isListLoaded () {
    return this.listLoaded
  }

  send (message, callback) {
    this.waitForConnection(() => {
      this.conn.sendNewEvent(message)

      if (typeof callback !== 'undefined') {
        callback()
      }
    }, 1000)
  }

  waitForConnection (callback, interval) {
    if (this.conn.ws && this.conn.ws.readyState && this.conn.ws.readyState === 1) {
      callback()
    } else {
      // optional: implement backoff for interval here
      setTimeout(() => {
        this.waitForConnection(callback, interval)
      }, interval)
    }
  }

  reconnect () {
    // Pending Implementation
  }

  establishConnection () {
    this.ws = new WebSocket(api.getWebsocketUrl())

    if (!this.noteId) {
      this.store.dispatch('updateWebSocketStatus', 'trying')
    }

    this.conn.ws = this.ws
    this.conn.ws.reconnectIfNotNormalClose = true

    this.conn.sendNewEvent = (data) => {
      if (this.ticket !== undefined) {
        data.principal = this.ticket.principal
        data.ticket = this.ticket.ticket
        data.roles = this.ticket.roles
      } else {
        data.principal = ''
        data.ticket = ''
        data.roles = ''
      }
      console.log('Send >> %o, %o, %o, %o, %o', data.op, data.principal, data.ticket, data.roles, data)
      this.conn.ws.send(JSON.stringify(data))
    }

    this.ws.onopen = () => {
      if (!this.noteId) {
        this.store.dispatch('updateWebSocketStatus', 'connected')
      }

      this.timer = setInterval(() => {
        this.conn.sendNewEvent({ op: 'PING' })
      }, 30000)
    }

    this.ws.onmessage = (evt) => {
      let payload

      if (event.data) {
        payload = JSON.parse(event.data)
      }

      console.log('Receive << %o, %o', payload.op, payload)

      let op = payload.op
      let data = payload.data

      if (this.noteId) {
        data.noteId = this.noteId
      }

      switch (op) {
        // ---------------- Note ---------------------------
        case 'NEW_NOTE':
          // window.open(`/note/${data.note.id}`, '_blank')
          // Pending - open the note tab data.note
          break
        case 'NOTES_INFO':
          this.listLoaded = true

          this.store.dispatch('setNoteList', data)
          break
        case 'NOTE':
          this.store.dispatch('setNoteContent', data)
          break

        // ---------------- Paragraph ---------------------------
        case 'PARAGRAPH':
          // we are already updating the local copy
          // of paragraphs so do not update again from
          // websockets
          // this.store.dispatch('setParagraph', data)
          break
        case 'PARAGRAPH_APPEND_OUTPUT':
          this.store.dispatch('setParagraphOutput', data)
          break
        case 'PARAGRAPH_ADDED':
          this.store.dispatch('setParagraph', data)
          break

        // ---------------- Other ---------------------------
        // interpreter
        case 'INTERPRETER_STATUS':
          break
        // interpreter list
        case 'CONFIGURATIONS_INFO':
          break
        // activity list
        case 'LIST_NOTE_JOBS':
          this.store.dispatch('saveActivityList', data)
          break
      }
    }
    this.ws.onerror = (e) => {
      console.log('WebSocket Error: ', e)
      this.handleErrors(e)
    }

    this.ws.onclose = (e) => {
      this.timer && clearInterval(this.timer)
      console.log('Connection closed', e)

      if (!this.noteId) {
        this.store.dispatch('updateWebSocketStatus', 'disconnected')
      }
    }
  }

  handleErrors (e) {

  }
}
