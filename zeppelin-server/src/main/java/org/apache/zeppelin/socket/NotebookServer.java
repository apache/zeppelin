/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.socket;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.http.HttpServletRequest;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.Message.OP;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.utils.SecurityUtils;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.Gson;

/**
 * Zeppelin websocket service.
 *
 */
public class NotebookServer extends WebSocketServlet implements
    NotebookSocketListener, JobListenerFactory, AngularObjectRegistryListener {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);
  Gson gson = new Gson();
  Map<String, List<NotebookSocket>> userSocketMap = new HashMap<>();
  final Map<String, List<NotebookSocket>> noteSocketMap = new HashMap<>();
  final Queue<NotebookSocket> connectedSockets = new ConcurrentLinkedQueue<>();

  private Notebook notebook() {
    return ZeppelinServer.notebook;
  }

  @Override
  public boolean checkOrigin(HttpServletRequest request, String origin) {
    try {
      return SecurityUtils.isValidOrigin(origin, ZeppelinConfiguration.create());
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return false;
  }

  @Override
  public WebSocket doWebSocketConnect(HttpServletRequest req, String protocol) {
    return new NotebookSocket(req, protocol, this);
  }

  @Override
  public void onOpen(NotebookSocket conn) {
    LOG.info("New connection from {} : {}", conn.getRequest().getRemoteAddr(),
        conn.getRequest().getRemotePort());
    connectedSockets.add(conn);
  }

  @Override
  public void onMessage(NotebookSocket conn, String msg) {
    Notebook notebook = notebook();
    try {
      Message messagereceived = deserializeMessage(msg);
      LOG.debug("RECEIVE << " + messagereceived.op);
      LOG.debug("RECEIVE PRINCIPAL << " + messagereceived.principal);
      LOG.debug("RECEIVE TICKET << " + messagereceived.ticket);
      String ticket = TicketContainer.instance.getTicket(messagereceived.principal);
      if (ticket != null && !ticket.equals(messagereceived.ticket))
        throw new Exception("Invalid ticket " + messagereceived.ticket + " != " + ticket);

      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      boolean allowAnonymous = conf.
          getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED);
      if (!allowAnonymous && messagereceived.principal.equals("anonymous")) {
        throw new Exception("Anonymous access not allowed ");
      }

      addConnectionToUserSocketMap(conn, messagereceived);

      /** Lets be elegant here */
      switch (messagereceived.op) {
          case LIST_NOTES:
            broadcastNoteList(messagereceived.principal);
            break;
          case GET_HOME_NOTE:
            sendHomeNote(conn, notebook, messagereceived);
            break;
          case GET_NOTE:
            sendNote(conn, notebook, messagereceived);
            break;
          case NEW_NOTE:
            createNote(conn, notebook, messagereceived);
            break;
          case DEL_NOTE:
            removeNote(conn, notebook, messagereceived);
            break;
          case CLONE_NOTE:
            cloneNote(conn, notebook, messagereceived);
            break;
          case IMPORT_NOTE:
            importNote(conn, notebook, messagereceived);
            break;
          case COMMIT_PARAGRAPH:
            updateParagraph(conn, notebook, messagereceived);
            break;
          case RUN_PARAGRAPH:
            runParagraph(conn, notebook, messagereceived);
            break;
          case CANCEL_PARAGRAPH:
            cancelParagraph(conn, notebook, messagereceived);
            break;
          case MOVE_PARAGRAPH:
            moveParagraph(conn, notebook, messagereceived);
            break;
          case INSERT_PARAGRAPH:
            insertParagraph(conn, notebook, messagereceived);
            break;
          case PARAGRAPH_REMOVE:
            removeParagraph(conn, notebook, messagereceived);
            break;
          case PARAGRAPH_CLEAR_OUTPUT:
            clearParagraphOutput(conn, notebook, messagereceived);
            break;
          case NOTE_UPDATE:
            updateNote(conn, notebook, messagereceived);
            break;
          case COMPLETION:
            completion(conn, notebook, messagereceived);
            break;
          case PING:
            break; //do nothing
          case ANGULAR_OBJECT_UPDATED:
            angularObjectUpdated(conn, notebook, messagereceived);
            break;
          default:
            broadcastNoteList(messagereceived.principal);
            break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }

  private void addConnectionToUserSocketMap(NotebookSocket conn, Message messagereceived) {
    List<NotebookSocket> conns = userSocketMap.get(messagereceived.principal);

    if (conns == null) {
      synchronized (userSocketMap) {
        conns = userSocketMap.get(messagereceived.principal);
        if (conns == null) {
          conns = new LinkedList<>();
          userSocketMap.put(messagereceived.principal, conns);
        }
      }
    }

    if (!conns.contains(conn)) {
      conns.add(conn);
    }
  }

  @Override
  public void onClose(NotebookSocket conn, int code, String reason) {
    LOG.info("Closed connection to {} : {}. ({}) {}", conn.getRequest()
        .getRemoteAddr(), conn.getRequest().getRemotePort(), code, reason);
    removeConnectionFromAllNote(conn);
    synchronized (userSocketMap) {
      Collection<List<NotebookSocket>> allSockets = userSocketMap.values();
      for (List<NotebookSocket> userList : allSockets) {
        userList.remove(conn);
      }
    }
  }

  @Override
  public void onError(NotebookSocket conn, Exception message) {
    removeConnectionFromAllNote(conn);
    synchronized (userSocketMap) {
      Collection<List<NotebookSocket>> allSockets = userSocketMap.values();
      for (List<NotebookSocket> userList : allSockets) {
        userList.remove(conn);
      }
    }
    connectedSockets.remove(conn);
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  private String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  private void addConnectionToNote(String noteId, NotebookSocket socket) {
    synchronized (noteSocketMap) {
      removeConnectionFromAllNote(socket); // make sure a socket relates only a
      // single note.
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList == null) {
        socketList = new LinkedList<>();
        noteSocketMap.put(noteId, socketList);
      }
      if (!socketList.contains(socket)) {
        socketList.add(socket);
      }
    }
  }

  private void removeConnectionFromNote(String noteId, NotebookSocket socket) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList != null) {
        socketList.remove(socket);
      }
    }
  }

  private void removeNote(String noteId) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketList = noteSocketMap.remove(noteId);
    }
  }

  private void removeConnectionFromAllNote(NotebookSocket socket) {
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        removeConnectionFromNote(noteId, socket);
      }
    }
  }

  private String getOpenNoteId(NotebookSocket socket) {
    String id = null;
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        List<NotebookSocket> sockets = noteSocketMap.get(noteId);
        if (sockets.contains(socket)) {
          id = noteId;
        }
      }
    }

    return id;
  }

  private void broadcastToNoteBindedInterpreter(String interpreterGroupId,
                                                Message m) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes(m.principal);
    for (Note note : notes) {
      List<String> ids = note.getNoteReplLoader().getInterpreters();
      for (String id : ids) {
        if (id.equals(interpreterGroupId)) {
          broadcast(note.id(), m);
        }
      }
    }
  }

  private void broadcast(String noteId, Message m) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (NotebookSocket conn : socketLists) {
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  private void broadcastExcept(String noteId, Message m, NotebookSocket exclude) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (NotebookSocket conn : socketLists) {
        if (exclude.equals(conn)) {
          continue;
        }
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  private void broadcastAll(Message m) {
    synchronized (userSocketMap) {
      List<Map<String, String>> notesInfo = (List<Map<String, String>>) m.get("notes");
      String principal = m.principal;
      List<NotebookSocket> conns = userSocketMap.get(principal);
      if (conns == null) {
        conns = new LinkedList<>();
        userSocketMap.put(principal, conns);
      }

      for (NotebookSocket theconn : conns) {
        try {
          theconn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  public List<Map<String, String>> generateNotebooksInfo (String principal){
    Notebook notebook = notebook();

    ZeppelinConfiguration conf = notebook.getConf();
    String homescreenNotebookId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList = conf.
        getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    List<Note> notes = notebook.getAllNotes(principal);
    List<Map<String, String>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      Map<String, String> info = new HashMap<>();

      if (hideHomeScreenNotebookFromList && note.id().equals(homescreenNotebookId)) {
        continue;
      }

      info.put("id", note.id());
      info.put("name", note.getName());
      info.put("principal", principal);
      notesInfo.add(info);
    }

    return notesInfo;
  }

  public void broadcastNote(Note note) {
    broadcast(note.id(), new Message(OP.NOTE).put("note", note));
  }

  public void broadcastNoteList(String principal) {

    List<Map<String, String>> notesInfo = generateNotebooksInfo(principal);
    Message message = new Message(OP.NOTES_INFO).put("notes", notesInfo);
    message.principal = principal;
    broadcastAll(message);
  }

  private void sendNote(NotebookSocket conn, Notebook notebook,
                        Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }
    Note note = notebook.getNote(noteId, fromMessage.principal);
    if (note != null) {
      addConnectionToNote(note.id(), conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, conn);
    }
  }

  private void sendHomeNote(NotebookSocket conn, Notebook notebook,
                            Message fromMessage) throws IOException {
    String noteId = notebook.getConf().getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);

    Note note = null;
    if (noteId != null) {
      note = notebook.getNote(noteId, fromMessage.principal);
    }

    if (note != null) {
      addConnectionToNote(note.id(), conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, conn);
    } else {
      removeConnectionFromAllNote(conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
    }
  }

  private void updateNote(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    if (noteId == null) {
      return;
    }
    if (config == null) {
      return;
    }
    Note note = notebook.getNote(noteId, fromMessage.principal);
    if (note != null) {
      boolean cronUpdated = isCronUpdated(config, note.getConfig());
      note.setName(name);
      note.setConfig(config);
      if (cronUpdated) {
        notebook.refreshCron(note.id(), fromMessage.principal);
      }

      note.persist();
      broadcastNote(note);
      broadcastNoteList(fromMessage.principal);
    }
  }

  private boolean isCronUpdated(Map<String, Object> configA,
                                Map<String, Object> configB) {
    boolean cronUpdated = false;
    if (configA.get("cron") != null && configB.get("cron") != null
        && configA.get("cron").equals(configB.get("cron"))) {
      cronUpdated = true;
    } else if (configA.get("cron") == null && configB.get("cron") == null) {
      cronUpdated = false;
    } else if (configA.get("cron") != null || configB.get("cron") != null) {
      cronUpdated = true;
    }

    return cronUpdated;
  }

  private void createNote(NotebookSocket conn, Notebook notebook, Message fromMsg)
      throws IOException {
    Note note = notebook.createNote(fromMsg.principal);
    note.addParagraph(); // it's an empty note. so add one paragraph
    if (fromMsg != null) {
      String noteName = (String) fromMsg.get("name");
      if (noteName == null || noteName.isEmpty()) {
        noteName = "Note " + note.getId();
      }
      note.setName(noteName);
    }

    note.persist();
    addConnectionToNote(note.id(), (NotebookSocket) conn);
    broadcastNote(note);
    broadcastNoteList(fromMsg.principal);
  }

  private void removeNote(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }
    Note note = notebook.getNote(noteId, fromMessage.principal);
    note.unpersist();
    notebook.removeNote(noteId, fromMessage.principal);
    removeNote(noteId);
    broadcastNoteList(fromMessage.principal);
  }

  private void updateParagraph(NotebookSocket conn, Notebook notebook,
                               Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    Paragraph p = note.getParagraph(paragraphId);
    p.settings.setParams(params);
    p.setConfig(config);
    p.setTitle((String) fromMessage.get("title"));
    p.setText((String) fromMessage.get("paragraph"));
    note.persist();
    broadcast(note.id(), new Message(OP.PARAGRAPH).put("paragraph", p));
  }

  private void cloneNote(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException, CloneNotSupportedException {
    String noteId = getOpenNoteId(conn);
    String name = (String) fromMessage.get("name");
    Note newNote = notebook.cloneNote(noteId, name, fromMessage.principal);
    addConnectionToNote(newNote.id(), conn);
    broadcastNote(newNote);
    broadcastNoteList(fromMessage.principal);
  }

  protected Note importNote(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {

    Note note = notebook.createNote(fromMessage.principal);
    if (fromMessage != null) {
      String noteName = (String) ((Map) fromMessage.get("notebook")).get("name");
      if (noteName == null || noteName.isEmpty()) {
        noteName = "Note " + note.getId();
      }
      note.setName(noteName);
      ArrayList<Map> paragraphs = ((Map<String, ArrayList>) fromMessage.get("notebook"))
          .get("paragraphs");
      if (paragraphs.size() > 0) {
        for (Map paragraph : paragraphs) {
          try {
            Paragraph p = note.addParagraph();
            String text = (String) paragraph.get("text");
            p.setText(text);
            p.setTitle((String) paragraph.get("title"));
            Map<String, Object> params = (Map<String, Object>) ((Map) paragraph
                .get("settings")).get("params");
            Map<String, Input> forms = (Map<String, Input>) ((Map) paragraph
                .get("settings")).get("forms");
            if (params != null) {
              p.settings.setParams(params);
            }
            if (forms != null) {
              p.settings.setForms(forms);
            }
            Map<String, Object> result = (Map) paragraph.get("result");
            if (result != null) {
              InterpreterResult.Code code = InterpreterResult.Code
                  .valueOf((String) result.get("code"));
              InterpreterResult.Type type = InterpreterResult.Type
                  .valueOf((String) result.get("type"));
              String msg = (String) result.get("msg");
              p.setReturn(new InterpreterResult(code, type, msg), null);
            }

            Map<String, Object> config = (Map<String, Object>) paragraph
                .get("config");
            p.setConfig(config);
          } catch (Exception e) {
            LOG.error("Exception while setting parameter in paragraph", e);
          }
        }
      }
    }

    note.persist();
    broadcastNote(note);
    broadcastNoteList(fromMessage.principal);
    return note;
  }

  private void removeParagraph(NotebookSocket conn, Notebook notebook,
                               Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    /** We don't want to remove the last paragraph */
    if (!note.isLastParagraph(paragraphId)) {
      note.removeParagraph(paragraphId);
      note.persist();
      broadcastNote(note);
    }
  }

  private void clearParagraphOutput(NotebookSocket conn, Notebook notebook,
      Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    note.clearParagraphOutput(paragraphId);
    broadcastNote(note);
  }

  private void completion(NotebookSocket conn, Notebook notebook,
                          Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String buffer = (String) fromMessage.get("buf");
    int cursor = (int) Double.parseDouble(fromMessage.get("cursor").toString());
    Message resp = new Message(OP.COMPLETION_LIST).put("id", paragraphId);
    if (paragraphId == null) {
      conn.send(serializeMessage(resp));
      return;
    }

    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    List<String> candidates = note.completion(paragraphId, buffer, cursor);
    resp.put("completions", candidates);
    conn.send(serializeMessage(resp));
  }

  /**
   * When angular object updated from client
   *
   * @param conn        the web socket.
   * @param notebook    the notebook.
   * @param fromMessage the message.
   */
  private void angularObjectUpdated(NotebookSocket conn, Notebook notebook,
      Message fromMessage) {
    String noteId = (String) fromMessage.get("noteId");
    String interpreterGroupId = (String) fromMessage.get("interpreterGroupId");
    String varName = (String) fromMessage.get("name");
    Object varValue = fromMessage.get("value");
    AngularObject ao = null;
    boolean global = false;
    // propagate change to (Remote) AngularObjectRegistry
    Note note = notebook.getNote(noteId, fromMessage.principal);
    if (note != null) {
      List<InterpreterSetting> settings = note.getNoteReplLoader()
          .getInterpreterSettings();
      for (InterpreterSetting setting : settings) {
        if (setting.getInterpreterGroup() == null) {
          continue;
        }
        if (interpreterGroupId.equals(setting.getInterpreterGroup().getId())) {
          AngularObjectRegistry angularObjectRegistry = setting
              .getInterpreterGroup().getAngularObjectRegistry();
          // first trying to get local registry
          ao = angularObjectRegistry.get(varName, noteId);
          if (ao == null) {
            // then try global registry
            ao = angularObjectRegistry.get(varName, null);
            if (ao == null) {
              LOG.warn("Object {} is not binded", varName);
            } else {
              // path from client -> server
              ao.set(varValue, false);
              global = true;
            }
          } else {
            // path from client -> server
            ao.set(varValue, false);
            global = false;
          }
          break;
        }
      }
    }

    if (global) { // broadcast change to all web session that uses related
      // interpreter.
      for (Note n : notebook.getAllNotes()) {
        List<InterpreterSetting> settings = note.getNoteReplLoader()
            .getInterpreterSettings();
        for (InterpreterSetting setting : settings) {
          if (setting.getInterpreterGroup() == null) {
            continue;
          }
          if (interpreterGroupId.equals(setting.getInterpreterGroup().getId())) {
            AngularObjectRegistry angularObjectRegistry = setting
                .getInterpreterGroup().getAngularObjectRegistry();
            this.broadcastExcept(
                n.id(),
                new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId)
                    .put("noteId", n.id()),
                conn);
          }
        }
      }
    } else { // broadcast to all web session for the note
      this.broadcastExcept(
          note.id(),
          new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
              .put("interpreterGroupId", interpreterGroupId)
              .put("noteId", note.id()),
          conn);
    }
  }

  private void moveParagraph(NotebookSocket conn, Notebook notebook,
                             Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    final int newIndex = (int) Double.parseDouble(fromMessage.get("index").toString());
    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    note.moveParagraph(paragraphId, newIndex);
    note.persist();
    broadcastNote(note);
  }

  private void insertParagraph(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {
    final int index = (int) Double.parseDouble(fromMessage.get("index").toString());
    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    note.insertParagraph(index);
    note.persist();
    broadcastNote(note);
  }

  private void cancelParagraph(NotebookSocket conn, Notebook notebook,
                               Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    Paragraph p = note.getParagraph(paragraphId);
    p.abort();
  }

  private void runParagraph(NotebookSocket conn, Notebook notebook,
                            Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    final Note note = notebook.getNote(getOpenNoteId(conn), fromMessage.principal);
    Paragraph p = note.getParagraph(paragraphId);
    String text = (String) fromMessage.get("paragraph");
    p.setText(text);
    p.setTitle((String) fromMessage.get("title"));
    Map<String, Object> params = (Map<String, Object>) fromMessage
        .get("params");
    p.settings.setParams(params);
    Map<String, Object> config = (Map<String, Object>) fromMessage
        .get("config");
    p.setConfig(config);
    // if it's the last paragraph, let's add a new one
    boolean isTheLastParagraph = note.getLastParagraph().getId()
        .equals(p.getId());
    if (!Strings.isNullOrEmpty(text) && isTheLastParagraph) {
      note.addParagraph();
    }

    note.persist();
    try {
      note.run(paragraphId);
    } catch (Exception ex) {
      LOG.error("Exception from run", ex);
      if (p != null) {
        p.setReturn(
            new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage()),
            ex);
        p.setStatus(Status.ERROR);
      }
    }
  }

  /**
   * Need description here.
   */
  public static class ParagraphJobListener implements JobListener {
    private NotebookServer notebookServer;
    private Note note;

    public ParagraphJobListener(NotebookServer notebookServer, Note note) {
      this.notebookServer = notebookServer;
      this.note = note;
    }

    @Override
    public void onProgressUpdate(Job job, int progress) {
      notebookServer.broadcast(
          note.id(),
          new Message(OP.PROGRESS).put("id", job.getId()).put("progress",
              job.progress()));
    }

    @Override
    public void beforeStatusChange(Job job, Status before, Status after) {
    }

    @Override
    public void afterStatusChange(Job job, Status before, Status after) {
      if (after == Status.ERROR) {
        if (job.getException() != null) {
          LOG.error("Error", job.getException());
        }
      }

      if (job.isTerminated()) {
        LOG.info("Job {} is finished", job.getId());
        try {
          note.persist();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      notebookServer.broadcastNote(note);
    }
  }

  @Override
  public JobListener getParagraphJobListener(Note note) {
    return new ParagraphJobListener(this, note);
  }

  private void sendAllAngularObjects(Note note, NotebookSocket conn) throws IOException {
    List<InterpreterSetting> settings = note.getNoteReplLoader()
        .getInterpreterSettings();
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting intpSetting : settings) {
      AngularObjectRegistry registry = intpSetting.getInterpreterGroup()
          .getAngularObjectRegistry();
      List<AngularObject> objects = registry.getAllWithGlobal(note.id());
      for (AngularObject object : objects) {
        conn.send(serializeMessage(new Message(OP.ANGULAR_OBJECT_UPDATE)
            .put("angularObject", object)
            .put("interpreterGroupId",
                intpSetting.getInterpreterGroup().getId())
            .put("noteId", note.id())));
      }
    }
  }

  @Override
  public void onAdd(String interpreterGroupId, AngularObject object) {
    onUpdate(interpreterGroupId, object);
  }

  @Override
  public void onUpdate(String interpreterGroupId, AngularObject object) {
    Notebook notebook = notebook();
    if (notebook == null) {
      return;
    }

    List<Note> notes = notebook.getAllNotes(object.getPrincipal());
    for (Note note : notes) {
      if (object.getNoteId() != null && !note.id().equals(object.getNoteId())) {
        continue;
      }

      List<InterpreterSetting> intpSettings = note.getNoteReplLoader()
          .getInterpreterSettings();
      if (intpSettings.isEmpty())
        continue;
      for (InterpreterSetting setting : intpSettings) {
        if (setting.getInterpreterGroup().getId().equals(interpreterGroupId)) {
          broadcast(
              note.id(),
              new Message(OP.ANGULAR_OBJECT_UPDATE)
                  .put("angularObject", object)
                  .put("interpreterGroupId", interpreterGroupId)
                  .put("noteId", note.id()));
        }
      }
    }
  }

  @Override
  public void onRemove(String interpreterGroupId, AngularObject object) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes(object.getPrincipal());
    for (Note note : notes) {
      if (object.getNoteId() != null && !note.id().equals(object.getNoteId())) {
        continue;
      }

      List<String> ids = note.getNoteReplLoader().getInterpreters();
      for (String id : ids) {
        if (id.equals(interpreterGroupId)) {
          broadcast(
              note.id(),
              new Message(OP.ANGULAR_OBJECT_REMOVE).put("name", object.getName()).put(
                  "noteId", object.getNoteId()));
        }
      }
    }
  }
}

