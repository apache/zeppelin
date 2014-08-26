package com.nflabs.zeppelin.socket;

import java.net.InetSocketAddress;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.Note;
import com.nflabs.zeppelin.server.Notebook;
import com.nflabs.zeppelin.server.ZeppelinServer;
import com.nflabs.zeppelin.socket.Message.OP;

public class NotebookServer extends WebSocketServer {

	private static final Logger LOG = LoggerFactory
			.getLogger(NotebookServer.class);

	private static final int DEFAULT_PORT = 8282;

	private static void creatingwebSocketServerLog(int port) {
		LOG.info("Create zeppeling websocket on port {}", port);
	}

	Gson gson = new Gson();
	
	public NotebookServer() {
		super(new InetSocketAddress(DEFAULT_PORT));
		creatingwebSocketServerLog(DEFAULT_PORT);
	}

	public NotebookServer(int port) {
		super(new InetSocketAddress(port));
		creatingwebSocketServerLog(port);
		
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		LOG.info("Closed connection to "
				+ conn.getRemoteSocketAddress().getAddress().getHostAddress());

	}

	@Override
	public void onError(WebSocket conn, Exception message) {
		// TODO Auto-generated method stub

	}

	private Message deserializeMessage(String msg) {
		Message m = gson.fromJson(msg, Message.class);
		return m;
	}

	private String serializeMessage(Message m) {
		return gson.toJson(m);
	}

	@Override
	public void onMessage(WebSocket conn, String msg) {
		Notebook notebook = ZeppelinServer.notebook;

		try { 
			Message m = deserializeMessage(msg);
			LOG.info("[OP] -- "+m.op+" --");
			if(m.op == OP.GET_NOTE) { // get note			
				String noteId = (String) m.get("id");			
				Note note = notebook.getNote(noteId);			
				conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
			} else if(m.op == OP.NEW_NOTE) { // new note
				Note note = notebook.createNote();
				conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
			} else {
				LOG.error("Unsupported operation {}", m.op);
			}
		} catch (Exception e) {
			LOG.error("Can't handle message", e);
		}
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		LOG.info("New connection from "
				+ conn.getRemoteSocketAddress().getAddress().getHostAddress());
	}

}
