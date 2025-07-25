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


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.notebook.socket.WatcherMessage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manager class for managing websocket connections
 */
public class ConnectionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);
  private static final Gson gson = new GsonBuilder()
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .setPrettyPrinting()
      .registerTypeAdapterFactory(Input.TypeAdapterFactory).create();

  final Queue<NotebookSocket> connectedSockets = Metrics.gaugeCollectionSize("zeppelin_connected_sockets", Tags.empty(), new ConcurrentLinkedQueue<>());
  // noteId -> connection
  final Map<String, Set<NotebookSocket>> noteSocketMap = Metrics.gaugeMapSize("zeppelin_note_sockets", Tags.empty(), new HashMap<>());
  // user -> connection
  final Map<String, Queue<NotebookSocket>> userSocketMap = Metrics.gaugeMapSize("zeppelin_user_sockets", Tags.empty(), new HashMap<>());

  /**
   * This is a special endpoint in the notebook websocket, Every connection in this Queue
   * will be able to watch every websocket event, it doesnt need to be listed into the map of
   * noteSocketMap. This can be used to get information about websocket traffic and watch what
   * is going on.
   */
  final Queue<NotebookSocket> watcherSockets = new ConcurrentLinkedQueue<>();

  private final HashSet<String> collaborativeModeList = Metrics.gaugeCollectionSize("zeppelin_collaborative_modes", Tags.empty(),new HashSet<>());

  private final AuthorizationService authorizationService;
  private final ZeppelinConfiguration zConf;

  @Inject
  public ConnectionManager(AuthorizationService authorizationService, ZeppelinConfiguration zConf) {
    this.authorizationService = authorizationService;
    this.zConf = zConf;
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
      Set<NotebookSocket> sockets = noteSocketMap.computeIfAbsent(noteId, k -> new HashSet<>());
      sockets.add(socket);
      checkCollaborativeStatus(noteId, sockets);
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
      Set<NotebookSocket> sockets = noteSocketMap.getOrDefault(noteId, Collections.emptySet());
      removeNoteConnection(noteId, sockets, socket);
      // Remove empty socket collection from map
      if (sockets.isEmpty()) {
        noteSocketMap.remove(noteId);
      }
    }
  }

  private void removeNoteConnection(String noteId, Set<NotebookSocket> sockets,
    NotebookSocket socket) {
    sockets.remove(socket);
    checkCollaborativeStatus(noteId, sockets);
  }

  public void removeConnectionFromAllNote(NotebookSocket socket) {
    LOGGER.debug("Remove connection {} from all notes", socket);
    synchronized (noteSocketMap) {
      Iterator<Entry<String, Set<NotebookSocket>>> iterator = noteSocketMap.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<String, Set<NotebookSocket>> noteSocketMapEntry = iterator.next();
        removeNoteConnection(noteSocketMapEntry.getKey(), noteSocketMapEntry.getValue(), socket);
        // Remove empty socket collection from map
        if (noteSocketMapEntry.getValue().isEmpty()) {
          iterator.remove();
        }
      }
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
      Queue<NotebookSocket> connections = userSocketMap.get(user);
      connections.remove(conn);
      if (connections.isEmpty()) {
        userSocketMap.remove(user);
      }
    } else {
      LOGGER.warn("Closing connection that is absent in user connections");
    }
  }

  public void removeWatcherConnection(NotebookSocket conn) {
    synchronized (watcherSockets) {
      if (watcherSockets.remove(conn)) {
        LOGGER.debug("Removed watcher connection: {}", conn);
      }
    }
  }

  public String getAssociatedNoteId(NotebookSocket socket) {
    String associatedNoteId = null;
    synchronized (noteSocketMap) {
      for (Entry<String, Set<NotebookSocket>> noteSocketMapEntry : noteSocketMap.entrySet()) {
        if (noteSocketMapEntry.getValue().contains(socket)) {
          associatedNoteId = noteSocketMapEntry.getKey();
        }
      }
    }

    return associatedNoteId;
  }

  private void checkCollaborativeStatus(String noteId, Set<NotebookSocket> socketList) {
    if (!zConf.isZeppelinNotebookCollaborativeModeEnable()) {
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
        } catch (IOException | RuntimeException e) {
          LOGGER.error("Send error: {}", m, e);
        }
      }
    }
  }

  public void broadcast(String noteId, Message m) {
    List<NotebookSocket> socketsToBroadcast;
    synchronized (noteSocketMap) {
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      Set<NotebookSocket> sockets = noteSocketMap.get(noteId);
      if (sockets == null || sockets.isEmpty()) {
        return;
      }
      socketsToBroadcast = new ArrayList<>(sockets);
    }
    LOGGER.debug("SEND >> {}", m);
    for (NotebookSocket conn : socketsToBroadcast) {
      try {
        conn.send(serializeMessage(m));
      } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
          LOGGER.error("Cannot broadcast message to watcher", e);
        }
      }
    }
  }

  public void broadcastExcept(String noteId, Message m, NotebookSocket exclude) {
    List<NotebookSocket> socketsToBroadcast;
    synchronized (noteSocketMap) {
      broadcastToWatchers(noteId, StringUtils.EMPTY, m);
      Set<NotebookSocket> socketSet = noteSocketMap.get(noteId);
      if (socketSet == null || socketSet.isEmpty()) {
        return;
      }
      socketsToBroadcast = new ArrayList<>(socketSet);
    }

    LOGGER.debug("SEND >> {}", m);
    for (NotebookSocket conn : socketsToBroadcast) {
      if (exclude.equals(conn)) {
        continue;
      }
      try {
        conn.send(serializeMessage(m));
      } catch (IOException | RuntimeException e) {
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
        } catch (IOException | RuntimeException e) {
          LOGGER.error("Cannot broadcast message to conn", e);
        }
      }
    }
  }

  public Set<String> getConnectedUsers() {
    Set<String> connectedUsers = new HashSet<>();
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
    } catch (IOException | RuntimeException e) {
      LOGGER.error("socket error", e);
    }
    broadcastToWatchers(StringUtils.EMPTY, StringUtils.EMPTY, m);
  }

  public void unicastParagraph(Note note, Paragraph p, String user, String msgId) {
    if (!note.isPersonalizedMode() || p == null || user == null) {
      return;
    }

    if (!userSocketMap.containsKey(user)) {
      LOGGER.warn("Failed to send unicast. user {} that is not in connections map", user);
      return;
    }

    for (NotebookSocket conn : userSocketMap.get(user)) {
      Message m = new Message(Message.OP.PARAGRAPH).withMsgId(msgId).put("paragraph", p);
      unicast(m, conn);
    }
  }

  public interface UserIterator {
    void handleUser(String user, Set<String> userAndRoles);
  }

  public void forAllUsers(UserIterator iterator) {
    for (String user : userSocketMap.keySet()) {
      Set<String> userAndRoles = authorizationService.getRoles(user);
      userAndRoles.add(user);
      iterator.handleUser(user, userAndRoles);
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
      broadcastParagraphs(p.getUserParagraphMap());
    } else {
      broadcast(note.getId(), new Message(Message.OP.PARAGRAPH).put("paragraph", p));
    }
  }

  public void broadcastParagraphs(Map<String, Paragraph> userParagraphMap) {
    if (null != userParagraphMap) {
      for (Entry<String, Paragraph> userParagraphEntry : userParagraphMap.entrySet()) {
        multicastToUser(userParagraphEntry.getKey(),
            new Message(Message.OP.PARAGRAPH).put("paragraph", userParagraphEntry.getValue()));
      }
    }
  }

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
      LOGGER.info("connection already present in the watcher");
      return;
    }
    watcherSockets.add(conn);

    // remove this connection from regular zeppelin ws usage.
    removeConnection(conn);
    removeConnectionFromAllNote(conn);
    removeUserConnection(conn.getUser(), conn);
  }

  private boolean isSessionAllowedToSwitchToWatcher(NotebookSocket notebookSocket) {
    String watcherSecurityKey = notebookSocket.getHeader(WatcherSecurityKey.HTTP_HEADER);
    return !(StringUtils.isBlank(watcherSecurityKey) || !watcherSecurityKey
        .equals(WatcherSecurityKey.getKey()));
  }
}
