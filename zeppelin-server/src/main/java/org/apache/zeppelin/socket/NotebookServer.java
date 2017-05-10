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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.json.NotebookTypeAdapterFactory;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Folder;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.NotebookEventListener;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.notebook.ParagraphRuntimeInfo;
import org.apache.zeppelin.notebook.repo.NotebookRepo.Revision;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.notebook.socket.WatcherMessage;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.types.InterpreterSettingsList;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.apache.zeppelin.utils.InterpreterBindingUtils;
import org.apache.zeppelin.utils.SecurityUtils;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Queues;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * Zeppelin websocket service.
 */
public class NotebookServer extends WebSocketServlet
    implements NotebookSocketListener, JobListenerFactory, AngularObjectRegistryListener,
    RemoteInterpreterProcessListener, ApplicationEventListener {

  /**
   * Job manager service type
   */
  protected enum JOB_MANAGER_SERVICE {
    JOB_MANAGER_PAGE("JOB_MANAGER_PAGE");
    private String serviceTypeKey;

    JOB_MANAGER_SERVICE(String serviceType) {
      this.serviceTypeKey = serviceType;
    }

    String getKey() {
      return this.serviceTypeKey;
    }
  }


  private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);
  Gson gson = new GsonBuilder()
      .registerTypeAdapterFactory(new NotebookTypeAdapterFactory<Paragraph>(Paragraph.class) {
        @Override
        protected void beforeWrite(Paragraph source, JsonElement toSerialize) {
          Map<String, ParagraphRuntimeInfo> runtimeInfos = source.getRuntimeInfos();
          if (runtimeInfos != null) {
            JsonElement jsonTree = gson.toJsonTree(runtimeInfos);
            if (toSerialize instanceof JsonObject) {
              JsonObject jsonObj = (JsonObject) toSerialize;
              jsonObj.add("runtimeInfos", jsonTree);
            }
          }
        }
      }).setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
      .registerTypeAdapterFactory(Input.TypeAdapterFactory).create();

  final Map<String, List<NotebookSocket>> noteSocketMap = new HashMap<>();
  final Queue<NotebookSocket> connectedSockets = new ConcurrentLinkedQueue<>();
  final Map<String, Queue<NotebookSocket>> userConnectedSockets = new ConcurrentHashMap<>();

  /**
   * This is a special endpoint in the notebook websoket, Every connection in this Queue
   * will be able to watch every websocket event, it doesnt need to be listed into the map of
   * noteSocketMap. This can be used to get information about websocket traffic and watch what
   * is going on.
   */
  final Queue<NotebookSocket> watcherSockets = Queues.newConcurrentLinkedQueue();

  private Notebook notebook() {
    return ZeppelinServer.notebook;
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.setCreator(new NotebookWebSocketCreator(this));
  }

  public boolean checkOrigin(HttpServletRequest request, String origin) {
    try {
      return SecurityUtils.isValidOrigin(origin, ZeppelinConfiguration.create());
    } catch (UnknownHostException e) {
      LOG.error(e.toString(), e);
    } catch (URISyntaxException e) {
      LOG.error(e.toString(), e);
    }
    return false;
  }

  public NotebookSocket doWebSocketConnect(HttpServletRequest req, String protocol) {
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
      LOG.debug("RECEIVE << " + messagereceived.op +
          ", RECEIVE PRINCIPAL << " + messagereceived.principal +
          ", RECEIVE TICKET << " + messagereceived.ticket +
          ", RECEIVE ROLES << " + messagereceived.roles +
          ", RECEIVE DATA << " + messagereceived.data);

      if (LOG.isTraceEnabled()) {
        LOG.trace("RECEIVE MSG = " + messagereceived);
      }

      String ticket = TicketContainer.instance.getTicket(messagereceived.principal);
      if (ticket != null &&
          (messagereceived.ticket == null || !ticket.equals(messagereceived.ticket))) {
        /* not to pollute logs, log instead of exception */
        if (StringUtils.isEmpty(messagereceived.ticket)) {
          LOG.debug("{} message: invalid ticket {} != {}", messagereceived.op,
              messagereceived.ticket, ticket);
        } else {
          if (!messagereceived.op.equals(OP.PING)) {
            conn.send(serializeMessage(new Message(OP.SESSION_LOGOUT).put("info",
                "Your ticket is invalid possibly due to server restart. "
                    + "Please login again.")));
          }
        }
        return;
      }

      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      boolean allowAnonymous = conf.isAnonymousAllowed();
      if (!allowAnonymous && messagereceived.principal.equals("anonymous")) {
        throw new Exception("Anonymous access not allowed ");
      }

      HashSet<String> userAndRoles = new HashSet<>();
      userAndRoles.add(messagereceived.principal);
      if (!messagereceived.roles.equals("")) {
        HashSet<String> roles =
            gson.fromJson(messagereceived.roles, new TypeToken<HashSet<String>>() {
            }.getType());
        if (roles != null) {
          userAndRoles.addAll(roles);
        }
      }
      if (StringUtils.isEmpty(conn.getUser())) {
        addUserConnection(messagereceived.principal, conn);
      }
      AuthenticationInfo subject =
          new AuthenticationInfo(messagereceived.principal, messagereceived.ticket);

      /** Lets be elegant here */
      switch (messagereceived.op) {
        case LIST_NOTES:
          unicastNoteList(conn, subject, userAndRoles);
          break;
        case RELOAD_NOTES_FROM_REPO:
          broadcastReloadedNoteList(subject, userAndRoles);
          break;
        case GET_HOME_NOTE:
          sendHomeNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case GET_NOTE:
          sendNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case NEW_NOTE:
          createNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case DEL_NOTE:
          removeNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case REMOVE_FOLDER:
          removeFolder(conn, userAndRoles, notebook, messagereceived);
          break;
        case MOVE_NOTE_TO_TRASH:
          moveNoteToTrash(conn, userAndRoles, notebook, messagereceived);
          break;
        case MOVE_FOLDER_TO_TRASH:
          moveFolderToTrash(conn, userAndRoles, notebook, messagereceived);
          break;
        case EMPTY_TRASH:
          emptyTrash(conn, userAndRoles, notebook, messagereceived);
          break;
        case RESTORE_FOLDER:
          restoreFolder(conn, userAndRoles, notebook, messagereceived);
          break;
        case RESTORE_NOTE:
          restoreNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case RESTORE_ALL:
          restoreAll(conn, userAndRoles, notebook, messagereceived);
          break;
        case CLONE_NOTE:
          cloneNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case IMPORT_NOTE:
          importNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case COMMIT_PARAGRAPH:
          updateParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case RUN_PARAGRAPH:
          runParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case PARAGRAPH_EXECUTED_BY_SPELL:
          broadcastSpellExecution(conn, userAndRoles, notebook, messagereceived);
          break;
        case RUN_ALL_PARAGRAPHS:
          runAllParagraphs(conn, userAndRoles, notebook, messagereceived);
          break;
        case CANCEL_PARAGRAPH:
          cancelParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case MOVE_PARAGRAPH:
          moveParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case INSERT_PARAGRAPH:
          insertParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case COPY_PARAGRAPH:
          copyParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case PARAGRAPH_REMOVE:
          removeParagraph(conn, userAndRoles, notebook, messagereceived);
          break;
        case PARAGRAPH_CLEAR_OUTPUT:
          clearParagraphOutput(conn, userAndRoles, notebook, messagereceived);
          break;
        case PARAGRAPH_CLEAR_ALL_OUTPUT:
          clearAllParagraphOutput(conn, userAndRoles, notebook, messagereceived);
          break;
        case NOTE_UPDATE:
          updateNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case NOTE_RENAME:
          renameNote(conn, userAndRoles, notebook, messagereceived);
          break;
        case FOLDER_RENAME:
          renameFolder(conn, userAndRoles, notebook, messagereceived);
          break;
        case UPDATE_PERSONALIZED_MODE:
          updatePersonalizedMode(conn, userAndRoles, notebook, messagereceived);
          break;
        case COMPLETION:
          completion(conn, userAndRoles, notebook, messagereceived);
          break;
        case PING:
          break; //do nothing
        case ANGULAR_OBJECT_UPDATED:
          angularObjectUpdated(conn, userAndRoles, notebook, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_BIND:
          angularObjectClientBind(conn, userAndRoles, notebook, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_UNBIND:
          angularObjectClientUnbind(conn, userAndRoles, notebook, messagereceived);
          break;
        case LIST_CONFIGURATIONS:
          sendAllConfigurations(conn, userAndRoles, notebook);
          break;
        case CHECKPOINT_NOTE:
          checkpointNote(conn, notebook, messagereceived);
          break;
        case LIST_REVISION_HISTORY:
          listRevisionHistory(conn, notebook, messagereceived);
          break;
        case SET_NOTE_REVISION:
          setNoteRevision(conn, userAndRoles, notebook, messagereceived);
          break;
        case NOTE_REVISION:
          getNoteByRevision(conn, notebook, messagereceived);
          break;
        case LIST_NOTE_JOBS:
          unicastNoteJobInfo(conn, messagereceived);
          break;
        case UNSUBSCRIBE_UPDATE_NOTE_JOBS:
          unsubscribeNoteJobInfo(conn);
          break;
        case GET_INTERPRETER_BINDINGS:
          getInterpreterBindings(conn, messagereceived);
          break;
        case SAVE_INTERPRETER_BINDINGS:
          saveInterpreterBindings(conn, messagereceived);
          break;
        case EDITOR_SETTING:
          getEditorSetting(conn, messagereceived);
          break;
        case GET_INTERPRETER_SETTINGS:
          getInterpreterSettings(conn, subject);
          break;
        case WATCHER:
          switchConnectionToWatcher(conn, messagereceived);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }

  @Override
  public void onClose(NotebookSocket conn, int code, String reason) {
    LOG.info("Closed connection to {} : {}. ({}) {}", conn.getRequest().getRemoteAddr(),
        conn.getRequest().getRemotePort(), code, reason);
    removeConnectionFromAllNote(conn);
    connectedSockets.remove(conn);
    removeUserConnection(conn.getUser(), conn);
  }

  private void removeUserConnection(String user, NotebookSocket conn) {
    if (userConnectedSockets.containsKey(user)) {
      userConnectedSockets.get(user).remove(conn);
    } else {
      LOG.warn("Closing connection that is absent in user connections");
    }
  }

  private void addUserConnection(String user, NotebookSocket conn) {
    conn.setUser(user);
    if (userConnectedSockets.containsKey(user)) {
      userConnectedSockets.get(user).add(conn);
    } else {
      Queue<NotebookSocket> socketQueue = new ConcurrentLinkedQueue<>();
      socketQueue.add(conn);
      userConnectedSockets.put(user, socketQueue);
    }
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
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

  private void broadcastToNoteBindedInterpreter(String interpreterGroupId, Message m) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      List<String> ids = notebook.getInterpreterSettingManager().getInterpreters(note.getId());
      for (String id : ids) {
        if (id.equals(interpreterGroupId)) {
          broadcast(note.getId(), m);
        }
      }
    }
  }

  private void broadcast(String noteId, Message m) {
    synchronized (noteSocketMap) {
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m);
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
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m);
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

  private void multicastToUser(String user, Message m) {
    if (!userConnectedSockets.containsKey(user)) {
      LOG.warn("Multicasting to user {} that is not in connections map", user);
      return;
    }

    for (NotebookSocket conn : userConnectedSockets.get(user)) {
      unicast(m, conn);
    }
  }

  private void unicast(Message m, NotebookSocket conn) {
    try {
      conn.send(serializeMessage(m));
    } catch (IOException e) {
      LOG.error("socket error", e);
    }
    broadcastToWatchers(StringUtils.EMPTY, StringUtils.EMPTY, m);
  }

  public void unicastNoteJobInfo(NotebookSocket conn, Message fromMessage) throws IOException {
    addConnectionToNote(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(), conn);
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    List<Map<String, Object>> noteJobs = notebook().getJobListByUnixTime(false, 0, subject);
    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", noteJobs);

    conn.send(serializeMessage(new Message(OP.LIST_NOTE_JOBS).put("noteJobs", response)));
  }

  public void broadcastUpdateNoteJobInfo(long lastUpdateUnixTime) throws IOException {
    List<Map<String, Object>> noteJobs = new LinkedList<>();
    Notebook notebookObject = notebook();
    List<Map<String, Object>> jobNotes = null;
    if (notebookObject != null) {
      jobNotes = notebook().getJobListByUnixTime(false, lastUpdateUnixTime, null);
      noteJobs = jobNotes == null ? noteJobs : jobNotes;
    }

    Map<String, Object> response = new HashMap<>();
    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", noteJobs != null ? noteJobs : new LinkedList<>());

    broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
        new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
  }

  public void unsubscribeNoteJobInfo(NotebookSocket conn) {
    removeConnectionFromNote(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(), conn);
  }

  public void saveInterpreterBindings(NotebookSocket conn, Message fromMessage) {
    String noteId = (String) fromMessage.data.get("noteId");
    try {
      List<String> settingIdList =
          gson.fromJson(String.valueOf(fromMessage.data.get("selectedSettingIds")),
              new TypeToken<ArrayList<String>>() {
              }.getType());
      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
      notebook().bindInterpretersToNote(subject.getUser(), noteId, settingIdList);
      broadcastInterpreterBindings(noteId,
          InterpreterBindingUtils.getInterpreterBindings(notebook(), noteId));
    } catch (Exception e) {
      LOG.error("Error while saving interpreter bindings", e);
    }
  }

  public void getInterpreterBindings(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.data.get("noteId");
    List<InterpreterSettingsList> settingList =
        InterpreterBindingUtils.getInterpreterBindings(notebook(), noteId);
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_BINDINGS).put("interpreterBindings", settingList)));
  }

  public List<Map<String, String>> generateNotesInfo(boolean needsReload,
      AuthenticationInfo subject, Set<String> userAndRoles) {

    Notebook notebook = notebook();

    ZeppelinConfiguration conf = notebook.getConf();
    String homescreenNoteId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList =
        conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    if (needsReload) {
      try {
        notebook.reloadAllNotes(subject);
      } catch (IOException e) {
        LOG.error("Fail to reload notes from repository", e);
      }
    }

    List<Note> notes = notebook.getAllNotes(userAndRoles);
    List<Map<String, String>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      Map<String, String> info = new HashMap<>();

      if (hideHomeScreenNotebookFromList && note.getId().equals(homescreenNoteId)) {
        continue;
      }

      info.put("id", note.getId());
      info.put("name", note.getName());
      notesInfo.add(info);
    }

    return notesInfo;
  }

  public void broadcastNote(Note note) {
    broadcast(note.getId(), new Message(OP.NOTE).put("note", note));
  }

  public void broadcastInterpreterBindings(String noteId, List settingList) {
    broadcast(noteId, new Message(OP.INTERPRETER_BINDINGS).put("interpreterBindings", settingList));
  }

  public void unicastParagraph(Note note, Paragraph p, String user) {
    if (!note.isPersonalizedMode() || p == null || user == null) {
      return;
    }

    if (!userConnectedSockets.containsKey(user)) {
      LOG.warn("Failed to send unicast. user {} that is not in connections map", user);
      return;
    }

    for (NotebookSocket conn : userConnectedSockets.get(user)) {
      Message m = new Message(OP.PARAGRAPH).put("paragraph", p);
      unicast(m, conn);
    }
  }

  public void broadcastParagraph(Note note, Paragraph p) {
    if (note.isPersonalizedMode()) {
      broadcastParagraphs(p.getUserParagraphMap(), p);
    } else {
      broadcast(note.getId(), new Message(OP.PARAGRAPH).put("paragraph", p));
    }
  }

  public void broadcastParagraphs(Map<String, Paragraph> userParagraphMap,
      Paragraph defaultParagraph) {
    if (null != userParagraphMap) {
      for (String user : userParagraphMap.keySet()) {
        multicastToUser(user,
            new Message(OP.PARAGRAPH).put("paragraph", userParagraphMap.get(user)));
      }
    }
  }

  private void broadcastNewParagraph(Note note, Paragraph para) {
    LOG.info("Broadcasting paragraph on run call instead of note.");
    int paraIndex = note.getParagraphs().indexOf(para);
    broadcast(note.getId(),
        new Message(OP.PARAGRAPH_ADDED).put("paragraph", para).put("index", paraIndex));
  }

  public void broadcastNoteList(AuthenticationInfo subject, HashSet userAndRoles) {
    if (subject == null) {
      subject = new AuthenticationInfo(StringUtils.EMPTY);
    }
    //send first to requesting user
    List<Map<String, String>> notesInfo = generateNotesInfo(false, subject, userAndRoles);
    multicastToUser(subject.getUser(), new Message(OP.NOTES_INFO).put("notes", notesInfo));
    //to others afterwards
    broadcastNoteListExcept(notesInfo, subject);
  }

  public void unicastNoteList(NotebookSocket conn, AuthenticationInfo subject,
      HashSet<String> userAndRoles) {
    List<Map<String, String>> notesInfo = generateNotesInfo(false, subject, userAndRoles);
    unicast(new Message(OP.NOTES_INFO).put("notes", notesInfo), conn);
  }

  public void broadcastReloadedNoteList(AuthenticationInfo subject, HashSet userAndRoles) {
    if (subject == null) {
      subject = new AuthenticationInfo(StringUtils.EMPTY);
    }

    //reload and reply first to requesting user
    List<Map<String, String>> notesInfo = generateNotesInfo(true, subject, userAndRoles);
    multicastToUser(subject.getUser(), new Message(OP.NOTES_INFO).put("notes", notesInfo));
    //to others afterwards
    broadcastNoteListExcept(notesInfo, subject);
  }

  private void broadcastNoteListExcept(List<Map<String, String>> notesInfo,
      AuthenticationInfo subject) {
    Set<String> userAndRoles;
    NotebookAuthorization authInfo = NotebookAuthorization.getInstance();
    for (String user : userConnectedSockets.keySet()) {
      if (subject.getUser().equals(user)) {
        continue;
      }
      //reloaded already above; parameter - false
      userAndRoles = authInfo.getRoles(user);
      userAndRoles.add(user);
      notesInfo = generateNotesInfo(false, new AuthenticationInfo(user), userAndRoles);
      multicastToUser(user, new Message(OP.NOTES_INFO).put("notes", notesInfo));
    }
  }

  void permissionError(NotebookSocket conn, String op, String userName, Set<String> userAndRoles,
      Set<String> allowed) throws IOException {
    LOG.info("Cannot {}. Connection readers {}. Allowed readers {}", op, userAndRoles, allowed);

    conn.send(serializeMessage(new Message(OP.AUTH_INFO).put("info",
        "Insufficient privileges to " + op + " note.\n\n" + "Allowed users or roles: " + allowed
            .toString() + "\n\n" + "But the user " + userName + " belongs to: " + userAndRoles
            .toString())));
  }

  /**
   * @return false if user doesn't have reader permission for this paragraph
   */
  private boolean hasParagraphReaderPermission(NotebookSocket conn,
                                              Notebook notebook, String noteId,
                                              HashSet<String> userAndRoles,
                                              String principal, String op)
      throws IOException {

    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isReader(noteId, userAndRoles)) {
      permissionError(conn, op, principal, userAndRoles,
          notebookAuthorization.getOwners(noteId));
      return false;
    }

    return true;
  }

  /**
   * @return false if user doesn't have writer permission for this paragraph
   */
  private boolean hasParagraphWriterPermission(NotebookSocket conn,
                                               Notebook notebook, String noteId,
                                               HashSet<String> userAndRoles,
                                               String principal, String op)
      throws IOException {

    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(noteId, userAndRoles)) {
      permissionError(conn, op, principal, userAndRoles,
          notebookAuthorization.getOwners(noteId));
      return false;
    }

    return true;
  }

  /**
   * @return false if user doesn't have owner permission for this paragraph
   */
  private boolean hasParagraphOwnerPermission(NotebookSocket conn,
                                              Notebook notebook, String noteId,
                                              HashSet<String> userAndRoles,
                                              String principal, String op)
      throws IOException {

    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isOwner(noteId, userAndRoles)) {
      permissionError(conn, op, principal, userAndRoles,
          notebookAuthorization.getOwners(noteId));
      return false;
    }

    return true;
  }

  private void sendNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {

    LOG.info("New operation from {} : {} : {} : {} : {}", conn.getRequest().getRemoteAddr(),
        conn.getRequest().getRemotePort(), fromMessage.principal, fromMessage.op,
        fromMessage.get("id"));

    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }

    String user = fromMessage.principal;

    Note note = notebook.getNote(noteId);
    if (note != null) {

      if (!hasParagraphReaderPermission(conn, notebook, noteId,
          userAndRoles, fromMessage.principal, "read")) {
        return;
      }

      addConnectionToNote(note.getId(), conn);

      if (note.isPersonalizedMode()) {
        note = note.getUserNote(user);
      }
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, user, conn);
    } else {
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
    }
  }

  private void sendHomeNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    String noteId = notebook.getConf().getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    String user = fromMessage.principal;

    Note note = null;
    if (noteId != null) {
      note = notebook.getNote(noteId);
    }

    if (note != null) {
      if (!hasParagraphReaderPermission(conn, notebook, noteId,
          userAndRoles, fromMessage.principal, "read")) {
        return;
      }

      addConnectionToNote(note.getId(), conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, user, conn);
    } else {
      removeConnectionFromAllNote(conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
    }
  }

  private void updateNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    if (noteId == null) {
      return;
    }
    if (config == null) {
      return;
    }

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "update")) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null) {
      boolean cronUpdated = isCronUpdated(config, note.getConfig());
      note.setName(name);
      note.setConfig(config);
      if (cronUpdated) {
        notebook.refreshCron(note.getId());
      }

      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
      note.persist(subject);
      broadcast(note.getId(), new Message(OP.NOTE_UPDATED).put("name", name).put("config", config)
          .put("info", note.getInfo()));
      broadcastNoteList(subject, userAndRoles);
    }
  }

  private void updatePersonalizedMode(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    String personalized = (String) fromMessage.get("personalized");
    boolean isPersonalized = personalized.equals("true") ? true : false;

    if (noteId == null) {
      return;
    }

    if (!hasParagraphOwnerPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "persoanlized")) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null) {
      note.setPersonalizedMode(isPersonalized);
      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
      note.persist(subject);
      broadcastNote(note);
    }
  }

  private void renameNote(NotebookSocket conn, HashSet<String> userAndRoles,
                          Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    renameNote(conn, userAndRoles, notebook, fromMessage, "rename");
  }

  private void renameNote(NotebookSocket conn, HashSet<String> userAndRoles,
                          Notebook notebook, Message fromMessage, String op)
      throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");

    if (noteId == null) {
      return;
    }

    if (!hasParagraphOwnerPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "rename")) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null) {
      note.setName(name);

      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
      note.persist(subject);
      broadcastNote(note);
      broadcastNoteList(subject, userAndRoles);
    }
  }

  private void renameFolder(NotebookSocket conn, HashSet<String> userAndRoles,
                            Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    renameFolder(conn, userAndRoles, notebook, fromMessage, "rename");
  }

  private void renameFolder(NotebookSocket conn, HashSet<String> userAndRoles,
                          Notebook notebook, Message fromMessage, String op)
      throws SchedulerException, IOException {
    String oldFolderId = (String) fromMessage.get("id");
    String newFolderId = (String) fromMessage.get("name");

    if (oldFolderId == null) {
      return;
    }

    for (Note note : notebook.getNotesUnderFolder(oldFolderId)) {
      String noteId = note.getId();
      if (!hasParagraphOwnerPermission(conn, notebook, noteId,
          userAndRoles, fromMessage.principal, op + " folder of '" + note.getName() + "'")) {
        return;
      }
    }

    Folder oldFolder = notebook.renameFolder(oldFolderId, newFolderId);

    if (oldFolder != null) {
      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);

      List<Note> renamedNotes = oldFolder.getNotesRecursively();
      for (Note note : renamedNotes) {
        note.persist(subject);
        broadcastNote(note);
      }

      broadcastNoteList(subject, userAndRoles);
    }
  }

  private boolean isCronUpdated(Map<String, Object> configA, Map<String, Object> configB) {
    boolean cronUpdated = false;
    if (configA.get("cron") != null && configB.get("cron") != null && configA.get("cron")
        .equals(configB.get("cron"))) {
      cronUpdated = true;
    } else if (configA.get("cron") == null && configB.get("cron") == null) {
      cronUpdated = false;
    } else if (configA.get("cron") != null || configB.get("cron") != null) {
      cronUpdated = true;
    }

    return cronUpdated;
  }

  private void createNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message message) throws IOException {
    AuthenticationInfo subject = new AuthenticationInfo(message.principal);

    try {
      Note note = null;

      String defaultInterpreterId = (String) message.get("defaultInterpreterId");
      if (!StringUtils.isEmpty(defaultInterpreterId)) {
        List<String> interpreterSettingIds = new LinkedList<>();
        interpreterSettingIds.add(defaultInterpreterId);
        for (String interpreterSettingId : notebook.getInterpreterSettingManager().
            getDefaultInterpreterSettingList()) {
          if (!interpreterSettingId.equals(defaultInterpreterId)) {
            interpreterSettingIds.add(interpreterSettingId);
          }
        }
        note = notebook.createNote(interpreterSettingIds, subject);
      } else {
        note = notebook.createNote(subject);
      }

      note.addNewParagraph(subject); // it's an empty note. so add one paragraph
      if (message != null) {
        String noteName = (String) message.get("name");
        if (StringUtils.isEmpty(noteName)) {
          noteName = "Note " + note.getId();
        }
        note.setName(noteName);
      }

      note.persist(subject);
      addConnectionToNote(note.getId(), (NotebookSocket) conn);
      conn.send(serializeMessage(new Message(OP.NEW_NOTE).put("note", note)));
    } catch (FileSystemException e) {
      LOG.error("Exception from createNote", e);
      conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
          "Oops! There is something wrong with the notebook file system. "
              + "Please check the logs for more details.")));
      return;
    }
    broadcastNoteList(subject, userAndRoles);
  }

  private void removeNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }

    if (!hasParagraphOwnerPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "remove")) {
      return;
    }

    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    notebook.removeNote(noteId, subject);
    removeNote(noteId);
    broadcastNoteList(subject, userAndRoles);
  }

  private void removeFolder(NotebookSocket conn, HashSet<String> userAndRoles,
                            Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String folderId = (String) fromMessage.get("id");
    if (folderId == null) {
      return;
    }

    List<Note> notes = notebook.getNotesUnderFolder(folderId);
    for (Note note : notes) {
      String noteId = note.getId();

      if (!hasParagraphOwnerPermission(conn, notebook, noteId,
          userAndRoles, fromMessage.principal, "remove folder of '" + note.getName() + "'")) {
        return;
      }
    }

    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    for (Note note : notes) {
      notebook.removeNote(note.getId(), subject);
      removeNote(note.getId());
    }
    broadcastNoteList(subject, userAndRoles);
  }

  private void moveNoteToTrash(NotebookSocket conn, HashSet<String> userAndRoles,
                               Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null && !note.isTrash()){
      fromMessage.put("name", Folder.TRASH_FOLDER_ID + "/" + note.getName());
      renameNote(conn, userAndRoles, notebook, fromMessage, "move");
      notebook.moveNoteToTrash(note.getId());
    }
  }

  private void moveFolderToTrash(NotebookSocket conn, HashSet<String> userAndRoles,
                                 Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String folderId = (String) fromMessage.get("id");
    if (folderId == null) {
      return;
    }

    Folder folder = notebook.getFolder(folderId);
    if (folder != null && !folder.isTrash()) {
      String trashFolderId = Folder.TRASH_FOLDER_ID + "/" + folderId;
      if (notebook.hasFolder(trashFolderId)){
        DateTime currentDate = new DateTime();
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        trashFolderId += Folder.TRASH_FOLDER_CONFLICT_INFIX + formatter.print(currentDate);
      }

      fromMessage.put("name", trashFolderId);
      renameFolder(conn, userAndRoles, notebook, fromMessage, "move");
    }
  }

  private void restoreNote(NotebookSocket conn, HashSet<String> userAndRoles,
                          Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");

    if (noteId == null) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null && note.isTrash()) {
      fromMessage.put("name", note.getName().replaceFirst(Folder.TRASH_FOLDER_ID + "/", ""));
      renameNote(conn, userAndRoles, notebook, fromMessage, "restore");
    }
  }

  private void restoreFolder(NotebookSocket conn, HashSet<String> userAndRoles,
                            Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    String folderId = (String) fromMessage.get("id");

    if (folderId == null) {
      return;
    }

    Folder folder = notebook.getFolder(folderId);
    if (folder != null && folder.isTrash()) {
      String restoreName = folder.getId().replaceFirst(Folder.TRASH_FOLDER_ID + "/", "").trim();

      // if the folder had conflict when it had moved to trash before
      Pattern p = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$");
      Matcher m = p.matcher(restoreName);
      restoreName = m.replaceAll("").trim();

      fromMessage.put("name", restoreName);
      renameFolder(conn, userAndRoles, notebook, fromMessage, "restore");
    }
  }

  private void restoreAll(NotebookSocket conn, HashSet<String> userAndRoles,
                             Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    Folder trashFolder = notebook.getFolder(Folder.TRASH_FOLDER_ID);
    if (trashFolder != null) {
      fromMessage.data = new HashMap<>();
      fromMessage.put("id", Folder.TRASH_FOLDER_ID);
      fromMessage.put("name", Folder.ROOT_FOLDER_ID);
      renameFolder(conn, userAndRoles, notebook, fromMessage, "restore trash");
    }
  }

  private void emptyTrash(NotebookSocket conn, HashSet<String> userAndRoles,
                          Notebook notebook, Message fromMessage)
      throws SchedulerException, IOException {
    fromMessage.data = new HashMap<>();
    fromMessage.put("id", Folder.TRASH_FOLDER_ID);
    removeFolder(conn, userAndRoles, notebook, fromMessage);
  }

  private void updateParagraph(NotebookSocket conn, HashSet<String> userAndRoles,
                               Notebook notebook, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    String noteId = getOpenNoteId(conn);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    final Note note = notebook.getNote(noteId);
    Paragraph p = note.getParagraph(paragraphId);

    p.settings.setParams(params);
    p.setConfig(config);
    p.setTitle((String) fromMessage.get("title"));
    p.setText((String) fromMessage.get("paragraph"));

    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    if (note.isPersonalizedMode()) {
      p = p.getUserParagraph(subject.getUser());
      p.settings.setParams(params);
      p.setConfig(config);
      p.setTitle((String) fromMessage.get("title"));
      p.setText((String) fromMessage.get("paragraph"));
    }


    note.persist(subject);

    if (note.isPersonalizedMode()) {
      Map<String, Paragraph> userParagraphMap =
          note.getParagraph(paragraphId).getUserParagraphMap();
      broadcastParagraphs(userParagraphMap, p);
    } else {
      broadcastParagraph(note, p);
    }
  }

  private void cloneNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException, CloneNotSupportedException {
    String noteId = getOpenNoteId(conn);
    String name = (String) fromMessage.get("name");
    Note newNote = notebook.cloneNote(noteId, name, new AuthenticationInfo(fromMessage.principal));
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    addConnectionToNote(newNote.getId(), (NotebookSocket) conn);
    conn.send(serializeMessage(new Message(OP.NEW_NOTE).put("note", newNote)));
    broadcastNoteList(subject, userAndRoles);
  }

  private void clearAllParagraphOutput(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws IOException {
    final String noteId = (String) fromMessage.get("id");
    if (StringUtils.isBlank(noteId)) {
      return;
    }

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "clear output")) {
      return;
    }

    Note note = notebook.getNote(noteId);
    note.clearAllParagraphOutput();
    broadcastNote(note);
  }

  protected Note importNote(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    Note note = null;
    if (fromMessage != null) {
      String noteName = (String) ((Map) fromMessage.get("note")).get("name");
      String noteJson = gson.toJson(fromMessage.get("note"));
      AuthenticationInfo subject = null;
      if (fromMessage.principal != null) {
        subject = new AuthenticationInfo(fromMessage.principal);
      } else {
        subject = new AuthenticationInfo("anonymous");
      }
      note = notebook.importNote(noteJson, noteName, subject);
      note.persist(subject);
      broadcastNote(note);
      broadcastNoteList(subject, userAndRoles);
    }
    return note;
  }

  private void removeParagraph(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    String noteId = getOpenNoteId(conn);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    /** We dont want to remove the last paragraph */
    final Note note = notebook.getNote(noteId);
    if (!note.isLastParagraph(paragraphId)) {
      AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
      Paragraph para = note.removeParagraph(subject.getUser(), paragraphId);
      note.persist(subject);
      if (para != null) {
        broadcast(note.getId(), new Message(OP.PARAGRAPH_REMOVED).
            put("id", para.getId()));
      }
    }
  }

  private void clearParagraphOutput(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);
    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    final Note note = notebook.getNote(noteId);
    if (note.isPersonalizedMode()) {
      String user = fromMessage.principal;
      Paragraph p = note.clearPersonalizedParagraphOutput(paragraphId, user);
      unicastParagraph(note, p, user);
    } else {
      note.clearParagraphOutput(paragraphId);
      Paragraph paragraph = note.getParagraph(paragraphId);
      broadcastParagraph(note, paragraph);
    }
  }

  private void completion(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String buffer = (String) fromMessage.get("buf");
    int cursor = (int) Double.parseDouble(fromMessage.get("cursor").toString());
    Message resp = new Message(OP.COMPLETION_LIST).put("id", paragraphId);
    if (paragraphId == null) {
      conn.send(serializeMessage(resp));
      return;
    }

    final Note note = notebook.getNote(getOpenNoteId(conn));
    List<InterpreterCompletion> candidates = note.completion(paragraphId, buffer, cursor);
    resp.put("completions", candidates);
    conn.send(serializeMessage(resp));
  }

  /**
   * When angular object updated from client
   *
   * @param conn the web socket.
   * @param notebook the notebook.
   * @param fromMessage the message.
   */
  private void angularObjectUpdated(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) {
    String noteId = (String) fromMessage.get("noteId");
    String paragraphId = (String) fromMessage.get("paragraphId");
    String interpreterGroupId = (String) fromMessage.get("interpreterGroupId");
    String varName = (String) fromMessage.get("name");
    Object varValue = fromMessage.get("value");
    String user = fromMessage.principal;
    AngularObject ao = null;
    boolean global = false;
    // propagate change to (Remote) AngularObjectRegistry
    Note note = notebook.getNote(noteId);
    if (note != null) {
      List<InterpreterSetting> settings =
          notebook.getInterpreterSettingManager().getInterpreterSettings(note.getId());
      for (InterpreterSetting setting : settings) {
        if (setting.getInterpreterGroup(user, note.getId()) == null) {
          continue;
        }
        if (interpreterGroupId.equals(setting.getInterpreterGroup(user, note.getId()).getId())) {
          AngularObjectRegistry angularObjectRegistry =
              setting.getInterpreterGroup(user, note.getId()).getAngularObjectRegistry();

          // first trying to get local registry
          ao = angularObjectRegistry.get(varName, noteId, paragraphId);
          if (ao == null) {
            // then try notebook scope registry
            ao = angularObjectRegistry.get(varName, noteId, null);
            if (ao == null) {
              // then try global scope registry
              ao = angularObjectRegistry.get(varName, null, null);
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
          } else {
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
        List<InterpreterSetting> settings =
            notebook.getInterpreterSettingManager().getInterpreterSettings(note.getId());
        for (InterpreterSetting setting : settings) {
          if (setting.getInterpreterGroup(user, n.getId()) == null) {
            continue;
          }
          if (interpreterGroupId.equals(setting.getInterpreterGroup(user, n.getId()).getId())) {
            AngularObjectRegistry angularObjectRegistry =
                setting.getInterpreterGroup(user, n.getId()).getAngularObjectRegistry();
            this.broadcastExcept(n.getId(),
                new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId).put("noteId", n.getId())
                    .put("paragraphId", ao.getParagraphId()), conn);
          }
        }
      }
    } else { // broadcast to all web session for the note
      this.broadcastExcept(note.getId(),
          new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
              .put("interpreterGroupId", interpreterGroupId).put("noteId", note.getId())
              .put("paragraphId", ao.getParagraphId()), conn);
    }
  }

  /**
   * Push the given Angular variable to the target
   * interpreter angular registry given a noteId
   * and a paragraph id
   */
  protected void angularObjectClientBind(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    Object varValue = fromMessage.get("value");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook.getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value bind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);

      final AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
      if (registry instanceof RemoteAngularObjectRegistry) {

        RemoteAngularObjectRegistry remoteRegistry = (RemoteAngularObjectRegistry) registry;
        pushAngularObjectToRemoteRegistry(noteId, paragraphId, varName, varValue, remoteRegistry,
            interpreterGroup.getId(), conn);

      } else {
        pushAngularObjectToLocalRepo(noteId, paragraphId, varName, varValue, registry,
            interpreterGroup.getId(), conn);
      }
    }
  }

  /**
   * Remove the given Angular variable to the target
   * interpreter(s) angular registry given a noteId
   * and an optional list of paragraph id(s)
   */
  protected void angularObjectClientUnbind(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook.getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value unBind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);

      final AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();

      if (registry instanceof RemoteAngularObjectRegistry) {
        RemoteAngularObjectRegistry remoteRegistry = (RemoteAngularObjectRegistry) registry;
        removeAngularFromRemoteRegistry(noteId, paragraphId, varName, remoteRegistry,
            interpreterGroup.getId(), conn);
      } else {
        removeAngularObjectFromLocalRepo(noteId, paragraphId, varName, registry,
            interpreterGroup.getId(), conn);
      }
    }
  }

  private InterpreterGroup findInterpreterGroupForParagraph(Note note, String paragraphId)
      throws Exception {
    final Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      throw new IllegalArgumentException("Unknown paragraph with id : " + paragraphId);
    }
    return paragraph.getCurrentRepl().getInterpreterGroup();
  }

  private void pushAngularObjectToRemoteRegistry(String noteId, String paragraphId, String varName,
      Object varValue, RemoteAngularObjectRegistry remoteRegistry, String interpreterGroupId,
      NotebookSocket conn) {

    final AngularObject ao =
        remoteRegistry.addAndNotifyRemoteProcess(varName, varValue, noteId, paragraphId);

    this.broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);
  }

  private void removeAngularFromRemoteRegistry(String noteId, String paragraphId, String varName,
      RemoteAngularObjectRegistry remoteRegistry, String interpreterGroupId, NotebookSocket conn) {
    final AngularObject ao =
        remoteRegistry.removeAndNotifyRemoteProcess(varName, noteId, paragraphId);
    this.broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_REMOVE).put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);
  }

  private void pushAngularObjectToLocalRepo(String noteId, String paragraphId, String varName,
      Object varValue, AngularObjectRegistry registry, String interpreterGroupId,
      NotebookSocket conn) {
    AngularObject angularObject = registry.get(varName, noteId, paragraphId);
    if (angularObject == null) {
      angularObject = registry.add(varName, varValue, noteId, paragraphId);
    } else {
      angularObject.set(varValue, true);
    }

    this.broadcastExcept(noteId,
        new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", angularObject)
            .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
            .put("paragraphId", paragraphId), conn);
  }

  private void removeAngularObjectFromLocalRepo(String noteId, String paragraphId, String varName,
      AngularObjectRegistry registry, String interpreterGroupId, NotebookSocket conn) {
    final AngularObject removed = registry.remove(varName, noteId, paragraphId);
    if (removed != null) {
      this.broadcastExcept(noteId,
          new Message(OP.ANGULAR_OBJECT_REMOVE).put("angularObject", removed)
              .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
              .put("paragraphId", paragraphId), conn);
    }
  }

  private void moveParagraph(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    final int newIndex = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    note.moveParagraph(paragraphId, newIndex);
    note.persist(subject);
    broadcast(note.getId(),
        new Message(OP.PARAGRAPH_MOVED).put("id", paragraphId).put("index", newIndex));
  }

  private String insertParagraph(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook, Message fromMessage) throws IOException {
    final int index = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return null;
    }

    Paragraph newPara = note.insertNewParagraph(index, subject);
    note.persist(subject);
    broadcastNewParagraph(note, newPara);

    return newPara.getId();
  }

  private void copyParagraph(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    String newParaId = insertParagraph(conn, userAndRoles, notebook, fromMessage);

    if (newParaId == null) {
      return;
    }
    fromMessage.put("id", newParaId);

    updateParagraph(conn, userAndRoles, notebook, fromMessage);
  }

  private void cancelParagraph(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    final Note note = notebook.getNote(noteId);
    Paragraph p = note.getParagraph(paragraphId);
    p.abort();
  }

  private void runAllParagraphs(NotebookSocket conn, HashSet<String> userAndRoles,
                                Notebook notebook,
      Message fromMessage) throws IOException {
    final String noteId = (String) fromMessage.get("noteId");
    if (StringUtils.isBlank(noteId)) {
      return;
    }

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "run all paragraphs")) {
      return;
    }

    List<Map<String, Object>> paragraphs =
        gson.fromJson(String.valueOf(fromMessage.data.get("paragraphs")),
            new TypeToken<List<Map<String, Object>>>() {}.getType());

    for (Map<String, Object> raw : paragraphs) {
      String paragraphId = (String) raw.get("id");
      if (paragraphId == null) {
        continue;
      }

      String text = (String) raw.get("paragraph");
      String title = (String) raw.get("title");
      Map<String, Object> params = (Map<String, Object>) raw.get("params");
      Map<String, Object> config = (Map<String, Object>) raw.get("config");

      Note note = notebook.getNote(noteId);
      Paragraph p = setParagraphUsingMessage(note, fromMessage,
          paragraphId, text, title, params, config);

      persistAndExecuteSingleParagraph(conn, note, p);
    }
  }

  private void broadcastSpellExecution(NotebookSocket conn, HashSet<String> userAndRoles,
                                       Notebook notebook, Message fromMessage)
      throws IOException {

    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    String text = (String) fromMessage.get("paragraph");
    String title = (String) fromMessage.get("title");
    Status status = Status.valueOf((String) fromMessage.get("status"));
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");

    final Note note = notebook.getNote(noteId);
    Paragraph p = setParagraphUsingMessage(note, fromMessage, paragraphId,
        text, title, params, config);
    p.setResult(fromMessage.get("results"));
    p.setErrorMessage((String) fromMessage.get("errorMessage"));
    p.setStatusWithoutNotification(status);

    addNewParagraphIfLastParagraphIsExecuted(note, p);
    if (!persistNoteWithAuthInfo(conn, note, p)) {
      return;
    }

    // broadcast to other clients only
    broadcastExcept(note.getId(),
        new Message(OP.RUN_PARAGRAPH_USING_SPELL).put("paragraph", p), conn);
  }

  private void runParagraph(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
                            Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "write")) {
      return;
    }

    // 1. clear paragraph only if personalized,
    // otherwise this will be handed in `onOutputClear`
    final Note note = notebook.getNote(noteId);
    if (note.isPersonalizedMode()) {
      String user = fromMessage.principal;
      Paragraph p = note.clearPersonalizedParagraphOutput(paragraphId, user);
      unicastParagraph(note, p, user);
    }

    // 2. set paragraph values
    String text = (String) fromMessage.get("paragraph");
    String title = (String) fromMessage.get("title");
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");

    Paragraph p = setParagraphUsingMessage(note, fromMessage, paragraphId,
        text, title, params, config);

    persistAndExecuteSingleParagraph(conn, note, p);
  }

  private void addNewParagraphIfLastParagraphIsExecuted(Note note, Paragraph p) {
    // if it's the last paragraph and not empty, let's add a new one
    boolean isTheLastParagraph = note.isLastParagraph(p.getId());
    if (!(Strings.isNullOrEmpty(p.getText()) ||
        p.getText().trim().equals(p.getMagic())) &&
        isTheLastParagraph) {
      Paragraph newPara = note.addNewParagraph(p.getAuthenticationInfo());
      broadcastNewParagraph(note, newPara);
    }
  }

  /**
   * @return false if failed to save a note
   */
  private boolean persistNoteWithAuthInfo(NotebookSocket conn,
                                          Note note, Paragraph p) throws IOException {
    try {
      note.persist(p.getAuthenticationInfo());
      return true;
    } catch (FileSystemException ex) {
      LOG.error("Exception from run", ex);
      conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
          "Oops! There is something wrong with the notebook file system. "
              + "Please check the logs for more details.")));
      // don't run the paragraph when there is error on persisting the note information
      return false;
    }
  }

  private void persistAndExecuteSingleParagraph(NotebookSocket conn,
                                                Note note, Paragraph p) throws IOException {
    addNewParagraphIfLastParagraphIsExecuted(note, p);
    if (!persistNoteWithAuthInfo(conn, note, p)) {
      return;
    }

    try {
      note.run(p.getId());
    } catch (Exception ex) {
      LOG.error("Exception from run", ex);
      if (p != null) {
        p.setReturn(new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage()), ex);
        p.setStatus(Status.ERROR);
        broadcast(note.getId(), new Message(OP.PARAGRAPH).put("paragraph", p));
      }
    }
  }

  private Paragraph setParagraphUsingMessage(Note note, Message fromMessage, String paragraphId,
                                             String text, String title, Map<String, Object> params,
                                             Map<String, Object> config) {
    Paragraph p = note.getParagraph(paragraphId);
    p.setText(text);
    p.setTitle(title);
    AuthenticationInfo subject =
        new AuthenticationInfo(fromMessage.principal, fromMessage.ticket);
    p.setAuthenticationInfo(subject);
    p.settings.setParams(params);
    p.setConfig(config);

    if (note.isPersonalizedMode()) {
      p = note.getParagraph(paragraphId);
      p.setText(text);
      p.setTitle(title);
      p.setAuthenticationInfo(subject);
      p.settings.setParams(params);
      p.setConfig(config);
    }

    return p;
  }

  private void sendAllConfigurations(NotebookSocket conn, HashSet<String> userAndRoles,
      Notebook notebook) throws IOException {
    ZeppelinConfiguration conf = notebook.getConf();

    Map<String, String> configurations =
        conf.dumpConfigurations(conf, new ZeppelinConfiguration.ConfigurationKeyPredicate() {
          @Override
          public boolean apply(String key) {
            return !key.contains("password") && !key.equals(
                ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING
                    .getVarName());
          }
        });

    conn.send(serializeMessage(
        new Message(OP.CONFIGURATIONS_INFO).put("configurations", configurations)));
  }

  private void checkpointNote(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String commitMessage = (String) fromMessage.get("commitMessage");
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    Revision revision = notebook.checkpointNote(noteId, commitMessage, subject);
    if (!Revision.isEmpty(revision)) {
      List<Revision> revisions = notebook.listRevisionHistory(noteId, subject);
      conn.send(
          serializeMessage(new Message(OP.LIST_REVISION_HISTORY).put("revisionList", revisions)));
    } else {
      conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
          "Couldn't checkpoint note revision: possibly storage doesn't support versioning. "
              + "Please check the logs for more details.")));
    }
  }

  private void listRevisionHistory(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    List<Revision> revisions = notebook.listRevisionHistory(noteId, subject);

    conn.send(
        serializeMessage(new Message(OP.LIST_REVISION_HISTORY).put("revisionList", revisions)));
  }

  private void setNoteRevision(NotebookSocket conn, HashSet<String> userAndRoles, Notebook notebook,
      Message fromMessage) throws IOException {

    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);

    if (!hasParagraphWriterPermission(conn, notebook, noteId,
        userAndRoles, fromMessage.principal, "update")) {
      return;
    }

    Note headNote = null;
    boolean setRevisionStatus;
    try {
      headNote = notebook.setNoteRevision(noteId, revisionId, subject);
      setRevisionStatus = headNote != null;
    } catch (Exception e) {
      setRevisionStatus = false;
      LOG.error("Failed to set given note revision", e);
    }
    if (setRevisionStatus) {
      notebook.loadNoteFromRepo(noteId, subject);
    }

    conn.send(serializeMessage(new Message(OP.SET_NOTE_REVISION).put("status", setRevisionStatus)));

    if (setRevisionStatus) {
      Note reloadedNote = notebook.getNote(headNote.getId());
      broadcastNote(reloadedNote);
    } else {
      conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
          "Couldn't set note to the given revision. "
              + "Please check the logs for more details.")));
    }
  }

  private void getNoteByRevision(NotebookSocket conn, Notebook notebook, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    AuthenticationInfo subject = new AuthenticationInfo(fromMessage.principal);
    Note revisionNote = notebook.getNoteByRevision(noteId, revisionId, subject);
    conn.send(serializeMessage(
        new Message(OP.NOTE_REVISION).put("noteId", noteId).put("revisionId", revisionId)
            .put("note", revisionNote)));
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer
   *
   * @param output output to append
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String output) {
    Message msg = new Message(OP.PARAGRAPH_APPEND_OUTPUT).put("noteId", noteId)
        .put("paragraphId", paragraphId).put("index", index).put("data", output);
    broadcast(noteId, msg);
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer
   *
   * @param output output to update (replace)
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index,
      InterpreterResult.Type type, String output) {
    Message msg = new Message(OP.PARAGRAPH_UPDATE_OUTPUT).put("noteId", noteId)
        .put("paragraphId", paragraphId).put("index", index).put("type", type).put("data", output);
    Note note = notebook().getNote(noteId);
    if (note.isPersonalizedMode()) {
      String user = note.getParagraph(paragraphId).getUser();
      if (null != user) {
        multicastToUser(user, msg);
      }
    } else {
      broadcast(noteId, msg);
    }
  }


  /**
   * This callback is for the paragraph that runs on ZeppelinServer
   */
  @Override
  public void onOutputClear(String noteId, String paragraphId) {
    Notebook notebook = notebook();
    final Note note = notebook.getNote(noteId);
    note.clearParagraphOutput(paragraphId);
    Paragraph paragraph = note.getParagraph(paragraphId);
    broadcastParagraph(note, paragraph);
  }

  /**
   * When application append output
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String appId,
      String output) {
    Message msg =
        new Message(OP.APP_APPEND_OUTPUT).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("index", index).put("appId", appId).put("data", output);
    broadcast(noteId, msg);
  }

  /**
   * When application update output
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    Message msg =
        new Message(OP.APP_UPDATE_OUTPUT).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("index", index).put("type", type).put("appId", appId).put("data", output);
    broadcast(noteId, msg);
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    Message msg = new Message(OP.APP_LOAD).put("noteId", noteId).put("paragraphId", paragraphId)
        .put("appId", appId).put("pkg", pkg);
    broadcast(noteId, msg);
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    Message msg =
        new Message(OP.APP_STATUS_CHANGE).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("appId", appId).put("status", status);
    broadcast(noteId, msg);
  }

  @Override
  public void onGetParagraphRunners(String noteId, String paragraphId,
      RemoteWorksEventListener callback) {
    Notebook notebookIns = notebook();
    List<InterpreterContextRunner> runner = new LinkedList<>();

    if (notebookIns == null) {
      LOG.info("intepreter request notebook instance is null");
      callback.onFinished(notebookIns);
    }

    try {
      Note note = notebookIns.getNote(noteId);
      if (note != null) {
        if (paragraphId != null) {
          Paragraph paragraph = note.getParagraph(paragraphId);
          if (paragraph != null) {
            runner.add(paragraph.getInterpreterContextRunner());
          }
        } else {
          for (Paragraph p : note.getParagraphs()) {
            runner.add(p.getInterpreterContextRunner());
          }
        }
      }
      callback.onFinished(runner);
    } catch (NullPointerException e) {
      LOG.warn(e.getMessage());
      callback.onError();
    }
  }

  @Override
  public void onRemoteRunParagraph(String noteId, String paragraphId) throws Exception {
    Notebook notebookIns = notebook();
    try {
      if (notebookIns == null) {
        throw new Exception("onRemoteRunParagraph notebook instance is null");
      }
      Note noteIns = notebookIns.getNote(noteId);
      if (noteIns == null) {
        throw new Exception(String.format("Can't found note id %s", noteId));
      }

      Paragraph paragraph = noteIns.getParagraph(paragraphId);
      if (paragraph == null) {
        throw new Exception(String.format("Can't found paragraph %s %s", noteId, paragraphId));
      }

      Set<String> userAndRoles = Sets.newHashSet();
      userAndRoles.add(SecurityUtils.getPrincipal());
      userAndRoles.addAll(SecurityUtils.getRoles());
      if (!notebookIns.getNotebookAuthorization().hasWriteAuthorization(userAndRoles, noteId)) {
        throw new ForbiddenException(String.format("can't execute note %s", noteId));
      }

      AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
      paragraph.setAuthenticationInfo(subject);

      noteIns.run(paragraphId);

    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * Notebook Information Change event
   */
  public static class NotebookInformationListener implements NotebookEventListener {

    private NotebookServer notebookServer;

    public NotebookInformationListener(NotebookServer notebookServer) {
      this.notebookServer = notebookServer;
    }

    @Override
    public void onParagraphRemove(Paragraph p) {
      try {
        notebookServer.broadcastUpdateNoteJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException ioe) {
        LOG.error("can not broadcast for job manager {}", ioe.getMessage());
      }
    }

    @Override
    public void onNoteRemove(Note note) {
      try {
        notebookServer.broadcastUpdateNoteJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException ioe) {
        LOG.error("can not broadcast for job manager {}", ioe.getMessage());
      }

      List<Map<String, Object>> notesInfo = new LinkedList<>();
      Map<String, Object> info = new HashMap<>();
      info.put("noteId", note.getId());
      // set paragraphs
      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();

      // notebook json object root information.
      info.put("isRunningJob", false);
      info.put("unixTimeLastRun", 0);
      info.put("isRemoved", true);
      info.put("paragraphs", paragraphsInfo);
      notesInfo.add(info);

      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notesInfo);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));

    }

    @Override
    public void onParagraphCreate(Paragraph p) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByParagraphId(p.getId());
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }

    @Override
    public void onNoteCreate(Note note) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByNoteId(note.getId());
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }

    @Override
    public void onParagraphStatusChange(Paragraph p, Status status) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByParagraphId(p.getId());

      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }

    @Override
    public void onUnbindInterpreter(Note note, InterpreterSetting setting) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByNoteId(note.getId());
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }
  }


  /**
   * Need description here.
   */
  public static class ParagraphListenerImpl implements ParagraphJobListener {

    private NotebookServer notebookServer;
    private Note note;

    public ParagraphListenerImpl(NotebookServer notebookServer, Note note) {
      this.notebookServer = notebookServer;
      this.note = note;
    }

    @Override
    public void onProgressUpdate(Job job, int progress) {
      notebookServer.broadcast(note.getId(),
          new Message(OP.PROGRESS).put("id", job.getId()).put("progress", progress));
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
        if (job.getStatus() == Status.FINISHED) {
          LOG.info("Job {} is finished successfully, status: {}", job.getId(), job.getStatus());
        } else {
          LOG.warn("Job {} is finished, status: {}, exception: {}, result: {}" , job.getId(),
              job.getStatus(), job.getException(), job.getReturn());
        }

        try {
          //TODO(khalid): may change interface for JobListener and pass subject from interpreter
          note.persist(job instanceof Paragraph ? ((Paragraph) job).getAuthenticationInfo() : null);
        } catch (IOException e) {
          LOG.error(e.toString(), e);
        }
      }
      if (job instanceof Paragraph) {
        Paragraph p = (Paragraph) job;
        p.setStatusToUserParagraph(job.getStatus());
        notebookServer.broadcastParagraph(note, p);
      }
      try {
        notebookServer.broadcastUpdateNoteJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException e) {
        LOG.error("can not broadcast for job manager {}", e);
      }
    }

    /**
     * This callback is for paragraph that runs on RemoteInterpreterProcess
     */
    @Override
    public void onOutputAppend(Paragraph paragraph, int idx, String output) {
      Message msg =
          new Message(OP.PARAGRAPH_APPEND_OUTPUT).put("noteId", paragraph.getNote().getId())
              .put("paragraphId", paragraph.getId()).put("data", output);

      notebookServer.broadcast(paragraph.getNote().getId(), msg);
    }

    /**
     * This callback is for paragraph that runs on RemoteInterpreterProcess
     */
    @Override
    public void onOutputUpdate(Paragraph paragraph, int idx, InterpreterResultMessage result) {
      String output = result.getData();
      Message msg =
          new Message(OP.PARAGRAPH_UPDATE_OUTPUT).put("noteId", paragraph.getNote().getId())
              .put("paragraphId", paragraph.getId()).put("data", output);

      notebookServer.broadcast(paragraph.getNote().getId(), msg);
    }

    @Override
    public void onOutputUpdateAll(Paragraph paragraph, List<InterpreterResultMessage> msgs) {
      // TODO
    }
  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return new ParagraphListenerImpl(this, note);
  }

  public NotebookEventListener getNotebookInformationListener() {
    return new NotebookInformationListener(this);
  }

  private void sendAllAngularObjects(Note note, String user, NotebookSocket conn)
      throws IOException {
    List<InterpreterSetting> settings =
        notebook().getInterpreterSettingManager().getInterpreterSettings(note.getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting intpSetting : settings) {
      AngularObjectRegistry registry =
          intpSetting.getInterpreterGroup(user, note.getId()).getAngularObjectRegistry();
      List<AngularObject> objects = registry.getAllWithGlobal(note.getId());
      for (AngularObject object : objects) {
        conn.send(serializeMessage(
            new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", object)
                .put("interpreterGroupId",
                    intpSetting.getInterpreterGroup(user, note.getId()).getId())
                .put("noteId", note.getId()).put("paragraphId", object.getParagraphId())));
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

    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      if (object.getNoteId() != null && !note.getId().equals(object.getNoteId())) {
        continue;
      }

      List<InterpreterSetting> intpSettings =
          notebook.getInterpreterSettingManager().getInterpreterSettings(note.getId());
      if (intpSettings.isEmpty()) {
        continue;
      }

      broadcast(note.getId(), new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", object)
          .put("interpreterGroupId", interpreterGroupId).put("noteId", note.getId())
          .put("paragraphId", object.getParagraphId()));
    }
  }

  @Override
  public void onRemove(String interpreterGroupId, String name, String noteId, String paragraphId) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      if (noteId != null && !note.getId().equals(noteId)) {
        continue;
      }

      List<String> settingIds =
          notebook.getInterpreterSettingManager().getInterpreters(note.getId());
      for (String id : settingIds) {
        if (interpreterGroupId.contains(id)) {
          broadcast(note.getId(),
              new Message(OP.ANGULAR_OBJECT_REMOVE).put("name", name).put("noteId", noteId)
                  .put("paragraphId", paragraphId));
          break;
        }
      }
    }
  }

  private void getEditorSetting(NotebookSocket conn, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("paragraphId");
    String replName = (String) fromMessage.get("magic");
    String noteId = getOpenNoteId(conn);
    String user = fromMessage.principal;
    Message resp = new Message(OP.EDITOR_SETTING);
    resp.put("paragraphId", paragraphId);
    Interpreter interpreter =
        notebook().getInterpreterFactory().getInterpreter(user, noteId, replName);
    resp.put("editor", notebook().getInterpreterSettingManager().
        getEditorSetting(interpreter, user, noteId, replName));
    conn.send(serializeMessage(resp));
  }

  private void getInterpreterSettings(NotebookSocket conn, AuthenticationInfo subject)
      throws IOException {
    List<InterpreterSetting> availableSettings = notebook().getInterpreterSettingManager().get();
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_SETTINGS).put("interpreterSettings", availableSettings)));
  }

  @Override
  public void onMetaInfosReceived(String settingId, Map<String, String> metaInfos) {
    InterpreterSetting interpreterSetting =
        notebook().getInterpreterSettingManager().get(settingId);
    interpreterSetting.setInfos(metaInfos);
  }

  private void switchConnectionToWatcher(NotebookSocket conn, Message messagereceived)
      throws IOException {
    if (!isSessionAllowedToSwitchToWatcher(conn)) {
      LOG.error("Cannot switch this client to watcher, invalid security key");
      return;
    }
    LOG.info("Going to add {} to watcher socket", conn);
    // add the connection to the watcher.
    if (watcherSockets.contains(conn)) {
      LOG.info("connection alrerady present in the watcher");
      return;
    }
    watcherSockets.add(conn);

    // remove this connection from regular zeppelin ws usage.
    removeConnectionFromAllNote(conn);
    connectedSockets.remove(conn);
    removeUserConnection(conn.getUser(), conn);
  }

  private boolean isSessionAllowedToSwitchToWatcher(NotebookSocket session) {
    String watcherSecurityKey = session.getRequest().getHeader(WatcherSecurityKey.HTTP_HEADER);
    return !(StringUtils.isBlank(watcherSecurityKey) || !watcherSecurityKey
        .equals(WatcherSecurityKey.getKey()));
  }

  /**
   * Send websocket message to all connections regardless of notebook id
   */
  private void broadcastToAllConnections(String serialized) {
    broadcastToAllConnectionsExcept(null, serialized);
  }

  private void broadcastToAllConnectionsExcept(NotebookSocket exclude, String serialized) {
    synchronized (connectedSockets) {
      for (NotebookSocket conn: connectedSockets) {
        if (exclude != null && exclude.equals(conn)) {
          continue;
        }

        try {
          conn.send(serialized);
        } catch (IOException e) {
          LOG.error("Cannot broadcast message to watcher", e);
        }
      }
    }
  }

  private void broadcastToWatchers(String noteId, String subject, Message message) {
    synchronized (watcherSockets) {
      for (NotebookSocket watcher : watcherSockets) {
        try {
          watcher.send(
              WatcherMessage.builder(noteId).subject(subject).message(serializeMessage(message))
                  .build().serialize());
        } catch (IOException e) {
          LOG.error("Cannot broadcast message to watcher", e);
        }
      }
    }
  }

  @Override
  public void onParaInfosReceived(String noteId, String paragraphId,
      String interpreterSettingId, Map<String, String> metaInfos) {
    Note note = notebook().getNote(noteId);
    if (note != null) {
      Paragraph paragraph = note.getParagraph(paragraphId);
      if (paragraph != null) {
        InterpreterSetting setting = notebook().getInterpreterSettingManager()
            .get(interpreterSettingId);
        setting.addNoteToPara(noteId, paragraphId);
        String label = metaInfos.get("label");
        String tooltip = metaInfos.get("tooltip");
        List<String> keysToRemove = Arrays.asList("noteId", "paraId", "label", "tooltip");
        for (String removeKey : keysToRemove) {
          metaInfos.remove(removeKey);
        }
        paragraph
            .updateRuntimeInfos(label, tooltip, metaInfos, setting.getGroup(), setting.getId());
        broadcast(
            note.getId(),
            new Message(OP.PARAS_INFO).put("id", paragraphId).put("infos",
                paragraph.getRuntimeInfos()));
      }
    }
  }

  public void clearParagraphRuntimeInfo(InterpreterSetting setting) {
    Map<String, Set<String>> noteIdAndParaMap = setting.getNoteIdAndParaMap();
    if (noteIdAndParaMap != null && !noteIdAndParaMap.isEmpty()) {
      for (String noteId : noteIdAndParaMap.keySet()) {
        Set<String> paraIdSet = noteIdAndParaMap.get(noteId);
        if (paraIdSet != null && !paraIdSet.isEmpty()) {
          for (String paraId : paraIdSet) {
            Note note = notebook().getNote(noteId);
            if (note != null) {
              Paragraph paragraph = note.getParagraph(paraId);
              if (paragraph != null) {
                paragraph.clearRuntimeInfo(setting.getId());
                broadcast(noteId, new Message(OP.PARAGRAPH).put("paragraph", paragraph));
              }
            }
          }
        }
      }
    }
    setting.clearNoteIdAndParaMap();
  }
}
