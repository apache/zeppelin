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
import com.nflabs.zeppelin.notebook.Note;
import com.nflabs.zeppelin.notebook.Notebook;
import com.nflabs.zeppelin.notebook.Paragraph;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.server.ZeppelinServer;
import com.nflabs.zeppelin.socket.Message.OP;

/**
 * Zeppelin websocket service.
 * 
 * @author anthonycorbacho
 */
public class NotebookServer extends WebSocketServer {

  private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);
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
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    LOG.info("New connection from {} : {}", conn.getRemoteSocketAddress().getHostName(), conn
        .getRemoteSocketAddress().getPort());
    synchronized (connectedSockets) {
      connectedSockets.add(conn);
    }
  }
  
  @Override
  public void onMessage(WebSocket conn, String msg) {
    Notebook notebook = notebook();
    try {
      Message messagereceived = deserializeMessage(msg);
      LOG.info("RECEIVE << " + messagereceived.op);
      /** Lets be elegant here */
      switch (messagereceived.op) {
        case LIST_NOTES:
          broadcastNoteList();
          break;
        case GET_NOTE:
          sendNote(conn, notebook, messagereceived);
          break;
        case NEW_NOTE:
          createNote(conn, notebook);
          break;
        case DEL_NOTE:
          removeNote(conn, notebook, messagereceived);
          break;
        case PARAGRAPH_PARAM:
          addParamsForParagraph(conn, notebook, messagereceived);
          break;
        case COMMIT_PARAGRAPH:
          updateParamsForParagraph(conn, notebook, messagereceived);
          break;
        case PARAGRAPH_UPDATE_STATE:
          updateParagraphState(conn, notebook, messagereceived);
          break;
        case RUN_PARAGRAPH:
          runParagraph(conn, notebook, messagereceived);
          break;
        default:
          broadcastNoteList();
          break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    LOG.info("Closed connection to {} : {}", conn.getRemoteSocketAddress().getHostName(),
                                             conn.getRemoteSocketAddress().getPort());
    removeConnectionFromAllNote(conn);
    synchronized (connectedSockets) {
      connectedSockets.remove(conn);
    }
  }

  @Override
  public void onError(WebSocket conn, Exception message) {
    removeConnectionFromAllNote(conn);
    synchronized (connectedSockets) {
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
    synchronized (noteSocketMap) {
      removeConnectionFromAllNote(socket); // make sure a socket relates only a single note.
      List<WebSocket> socketList = noteSocketMap.get(noteId);
      if (socketList == null) {
        socketList = new LinkedList<WebSocket>();
        noteSocketMap.put(noteId, socketList);
      }

      if (socketList.contains(socket) == false) {
        socketList.add(socket);
      }
    }
  }

  private void removeConnectionFromNote(String noteId, WebSocket socket) {
    synchronized (noteSocketMap) {
      List<WebSocket> socketList = noteSocketMap.get(noteId);
      if (socketList != null) {
        socketList.remove(socket);
      }
    }
  }

  private void removeNote(String noteId) {
    synchronized (noteSocketMap) {
      List<WebSocket> socketList = noteSocketMap.remove(noteId);
    }
  }

  private void removeConnectionFromAllNote(WebSocket socket) {
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        removeConnectionFromNote(noteId, socket);
      }
    }
  }

  private String getOpenNoteId(WebSocket socket) {
    String id = null;;
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        List<WebSocket> sockets = noteSocketMap.get(noteId);
        if (sockets.contains(socket)) {
          id = noteId;
        }
      }
    }

    return id;
  }

  private void broadcastNote(String noteId, Message m) {
    LOG.info("SEND >> " + m.op);
    synchronized (noteSocketMap) {
      List<WebSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0)
        return;
      for (WebSocket conn : socketLists) {
        conn.send(serializeMessage(m));
      }
    }
  }

  private void broadcastAll(Message m) {
    synchronized (connectedSockets) {
      for (WebSocket conn : connectedSockets) {
        conn.send(serializeMessage(m));
      }
    }
  }

  private Notebook notebook() {
    return ZeppelinServer.notebook;
  }

  private void broadcastNoteList() {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes();
    List<Map<String, String>> notesInfo = new LinkedList<Map<String, String>>();
    for (Note note : notes) {
      Map<String, String> info = new HashMap<String, String>();
      info.put("id", note.id());
      info.put("name", note.getName());
      notesInfo.add(info);
    }

    broadcastAll(new Message(OP.NOTES_INFO).put("notes", notesInfo));
  }
  
  private void sendNote(WebSocket conn, Notebook notebook, Message fromMessage) {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return ;
    }
    Note note = notebook.getNote(noteId);
    addConnectionToNote(note.id(), conn);
    conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
  }

  private void createNote(WebSocket conn, Notebook notebook) throws IOException {
    Note note = notebook.createNote();
    note.addParagraph(); // it's an empty note. so add one paragraph
    note.persist();
    broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
    broadcastNoteList();
  }

  private void removeNote(WebSocket conn, Notebook notebook, Message fromMessage) {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return ;
    }
    notebook.removeNote(noteId);
    removeNote(noteId);
    broadcastNoteList();
  }

  private void addParamsForParagraph(WebSocket conn, Notebook notebook, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return ;
    }
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    final Note note = notebook.getNote(getOpenNoteId(conn));
    Paragraph p = note.getParagraph(paragraphId);
    p.settings.setParams(params);
    note.persist();
    broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
  }

  private void updateParamsForParagraph(WebSocket conn, Notebook notebook, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return ;
    }
    final Note note = notebook.getNote(getOpenNoteId(conn));
    Paragraph p = note.getParagraph(paragraphId);
    p.setText((String) fromMessage.get("paragraph"));
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    p.settings.setParams(params);
    note.persist();
    broadcastNote(note.id(), new Message(OP.PARAGRAPH).put("paragraph", p));
  }
  
  private void updateParagraphState(WebSocket conn, Notebook notebook, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return ;
    }
    final Note note = notebook.getNote(getOpenNoteId(conn));
    Paragraph p = note.getParagraph(paragraphId);
    boolean state = (boolean) fromMessage.get("isClose");
    boolean editorState = (boolean) fromMessage.get("isEditorClose");
    if (state) {
      p.close();
    } else {
      p.open();
    }
    if (editorState) {
      p.closeEditor();
    } else {
      p.openEditor();
    }
    note.persist();
    broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
  }
  
  private void runParagraph(WebSocket conn, Notebook notebook, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    final Note note = notebook.getNote(getOpenNoteId(conn));
    Paragraph p = note.getParagraph(paragraphId);
    p.setText((String) fromMessage.get("paragraph"));
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    p.settings.setParams(params);

    // if it's an last pargraph, let's add new one
    if (note.getLastParagraph().getId().equals(p.getId())) {
      note.addParagraph();
      broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
    }
    note.persist();
    broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
    note.run(paragraphId, new JobListener() {
      @Override
      public void beforeStatusChange(Job job, Status before, Status after) {}

      @Override
      public void afterStatusChange(Job job, Status before, Status after) {
        if (after == Status.ERROR) {
          job.getException().printStackTrace();
        }
        if (job.isTerminated()) {
          LOG.info("Job {} is finished", job.getId());
          try {
            note.persist();
          } catch (IOException e) {
            e.printStackTrace();
          }
          broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
        } else {
          broadcastNote(note.id(), new Message(OP.NOTE).put("note", note));
          // broadcastNote(note.id(), new Message(OP.PARAGRAPH).put("paragraph",
          // note.getParagraph(paragraphId)));
        }
      }
    });
  }
}
