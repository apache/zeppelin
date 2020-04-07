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


import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.WatcherMessage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manager class for managing websocket connections
 */
public class ConnectionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);
  private static Gson gson = new GsonBuilder()
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .setPrettyPrinting()
      .registerTypeAdapterFactory(Input.TypeAdapterFactory).create();

  final Queue<NotebookSocket> connectedSockets = new ConcurrentLinkedQueue<>();
  // noteId -> connection
  final Map<String, List<NotebookSocket>> noteSocketMap = new ConcurrentHashMap<>();
  // user -> connection
  final Map<String, Queue<NotebookSocket>> userSocketMap = new ConcurrentHashMap<>();

  /**
   * This is a special endpoint in the notebook websoket, Every connection in this Queue
   * will be able to watch every websocket event, it doesnt need to be listed into the map of
   * noteSocketMap. This can be used to get information about websocket traffic and watch what
   * is going on.
   */
  final Queue<NotebookSocket> watcherSockets = Queues.newConcurrentLinkedQueue();

  private HashSet<String> collaborativeModeList = new HashSet<>();
  private Boolean collaborativeModeEnable = ZeppelinConfiguration
      .create()
      .isZeppelinNotebookCollaborativeModeEnable();


  private AuthorizationService authorizationService;

  @Inject
  public ConnectionManager(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  public void addConnection(NotebookSocket conn) {
    connectedSockets.add(conn);
  }

  public void removeConnection(NotebookSocket conn) {
    connectedSockets.remove(conn);
  }

  public void addNoteConnection(String noteId, NotebookSocket socket) {
    LOGGER.debug("Add connection {} to note: {}", socket, noteId);
    synchronized (noteSocketMap) {
      // make sure a socket relates only an single note.
      removeConnectionFromAllNote(socket);
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList == null) {
        socketList = new LinkedList<>();
        noteSocketMap.put(noteId, socketList);
      }
      if (!socketList.contains(socket)) {
        socketList.add(socket);
      }
      checkCollaborativeStatus(noteId, socketList);
    }
  }

  public void removeNoteConnection(String noteId) {
    synchronized (noteSocketMap) {
      noteSocketMap.remove(noteId);
    }
  }

  public void removeNoteConnection(String noteId, NotebookSocket socket) {
    LOGGER.debug("Remove connection {} from note: {}", socket, noteId);
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList != null) {
        socketList.remove(socket);
      }
      checkCollaborativeStatus(noteId, socketList);
    }
  }

  public void addUserConnection(String user, NotebookSocket conn) {
    LOGGER.debug("Add user connection {} for user: {}", conn, user);
    conn.setUser(user);
    if (userSocketMap.containsKey(user)) {
      userSocketMap.get(user).add(conn);
    } else {
      Queue<NotebookSocket> socketQueue = new ConcurrentLinkedQueue<>();
      socketQueue.add(conn);
      userSocketMap.put(user, socketQueue);
    }
  }

  public void removeUserConnection(String user, NotebookSocket conn) {
    LOGGER.debug("Remove user connection {} for user: {}", conn, user);
    if (userSocketMap.containsKey(user)) {
      userSocketMap.get(user).remove(conn);
    } else {
      LOGGER.warn("Closing connection that is absent in user connections");
    }
  }

  public String getAssociatedNoteId(NotebookSocket socket) {
    String associatedNoteId = null;
    synchronized (noteSocketMap) {
      Set<String> noteIds = noteSocketMap.keySet();
      for (String noteId : noteIds) {
        List<NotebookSocket> sockets = noteSocketMap.get(noteId);
        if (sockets.contains(socket)) {
          associatedNoteId = noteId;
        }
      }
    }

    return associatedNoteId;
  }

  public void removeConnectionFromAllNote(NotebookSocket socket) {
    synchronized (noteSocketMap) {
      Set<String> noteIds = noteSocketMap.keySet();
      for (String noteId : noteIds) {
        removeConnectionFromNote(noteId, socket);
      }
    }
  }

  private void removeConnectionFromNote(String noteId, NotebookSocket socket) {
    LOGGER.debug("Remove connection {} from note: {}", socket, noteId);
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList != null) {
        socketList.remove(socket);
      }
      checkCollaborativeStatus(noteId, socketList);
    }
  }

  private void checkCollaborativeStatus(String noteId, List<NotebookSocket> socketList) {
    if (!collaborativeModeEnable) {
      return;
    }
    boolean collaborativeStatusNew = socketList.size() > 1;
    if (collaborativeStatusNew) {
      collaborativeModeList.add(noteId);
    } else {
      collaborativeModeList.remove(noteId);
    }

    Message message = new Message(Message.OP.COLLABORATIVE_MODE_STATUS);
    message.put("status", collaborativeStatusNew);
    if (collaborativeStatusNew) {
      HashSet<String> userList = new HashSet<>();
      for (NotebookSocket noteSocket : socketList) {
        userList.add(noteSocket.getUser());
      }
      message.put("users", userList);
    }
    broadcast(noteId, message);
  }


  protected String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  public void broadcast(Message m) {
    synchronized (connectedSockets) {
      for (NotebookSocket ns : connectedSockets) {
        try {
          ns.send(serializeMessage(m));
        } catch (IOException | WebSocketException e) {
          LOGGER.error("Send error: " + m, e);
        }
      }
    }
  }

  public void broadcast(String noteId, Message m) {
    List<NotebookSocket> socketsToBroadcast = Collections.emptyList();
    synchronized (noteSocketMap) {
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      socketsToBroadcast = new ArrayList<>(socketLists);
    }
    LOGGER.debug("SEND >> " + m);
    for (NotebookSocket conn : socketsToBroadcast) {
      try {
        conn.send(serializeMessage(m));
      } catch (IOException | WebSocketException e) {
        LOGGER.error("socket error", e);
      }
    }
  }

  private void broadcastToWatchers(String noteId, String subject, Message message) {
    synchronized (watcherSockets) {
      for (NotebookSocket watcher : watcherSockets) {
        try {
          watcher.send(
              WatcherMessage.builder(noteId)
                  .subject(subject)
                  .message(serializeMessage(message))
                  .build()
                  .toJson());
        } catch (IOException | WebSocketException e) {
          LOGGER.error("Cannot broadcast message to watcher", e);
        }
      }
    }
  }

  public void broadcastExcept(String noteId, Message m, NotebookSocket exclude) {
    List<NotebookSocket> socketsToBroadcast = Collections.emptyList();
    synchronized (noteSocketMap) {
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      socketsToBroadcast = new ArrayList<>(socketLists);
    }

    LOGGER.debug("SEND >> " + m);
    for (NotebookSocket conn : socketsToBroadcast) {
      if (exclude.equals(conn)) {
        continue;
      }
      try {
        conn.send(serializeMessage(m));
      } catch (IOException | WebSocketException e) {
        LOGGER.error("socket error", e);
      }
    }
  }

  /**
   * Send websocket message to all connections regardless of notebook id.
   */
  public void broadcastToAllConnections(String serialized) {
    broadcastToAllConnectionsExcept(null, serialized);
  }

  public void broadcastToAllConnectionsExcept(NotebookSocket exclude, String serializedMsg) {
    synchronized (connectedSockets) {
      for (NotebookSocket conn : connectedSockets) {
        if (exclude != null && exclude.equals(conn)) {
          continue;
        }

        try {
          conn.send(serializedMsg);
        } catch (IOException | WebSocketException e) {
          LOGGER.error("Cannot broadcast message to conn", e);
        }
      }
    }
  }

  public Set<String> getConnectedUsers() {
    Set<String> connectedUsers = Sets.newHashSet();
    for (NotebookSocket notebookSocket : connectedSockets) {
      connectedUsers.add(notebookSocket.getUser());
    }
    return connectedUsers;
  }


  public void multicastToUser(String user, Message m) {
    if (!userSocketMap.containsKey(user)) {
      LOGGER.warn("Multicasting to user {} that is not in connections map", user);
      return;
    }

    for (NotebookSocket conn : userSocketMap.get(user)) {
      unicast(m, conn);
    }
  }

  public void unicast(Message m, NotebookSocket conn) {
    try {
      conn.send(serializeMessage(m));
    } catch (IOException | WebSocketException e) {
      LOGGER.error("socket error", e);
    }
    broadcastToWatchers(StringUtils.EMPTY, StringUtils.EMPTY, m);
  }

  public void unicastParagraph(Note note, Paragraph p, String user) {
    if (!note.isPersonalizedMode() || p == null || user == null) {
      return;
    }

    if (!userSocketMap.containsKey(user)) {
      LOGGER.warn("Failed to send unicast. user {} that is not in connections map", user);
      return;
    }

    for (NotebookSocket conn : userSocketMap.get(user)) {
      Message m = new Message(Message.OP.PARAGRAPH).put("paragraph", p);
      unicast(m, conn);
    }
  }

  public void broadcastNoteListExcept(List<NoteInfo> notesInfo,
                                      AuthenticationInfo subject) {
    Set<String> userAndRoles;
    for (String user : userSocketMap.keySet()) {
      if (subject.getUser().equals(user)) {
        continue;
      }
      //reloaded already above; parameter - false
      userAndRoles = authorizationService.getRoles(user);
      userAndRoles.add(user);
      // TODO(zjffdu) is it ok for comment the following line ?
      // notesInfo = generateNotesInfo(false, new AuthenticationInfo(user), userAndRoles);
      multicastToUser(user, new Message(Message.OP.NOTES_INFO).put("notes", notesInfo));
    }
  }

  public void broadcastNote(Note note) {
    broadcast(note.getId(), new Message(Message.OP.NOTE).put("note", note));
  }

  public void broadcastParagraph(Note note, Paragraph p) {
    broadcastNoteForms(note);

    if (note.isPersonalizedMode()) {
      broadcastParagraphs(p.getUserParagraphMap(), p);
    } else {
      broadcast(note.getId(), new Message(Message.OP.PARAGRAPH).put("paragraph", p));
    }
  }

  public void broadcastParagraphs(Map<String, Paragraph> userParagraphMap,
                                  Paragraph defaultParagraph) {
    if (null != userParagraphMap) {
      for (String user : userParagraphMap.keySet()) {
        multicastToUser(user,
            new Message(Message.OP.PARAGRAPH).put("paragraph", userParagraphMap.get(user)));
      }
    }
  }

  private void broadcastNewParagraph(Note note, Paragraph para) {
    LOGGER.info("Broadcasting paragraph on run call instead of note.");
    int paraIndex = note.getParagraphs().indexOf(para);
    broadcast(note.getId(),
        new Message(Message.OP.PARAGRAPH_ADDED).put("paragraph", para).put("index", paraIndex));
  }

  //  public void broadcastNoteList(AuthenticationInfo subject, Set<String> userAndRoles) {
  //    if (subject == null) {
  //      subject = new AuthenticationInfo(StringUtils.EMPTY);
  //    }
  //    //send first to requesting user
  //    List<Map<String, String>> notesInfo = generateNotesInfo(false, subject, userAndRoles);
  //    multicastToUser(subject.getUser(), new Message(Message.OP.NOTES_INFO)
  // .put("notes", notesInfo));
  //    //to others afterwards
  //    broadcastNoteListExcept(notesInfo, subject);
  //  }


  private void broadcastNoteForms(Note note) {
    GUI formsSettings = new GUI();
    formsSettings.setForms(note.getNoteForms());
    formsSettings.setParams(note.getNoteParams());
    broadcast(note.getId(), new Message(Message.OP.SAVE_NOTE_FORMS)
        .put("formsData", formsSettings));
  }

  public void switchConnectionToWatcher(NotebookSocket conn) {
    if (!isSessionAllowedToSwitchToWatcher(conn)) {
      LOGGER.error("Cannot switch this client to watcher, invalid security key");
      return;
    }
    LOGGER.info("Going to add {} to watcher socket", conn);
    // add the connection to the watcher.
    if (watcherSockets.contains(conn)) {
      LOGGER.info("connection alrerady present in the watcher");
      return;
    }
    watcherSockets.add(conn);

    // remove this connection from regular zeppelin ws usage.
    removeConnection(conn);
    removeConnectionFromAllNote(conn);
    removeUserConnection(conn.getUser(), conn);
  }

  private boolean isSessionAllowedToSwitchToWatcher(NotebookSocket session) {
    String watcherSecurityKey = session.getRequest().getHeader(WatcherSecurityKey.HTTP_HEADER);
    return !(StringUtils.isBlank(watcherSecurityKey) || !watcherSecurityKey
        .equals(WatcherSecurityKey.getKey()));
  }
}
