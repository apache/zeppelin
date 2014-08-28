package com.nflabs.zeppelin.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.server.Note;
import com.nflabs.zeppelin.server.Notebook;
import com.nflabs.zeppelin.server.Paragraph;
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
	Map<String, List<WebSocket>> noteSocketMap = new HashMap<String, List<WebSocket>>();
	List<WebSocket> connectedSockets = new LinkedList<WebSocket>();
	
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
		LOG.info("Closed connection to {} : {}", conn.getRemoteSocketAddress().getHostName()
		                                       , conn.getRemoteSocketAddress().getPort());
		removeConnectionFromAllNote(conn);
		synchronized(connectedSockets){
			connectedSockets.remove(conn);
		}
	}

	@Override
	public void onError(WebSocket conn, Exception message) {
		removeConnectionFromAllNote(conn);
		synchronized(connectedSockets){
			connectedSockets.remove(conn);
		}
	}

	private Message deserializeMessage(String msg) {
		Message m = gson.fromJson(msg, Message.class);
		return m;
	}

	private String serializeMessage(Message m) {
		return gson.toJson(m);
	}
	
	private void addConnectionToNote(String noteId, WebSocket socket) {
		synchronized(noteSocketMap){
			removeConnectionFromAllNote(socket); // make sure a socket relates only a single note.
			List<WebSocket> socketList = noteSocketMap.get(noteId);
			if(socketList==null) {
				socketList = new LinkedList<WebSocket>();
				noteSocketMap.put(noteId, socketList);
			}
			
			if(socketList.contains(socket)==false){
				socketList.add(socket);
			}
		}
	}
	
	private void removeConnectionFromNote(String noteId, WebSocket socket){
		synchronized(noteSocketMap){
			List<WebSocket> socketList = noteSocketMap.get(noteId);
			if(socketList!=null) {
				socketList.remove(socket);
			}
		}
	}
	
	private void removeNote(String noteId){
		synchronized(noteSocketMap){
			List<WebSocket> socketList = noteSocketMap.remove(noteId);
		}
	}
	
	private void removeConnectionFromAllNote(WebSocket socket) {
		synchronized(noteSocketMap){
			Set<String> keys = noteSocketMap.keySet();
			for(String noteId : keys) {
				removeConnectionFromNote(noteId, socket);
			}
		}
	}
	
	private String getOpenNoteId(WebSocket socket) {
		String id = null;;
		synchronized(noteSocketMap) {
			Set<String> keys = noteSocketMap.keySet();
			for(String noteId : keys) {
				List<WebSocket> sockets = noteSocketMap.get(noteId);
				if(sockets.contains(socket)){
					id = noteId;
				}				
			}
		}
		
		return id;
	}

	private void broadcastNote(String noteId, Message m){
		LOG.info("SEND >> "+m.op);
		synchronized(noteSocketMap) {
			List<WebSocket> socketLists = noteSocketMap.get(noteId);
			if(socketLists==null || socketLists.size()==0) return;
			for(WebSocket conn : socketLists) {
				conn.send(serializeMessage(m));
			}
		}
	}
	
	private void broadcastAll(Message m) {
		synchronized(connectedSockets) {
			for(WebSocket conn : connectedSockets) {
				conn.send(serializeMessage(m));
			}
		}
	}
	
	private Notebook notebook(){
		return ZeppelinServer.notebook;
	}
	
	
	private void printConnectionMap(){
		synchronized(noteSocketMap){
			for(Note n : notebook().getAllNotes()){
				System.out.println("Note "+n.id());
				List<WebSocket> socketLists = noteSocketMap.get(n.id());
				if(socketLists==null || socketLists.size()==0) return;
				for(WebSocket conn : socketLists) {
					System.out.println("     - "+conn);
				}
			}
		}
	}
	
	
	@Override
	public void onMessage(WebSocket conn, String msg) {
		Notebook notebook = notebook();

		try { 
			Message m = deserializeMessage(msg);
			LOG.info("RECEIVE << "+m.op);
			if(m.op == OP.GET_NOTE) { // get note			
				String noteId = (String) m.get("id");			
				Note note = notebook.getNote(noteId);
				addConnectionToNote(note.id(), conn);
				conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
			} else if(m.op == OP.NEW_NOTE) { // new note
				Note note = notebook.createNote();
				note.addParagraph();         // it's an empty note. so add one paragraph
				note.persist();
				broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
				broadcastNoteList();
			} else if(m.op == OP.DEL_NOTE){
				String noteId = (String) m.get("id");
				notebook.removeNote(noteId);
				removeNote(noteId);
				broadcastNoteList();
			} else if(m.op == OP.PARAGRAPH_PARAM) {
				String paragraphId = (String) m.get("id");
				Map<String, Object> params = (Map<String, Object>) m.get("params");
				final Note note = notebook.getNote(getOpenNoteId(conn));
				Paragraph p = note.getParagraph(paragraphId);
				p.form.setParams(params);
				note.persist();
				broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));	
			} else if(m.op == OP.COMMIT_PARAGRAPH){
				String paragraphId = (String) m.get("id");
				final Note note = notebook.getNote(getOpenNoteId(conn));
				Paragraph p = note.getParagraph(paragraphId);
				p.setParagraph((String) m.get("paragraph"));
				Map<String, Object> params = (Map<String, Object>)m.get("params");
				p.form.setParams(params);
				
				note.persist();
				
				broadcastNote(note.id(), new Message(OP.PARAGRAPH).put("paragraph", p));
				
			} else if(m.op == OP.RUN_PARAGRAPH) { // run a paragaph
				final String paragraphId = (String) m.get("id");
				final Note note = notebook.getNote(getOpenNoteId(conn));
				Paragraph p = note.getParagraph(paragraphId);
				p.setParagraph((String) m.get("paragraph"));
				Map<String, Object> params = (Map<String, Object>)m.get("params");
				p.form.setParams(params);
				
				// if it's an last pargraph, let's add new one
				if(note.getLastParagraph().getId().equals(p.getId())){
					note.addParagraph();
					broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
				}
				
				note.persist();
				broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
				note.run(paragraphId, new JobListener(){					
					@Override
					public void beforeStatusChange(Job job, Status before,
							Status after) {
					}

					@Override
					public void afterStatusChange(Job job, Status before,
							Status after) {
						if(after == Status.ERROR){
							job.getException().printStackTrace();
						}
						if(job.isTerminated()){
							LOG.info("Job {} is finished", job.getId());
							try {
								note.persist();
							} catch (IOException e) {
								e.printStackTrace();
							}
							broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
						} else {
							broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
							//broadcastNote(note.id(), new Message(OP.PARAGRAPH).put("paragraph", note.getParagraph(paragraphId)));
						}
					}					
				});
			} else if(m.op == OP.LIST_NOTES) {
				broadcastNoteList();
			} else {
				LOG.error("Unsupported operation {}", m.op);
			}
		} catch (Exception e) {
			LOG.error("Can't handle message", e);
		}
	}
	
	private void broadcastNoteList(){
		Notebook notebook = notebook();
		List<Note> notes = notebook.getAllNotes();
		List<Map<String, String>> notesInfo = new LinkedList<Map<String, String>>();
		for(Note note : notes) {
			Map<String, String> info = new HashMap<String, String>();
			info.put("id", note.id());
			info.put("name", note.getName());
			notesInfo.add(info);
		}
		
		broadcastAll(new Message(OP.NOTES_INFO).put("notes", notesInfo));		
	}
	

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		LOG.info("New connection from {} : {}", conn.getRemoteSocketAddress().getHostName()
		                                      , conn.getRemoteSocketAddress().getPort());
		synchronized(connectedSockets){
			connectedSockets.add(conn);
		}
	}

}
