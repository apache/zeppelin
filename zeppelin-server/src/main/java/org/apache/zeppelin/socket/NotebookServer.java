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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.thrift.TException;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.event.ClusterEvent;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.event.ClusterMessage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.jupyter.JupyterUtil;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl.Revision;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.JobManagerService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.types.InterpreterSettingsList;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.IdHashes;
import org.apache.zeppelin.utils.CorsUtils;
import org.apache.zeppelin.utils.TestUtils;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin websocket service. This class used setter injection because all servlet should have
 * no-parameter constructor
 */
@ManagedObject
public class NotebookServer extends WebSocketServlet
    implements NotebookSocketListener,
        AngularObjectRegistryListener,
        RemoteInterpreterProcessListener,
        ApplicationEventListener,
        ParagraphJobListener,
        NoteEventListener,
        ClusterEventListener {

  /**
   * Job manager service type.
   */
  protected enum JobManagerServiceType {
    JOB_MANAGER_PAGE("JOB_MANAGER_PAGE");
    private String serviceTypeKey;

    JobManagerServiceType(String serviceType) {
      this.serviceTypeKey = serviceType;
    }

    String getKey() {
      return this.serviceTypeKey;
    }
  }


  private Boolean collaborativeModeEnable = ZeppelinConfiguration
      .create()
      .isZeppelinNotebookCollaborativeModeEnable();
  private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);
  private static Gson gson = new GsonBuilder()
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .setPrettyPrinting()
      .registerTypeAdapterFactory(Input.TypeAdapterFactory).create();
  private static AtomicReference<NotebookServer> self = new AtomicReference<>();

  private ExecutorService executorService = Executors.newFixedThreadPool(10);

  private Provider<Notebook> notebookProvider;
  private Provider<NotebookService> notebookServiceProvider;
  private Provider<AuthorizationService> authorizationServiceProvider;
  private Provider<ConfigurationService> configurationServiceProvider;
  private Provider<JobManagerService> jobManagerServiceProvider;
  private Provider<ConnectionManager> connectionManagerProvider;

  public NotebookServer() {
    NotebookServer.self.set(this);
    LOG.info("NotebookServer instantiated: {}", this);
  }

  @Inject
  public void setServiceLocator(ServiceLocator serviceLocator) {
    LOG.info("Injected ServiceLocator: {}", serviceLocator);
  }

  @Inject
  public void setNotebook(Provider<Notebook> notebookProvider) {
    this.notebookProvider = notebookProvider;
    LOG.info("Injected NotebookProvider");
  }

  @Inject
  public void setNotebookService(
      Provider<NotebookService> notebookServiceProvider) {
    this.notebookServiceProvider = notebookServiceProvider;
    LOG.info("Injected NotebookServiceProvider");
  }

  @Inject
  public void setAuthorizationServiceProvider(Provider<AuthorizationService>
                                                      authorizationServiceProvider) {
    this.authorizationServiceProvider = authorizationServiceProvider;
    LOG.info("Injected NotebookAuthorizationServiceProvider");
  }

  @Inject
  public void setConnectionManagerProvider(Provider<ConnectionManager> connectionManagerProvider) {
    this.connectionManagerProvider = connectionManagerProvider;
    LOG.info("Injected ConnectionManagerProvider");
  }

  @Inject
  public void setConfigurationService(
      Provider<ConfigurationService> configurationServiceProvider) {
    this.configurationServiceProvider = configurationServiceProvider;
  }

  @Inject
  public void setJobManagerService(
      Provider<JobManagerService> jobManagerServiceProvider) {
    this.jobManagerServiceProvider = jobManagerServiceProvider;
  }

  public static NotebookServer getInstance() {
    return TestUtils.getInstance(NotebookServer.class);
  }

  public Notebook getNotebook() {
    return notebookProvider.get();
  }

  public ConnectionManager getConnectionManager() {
    return connectionManagerProvider.get();
  }

  public NotebookService getNotebookService() {
    return notebookServiceProvider.get();
  }

  public ConfigurationService getConfigurationService() {
    return configurationServiceProvider.get();
  }

  public synchronized JobManagerService getJobManagerService() {
    return jobManagerServiceProvider.get();
  }

  public AuthorizationService getNotebookAuthorizationService() {
    return authorizationServiceProvider.get();
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.setCreator(new NotebookWebSocketCreator(this));
  }

  public boolean checkOrigin(HttpServletRequest request, String origin) {
    try {
      return CorsUtils.isValidOrigin(origin, ZeppelinConfiguration.create());
    } catch (UnknownHostException | URISyntaxException e) {
      LOG.error(e.toString(), e);
    }
    return false;
  }

  @Override
  public void onOpen(NotebookSocket conn) {
    LOG.info("New connection from {}", conn);
    getConnectionManager().addConnection(conn);
  }

  @Override
  public void onMessage(NotebookSocket conn, String msg) {
    try {
      Message messagereceived = deserializeMessage(msg);
      if (messagereceived.op != OP.PING) {
        LOG.debug("RECEIVE: " + messagereceived.op +
            ", RECEIVE PRINCIPAL: " + messagereceived.principal +
            ", RECEIVE TICKET: " + messagereceived.ticket +
            ", RECEIVE ROLES: " + messagereceived.roles +
            ", RECEIVE DATA: " + messagereceived.data);
      }
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
        LOG.warn("Anonymous access not allowed.");
        return;
      }

      if (Message.isDisabledForRunningNotes(messagereceived.op)) {
        Note note = getNotebook().getNote((String) messagereceived.get("noteId"));
        if (note != null && note.isRunning()) {
          throw new Exception("Note is now running sequentially. Can not be performed: " +
                  messagereceived.op);
        }
      }

      if (StringUtils.isEmpty(conn.getUser())) {
        getConnectionManager().addUserConnection(messagereceived.principal, conn);
      }

      // Lets be elegant here
      switch (messagereceived.op) {
        case LIST_NOTES:
          listNotesInfo(conn, messagereceived);
          break;
        case RELOAD_NOTES_FROM_REPO:
          broadcastReloadedNoteList(conn, getServiceContext(messagereceived));
          break;
        case GET_HOME_NOTE:
          getHomeNote(conn, messagereceived);
          break;
        case GET_NOTE:
          getNote(conn, messagereceived);
          break;
        case NEW_NOTE:
          createNote(conn, messagereceived);
          break;
        case DEL_NOTE:
          deleteNote(conn, messagereceived);
          break;
        case REMOVE_FOLDER:
          removeFolder(conn, messagereceived);
          break;
        case MOVE_NOTE_TO_TRASH:
          moveNoteToTrash(conn, messagereceived);
          break;
        case MOVE_FOLDER_TO_TRASH:
          moveFolderToTrash(conn, messagereceived);
          break;
        case EMPTY_TRASH:
          emptyTrash(conn, messagereceived);
          break;
        case RESTORE_FOLDER:
          restoreFolder(conn, messagereceived);
          break;
        case RESTORE_NOTE:
          restoreNote(conn, messagereceived);
          break;
        case RESTORE_ALL:
          restoreAll(conn, messagereceived);
          break;
        case CLONE_NOTE:
          cloneNote(conn, messagereceived);
          break;
        case IMPORT_NOTE:
          importNote(conn, messagereceived);
          break;
        case CONVERT_NOTE_NBFORMAT:
          convertNote(conn, messagereceived);
          break;
        case COMMIT_PARAGRAPH:
          updateParagraph(conn, messagereceived);
          break;
        case RUN_PARAGRAPH:
          runParagraph(conn, messagereceived);
          break;
        case PARAGRAPH_EXECUTED_BY_SPELL:
          broadcastSpellExecution(conn, messagereceived);
          break;
        case RUN_ALL_PARAGRAPHS:
          runAllParagraphs(conn, messagereceived);
          break;
        case CANCEL_PARAGRAPH:
          cancelParagraph(conn, messagereceived);
          break;
        case MOVE_PARAGRAPH:
          moveParagraph(conn, messagereceived);
          break;
        case INSERT_PARAGRAPH:
          insertParagraph(conn, messagereceived);
          break;
        case COPY_PARAGRAPH:
          copyParagraph(conn, messagereceived);
          break;
        case PARAGRAPH_REMOVE:
          removeParagraph(conn, messagereceived);
          break;
        case PARAGRAPH_CLEAR_OUTPUT:
          clearParagraphOutput(conn, messagereceived);
          break;
        case PARAGRAPH_CLEAR_ALL_OUTPUT:
          clearAllParagraphOutput(conn, messagereceived);
          break;
        case NOTE_UPDATE:
          updateNote(conn, messagereceived);
          break;
        case NOTE_RENAME:
          renameNote(conn, messagereceived);
          break;
        case FOLDER_RENAME:
          renameFolder(conn, messagereceived);
          break;
        case UPDATE_PERSONALIZED_MODE:
          updatePersonalizedMode(conn, messagereceived);
          break;
        case COMPLETION:
          completion(conn, messagereceived);
          break;
        case PING:
          break; //do nothing
        case ANGULAR_OBJECT_UPDATED:
          angularObjectUpdated(conn, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_BIND:
          angularObjectClientBind(conn, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_UNBIND:
          angularObjectClientUnbind(conn, messagereceived);
          break;
        case LIST_CONFIGURATIONS:
          sendAllConfigurations(conn, messagereceived);
          break;
        case CHECKPOINT_NOTE:
          checkpointNote(conn, messagereceived);
          break;
        case LIST_REVISION_HISTORY:
          listRevisionHistory(conn, messagereceived);
          break;
        case SET_NOTE_REVISION:
          setNoteRevision(conn, messagereceived);
          break;
        case NOTE_REVISION:
          getNoteByRevision(conn, messagereceived);
          break;
        case NOTE_REVISION_FOR_COMPARE:
          getNoteByRevisionForCompare(conn, messagereceived);
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
          getInterpreterSettings(conn, messagereceived);
          break;
        case WATCHER:
          getConnectionManager().switchConnectionToWatcher(conn);
          break;
        case SAVE_NOTE_FORMS:
          saveNoteForms(conn, messagereceived);
          break;
        case REMOVE_NOTE_FORMS:
          removeNoteForms(conn, messagereceived);
          break;
        case PATCH_PARAGRAPH:
          patchParagraph(conn, messagereceived);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message: " + msg, e);
      try {
        conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info", e.getMessage())));
      } catch (IOException iox) {
        LOG.error("Fail to send error info", iox);
      }
    }
  }

  @Override
  public void onClose(NotebookSocket conn, int code, String reason) {
    LOG.info("Closed connection to {} ({}) {}", conn, code, reason);
    getConnectionManager().removeConnection(conn);
    getConnectionManager().removeConnectionFromAllNote(conn);
    getConnectionManager().removeUserConnection(conn.getUser(), conn);
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  public void broadcast(Message m) {
    getConnectionManager().broadcast(m);
  }

  public void unicastNoteJobInfo(NotebookSocket conn, Message fromMessage) throws IOException {

    getConnectionManager().addNoteConnection(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(), conn);
    getJobManagerService().getNoteJobInfoByUnixTime(0, getServiceContext(fromMessage),
        new WebSocketServiceCallback<List<JobManagerService.NoteJobInfo>>(conn) {
          @Override
          public void onSuccess(List<JobManagerService.NoteJobInfo> notesJobInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesJobInfo, context);
            Map<String, Object> response = new HashMap<>();
            response.put("lastResponseUnixTime", System.currentTimeMillis());
            response.put("jobs", notesJobInfo);
            conn.send(serializeMessage(new Message(OP.LIST_NOTE_JOBS).put("noteJobs", response)));
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            LOG.warn(ex.getMessage());
          }
        });
  }

  public void broadcastUpdateNoteJobInfo(Note note, long lastUpdateUnixTime) throws IOException {
    ServiceContext context = new ServiceContext(new AuthenticationInfo(),
            getNotebookAuthorizationService().getOwners(note.getId()));
    getJobManagerService().getNoteJobInfoByUnixTime(lastUpdateUnixTime, context,
        new WebSocketServiceCallback<List<JobManagerService.NoteJobInfo>>(null) {
          @Override
          public void onSuccess(List<JobManagerService.NoteJobInfo> notesJobInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesJobInfo, context);
            Map<String, Object> response = new HashMap<>();
            response.put("lastResponseUnixTime", System.currentTimeMillis());
            response.put("jobs", notesJobInfo);
            getConnectionManager().broadcast(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(),
                new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            LOG.warn(ex.getMessage());
          }
        });
  }

  public void unsubscribeNoteJobInfo(NotebookSocket conn) {
    getConnectionManager().removeNoteConnection(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(), conn);
  }

  public void getInterpreterBindings(NotebookSocket conn, Message fromMessage) throws IOException {
    List<InterpreterSettingsList> settingList = new ArrayList<>();
    ServiceContext context = getServiceContext(fromMessage);
    String noteId = (String) fromMessage.data.get("noteId");
    Note note = getNotebook().getNote(noteId);
    if (note != null) {
      List<InterpreterSetting> bindedSettings =
              note.getBindedInterpreterSettings(new ArrayList<>(context.getUserAndRoles()));
      for (InterpreterSetting setting : bindedSettings) {
        settingList.add(new InterpreterSettingsList(setting.getId(), setting.getName(),
                setting.getInterpreterInfos(), true));
      }
    }
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_BINDINGS).put("interpreterBindings", settingList)));
  }

  public void saveInterpreterBindings(NotebookSocket conn, Message fromMessage) throws IOException {
    List<InterpreterSettingsList> settingList = new ArrayList<>();
    String noteId = (String) fromMessage.data.get("noteId");
    ServiceContext context = getServiceContext(fromMessage);
    Note note = getNotebook().getNote(noteId);
    if (note != null) {
      List<String> settingIdList =
              gson.fromJson(String.valueOf(fromMessage.data.get("selectedSettingIds")),
                      new TypeToken<ArrayList<String>>() {}.getType());
      if (!settingIdList.isEmpty()) {
        note.setDefaultInterpreterGroup(settingIdList.get(0));
        getNotebook().saveNote(note,
                new AuthenticationInfo(fromMessage.principal, fromMessage.roles, fromMessage.ticket));
      }
      List<InterpreterSetting> bindedSettings =
              note.getBindedInterpreterSettings(new ArrayList<>(context.getUserAndRoles()));
      for (InterpreterSetting setting : bindedSettings) {
        settingList.add(new InterpreterSettingsList(setting.getId(), setting.getName(),
                setting.getInterpreterInfos(), true));
      }
    }

    conn.send(serializeMessage(
            new Message(OP.INTERPRETER_BINDINGS).put("interpreterBindings", settingList)));
  }

  public void broadcastNote(Note note) {
    inlineBroadcastNote(note);
    broadcastClusterEvent(ClusterEvent.BROADCAST_NOTE, note);
  }

  private void inlineBroadcastNote(Note note) {
    Message message = new Message(OP.NOTE).put("note", note);
    getConnectionManager().broadcast(note.getId(), message);
  }

  private void inlineBroadcastParagraph(Note note, Paragraph p) {
    broadcastNoteForms(note);

    if (note.isPersonalizedMode()) {
      broadcastParagraphs(p.getUserParagraphMap(), p);
    } else {
      Message message = new Message(OP.PARAGRAPH).put("paragraph", p);
      getConnectionManager().broadcast(note.getId(), message);
    }
  }

  public void broadcastParagraph(Note note, Paragraph p) {
    inlineBroadcastParagraph(note, p);
    broadcastClusterEvent(ClusterEvent.BROADCAST_PARAGRAPH, note, p);
  }

  private void inlineBroadcastParagraphs(Map<String, Paragraph> userParagraphMap,
                                         Paragraph defaultParagraph) {
    if (null != userParagraphMap) {
      for (String user : userParagraphMap.keySet()) {
        Message message = new Message(OP.PARAGRAPH).put("paragraph", userParagraphMap.get(user));
        getConnectionManager().multicastToUser(user, message);
      }
    }
  }

  private void broadcastParagraphs(Map<String, Paragraph> userParagraphMap,
                                  Paragraph defaultParagraph) {
    inlineBroadcastParagraphs(userParagraphMap, defaultParagraph);
    broadcastClusterEvent(ClusterEvent.BROADCAST_PARAGRAPHS, userParagraphMap, defaultParagraph);
  }

  private void inlineBroadcastNewParagraph(Note note, Paragraph para) {
    LOG.info("Broadcasting paragraph on run call instead of note.");
    int paraIndex = note.getParagraphs().indexOf(para);

    Message message = new Message(OP.PARAGRAPH_ADDED).put("paragraph", para).put("index", paraIndex);
    getConnectionManager().broadcast(note.getId(), message);
  }

  private void broadcastNewParagraph(Note note, Paragraph para) {
    inlineBroadcastNewParagraph(note, para);
    broadcastClusterEvent(ClusterEvent.BROADCAST_NEW_PARAGRAPH, note, para);
  }

  public void inlineBroadcastNoteList(AuthenticationInfo subject, Set<String> userAndRoles) {
    if (subject == null) {
      subject = new AuthenticationInfo(StringUtils.EMPTY);
    }
    //send first to requesting user
    AuthorizationService authorizationService = getNotebookAuthorizationService();
    List<NoteInfo> notesInfo = getNotebook().getNotesInfo(
        noteId -> authorizationService.isReader(noteId, userAndRoles));
    Message message = new Message(OP.NOTES_INFO).put("notes", notesInfo);
    getConnectionManager().multicastToUser(subject.getUser(), message);
    //to others afterwards
    getConnectionManager().broadcastNoteListExcept(notesInfo, subject);
  }

  public void broadcastNoteList(AuthenticationInfo subject, Set<String> userAndRoles) {
    inlineBroadcastNoteList(subject, userAndRoles);
    broadcastClusterEvent(ClusterEvent.BROADCAST_NOTE_LIST, subject, userAndRoles);
  }

  // broadcast ClusterEvent
  private void broadcastClusterEvent(ClusterEvent event, Object... objects) {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    if (!conf.isClusterMode()) {
      return;
    }

    ClusterMessage clusterMessage = new ClusterMessage(event);

    for(Object object : objects) {
      String json = "";
      if (object instanceof AuthenticationInfo) {
        json = ((AuthenticationInfo) object).toJson();
        clusterMessage.put("AuthenticationInfo", json);
      } else if (object instanceof Note) {
        json = ((Note) object).toJson();
        clusterMessage.put("Note", json);
      } else if (object instanceof Paragraph) {
        json = ((Paragraph) object).toJson();
        clusterMessage.put("Paragraph", json);
      } else if (object instanceof Set) {
        Gson gson = new Gson();
        json = gson.toJson(object);
        clusterMessage.put("Set<String>", json);
      } else if (object instanceof Map) {
        Gson gson = new Gson();
        json = gson.toJson(object);
        clusterMessage.put("Map<String, Paragraph>", json);
      } else {
        LOG.error("Unknown object type!");
      }
    }

    String msg = ClusterMessage.serializeMessage(clusterMessage);
    ClusterManagerServer.getInstance(conf).broadcastClusterEvent(
        ClusterManagerServer.CLUSTER_NOTE_EVENT_TOPIC, msg);
  }

  @Override
  public void onClusterEvent(String msg) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("onClusterEvent : {}", msg);
    }
    ClusterMessage message = ClusterMessage.deserializeMessage(msg);

    Note note = null;
    Paragraph paragraph = null;
    Set<String> userAndRoles = null;
    Map<String, Paragraph> userParagraphMap = null;
    AuthenticationInfo authenticationInfo = null;
    for (Map.Entry<String, String> entry : message.getData().entrySet()) {
      String key = entry.getKey();
      String json = entry.getValue();
      if (StringUtils.equals(key, "AuthenticationInfo")) {
        authenticationInfo = AuthenticationInfo.fromJson(json);
      } else if (StringUtils.equals(key, "Note")) {
        try {
          note = Note.fromJson(json);
        } catch (IOException e) {
          LOG.warn("Fail to parse note json", e);
        }
      } else if (StringUtils.equals(key, "Paragraph")) {
        paragraph = Paragraph.fromJson(json);
      } else if (StringUtils.equals(key, "Set<String>")) {
        Gson gson = new Gson();
        userAndRoles = gson.fromJson(json, new TypeToken<Set<String>>() {
        }.getType());
      } else if (StringUtils.equals(key, "Map<String, Paragraph>")) {
        Gson gson = new Gson();
        userParagraphMap = gson.fromJson(json, new TypeToken<Map<String, Paragraph>>() {
        }.getType());
      } else {
        LOG.error("Unknown key:{}, json:{}!" + key, json);
      }
    }

    switch (message.clusterEvent) {
      case BROADCAST_NOTE:
        inlineBroadcastNote(note);
        break;
      case BROADCAST_NOTE_LIST:
        try {
          getNotebook().reloadAllNotes(authenticationInfo);
          inlineBroadcastNoteList(authenticationInfo, userAndRoles);
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        }
        break;
      case BROADCAST_PARAGRAPH:
        inlineBroadcastParagraph(note, paragraph);
        break;
      case BROADCAST_PARAGRAPHS:
        inlineBroadcastParagraphs(userParagraphMap, paragraph);
        break;
      case BROADCAST_NEW_PARAGRAPH:
        inlineBroadcastNewParagraph(note, paragraph);
        break;
      default:
        LOG.error("Unknown clusterEvent:{}, msg:{} ", message.clusterEvent, msg);
        break;
    }
  }

  public void listNotesInfo(NotebookSocket conn, Message message) throws IOException {
    getNotebookService().listNotesInfo(false, getServiceContext(message),
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> notesInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesInfo, context);
            getConnectionManager().unicast(new Message(OP.NOTES_INFO).put("notes", notesInfo), conn);
          }
        });
  }

  public void broadcastReloadedNoteList(NotebookSocket conn, ServiceContext context)
      throws IOException {
    getNotebookService().listNotesInfo(true, context,
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> notesInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesInfo, context);
            getConnectionManager().multicastToUser(context.getAutheInfo().getUser(),
                new Message(OP.NOTES_INFO).put("notes", notesInfo));
            //to others afterwards
            getConnectionManager().broadcastNoteListExcept(notesInfo, context.getAutheInfo());
          }
        });
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
   * @return false if user doesn't have writer permission for this paragraph
   */
  private boolean hasParagraphWriterPermission(NotebookSocket conn, Notebook notebook,
                                               String noteId, Set<String> userAndRoles,
                                               String principal, String op)
      throws IOException {
    AuthorizationService authorizationService =
            getNotebookAuthorizationService();
    if (!authorizationService.isWriter(noteId, userAndRoles)) {
      permissionError(conn, op, principal, userAndRoles,
              authorizationService.getOwners(noteId));
      return false;
    }

    return true;
  }

  private void getNote(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }
    getNotebookService().getNote(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            getConnectionManager().addNoteConnection(note.getId(), conn);
            conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
            updateAngularObjectRegistry(conn, note);
            sendAllAngularObjects(note, context.getAutheInfo().getUser(), conn);
          }
        });
  }

  /**
   * Update the AngularObject object in the note to InterpreterGroup and AngularObjectRegistry.
   */
  private void updateAngularObjectRegistry(NotebookSocket conn, Note note) {
    for(Paragraph paragraph : note.getParagraphs()) {
      InterpreterGroup interpreterGroup = null;
      try {
        interpreterGroup = findInterpreterGroupForParagraph(note, paragraph.getId());
      } catch (Exception e) {
        LOG.warn(e.getMessage(), e);
      }
      if (null == interpreterGroup) {
        return;
      }
      RemoteAngularObjectRegistry registry = (RemoteAngularObjectRegistry)
          interpreterGroup.getAngularObjectRegistry();

      List<AngularObject> angularObjects = note.getAngularObjects(interpreterGroup.getId());
      for (AngularObject ao : angularObjects) {
        if (StringUtils.equals(ao.getNoteId(), note.getId())
            && StringUtils.equals(ao.getParagraphId(), paragraph.getId())) {
          pushAngularObjectToRemoteRegistry(ao.getNoteId(), ao.getParagraphId(),
              ao.getName(), ao.get(), registry, interpreterGroup.getId(), conn);
        }
      }
    }
  }

  private void getHomeNote(NotebookSocket conn,
                           Message fromMessage) throws IOException {

    getNotebookService().getHomeNote(getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            if (note != null) {
              getConnectionManager().addNoteConnection(note.getId(), conn);
              conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
              sendAllAngularObjects(note, context.getAutheInfo().getUser(), conn);
            } else {
              getConnectionManager().removeConnectionFromAllNote(conn);
              conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
            }
          }
        });
  }

  private void updateNote(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    if (noteId == null) {
      return;
    }
    if (config == null) {
      return;
    }

    getNotebookService().updateNote(noteId, name, config, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            getConnectionManager().broadcast(note.getId(), new Message(OP.NOTE_UPDATED).put("name", name)
                .put("config", config)
                .put("info", note.getInfo()));
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void updatePersonalizedMode(NotebookSocket conn,
                                      Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    String personalized = (String) fromMessage.get("personalized");
    boolean isPersonalized = personalized.equals("true") ? true : false;

    getNotebookService().updatePersonalizedMode(noteId, isPersonalized,
        getServiceContext(fromMessage), new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            getConnectionManager().broadcastNote(note);
          }
        });
  }

  private void renameNote(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");
    boolean isRelativePath = false;
    if (fromMessage.get("relative") != null) {
      isRelativePath = (boolean) fromMessage.get("relative");
    }
    if (noteId == null) {
      return;
    }
    getNotebookService().renameNote(noteId, name, isRelativePath, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            broadcastNote(note);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void renameFolder(NotebookSocket conn,
                            Message fromMessage) throws IOException {
    String oldFolderId = (String) fromMessage.get("id");
    String newFolderId = (String) fromMessage.get("name");
    getNotebookService().renameFolder(oldFolderId, newFolderId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void createNote(NotebookSocket conn,
                          Message message) throws IOException {

    String noteName = (String) message.get("name");
    String defaultInterpreterGroup = (String) message.get("defaultInterpreterGroup");

    getNotebookService().createNote(noteName, defaultInterpreterGroup, getServiceContext(message),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            getConnectionManager().addNoteConnection(note.getId(), conn);
            conn.send(serializeMessage(new Message(OP.NEW_NOTE).put("note", note)));
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            super.onFailure(ex, context);
            conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
                "Failed to create note.\n" + ExceptionUtils.getMessage(ex))));
          }
        });
  }

  private void deleteNote(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    getNotebookService().removeNote(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<String>(conn) {
          @Override
          public void onSuccess(String message, ServiceContext context) throws IOException {
            super.onSuccess(message, context);
            getConnectionManager().removeNoteConnection(noteId);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void removeFolder(NotebookSocket conn,
                            Message fromMessage) throws IOException {

    String folderPath = (String) fromMessage.get("id");
    folderPath = "/" + folderPath;
    getNotebookService().removeFolder(folderPath, getServiceContext(fromMessage),
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> notesInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesInfo, context);
            for (NoteInfo noteInfo : notesInfo) {
              getConnectionManager().removeNoteConnection(noteInfo.getId());
            }
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void moveNoteToTrash(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    getNotebookService().moveNoteToTrash(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            broadcastNote(note);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void moveFolderToTrash(NotebookSocket conn,
                                 Message fromMessage)
      throws IOException {

    String folderPath = (String) fromMessage.get("id");
    getNotebookService().moveFolderToTrash(folderPath, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Void>(conn) {
          @Override
          public void onSuccess(Void result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });

  }

  private void restoreNote(NotebookSocket conn,
                           Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    getNotebookService().restoreNote(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            broadcastNote(note);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });

  }

  private void restoreFolder(NotebookSocket conn,
                             Message fromMessage) throws IOException {
    String folderPath = (String) fromMessage.get("id");
    folderPath = "/" + folderPath;
    getNotebookService().restoreFolder(folderPath, getServiceContext(fromMessage),
        new WebSocketServiceCallback(conn) {
          @Override
          public void onSuccess(Object result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void restoreAll(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    getNotebookService().restoreAll(getServiceContext(fromMessage),
        new WebSocketServiceCallback(conn) {
          @Override
          public void onSuccess(Object result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void emptyTrash(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    getNotebookService().emptyTrash(getServiceContext(fromMessage),
        new WebSocketServiceCallback(conn) {
          @Override
          public void onSuccess(Object result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void updateParagraph(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    if (noteId == null) {
      noteId = (String) fromMessage.get("noteId");
    }
    String title = (String) fromMessage.get("title");
    String text = (String) fromMessage.get("paragraph");
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");

    getNotebookService().updateParagraph(noteId, paragraphId, title, text, params, config,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            if (p.getNote().isPersonalizedMode()) {
              Map<String, Paragraph> userParagraphMap =
                  p.getNote().getParagraph(paragraphId).getUserParagraphMap();
              broadcastParagraphs(userParagraphMap, p);
            } else {
              broadcastParagraph(p.getNote(), p);
            }
          }
        });
  }

  private void patchParagraph(NotebookSocket conn,
                              Message fromMessage) throws IOException {
    if (!collaborativeModeEnable) {
      return;
    }
    String paragraphId = fromMessage.getType("id", LOG);
    if (paragraphId == null) {
      return;
    }

    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    if (noteId == null) {
      noteId = fromMessage.getType("noteId", LOG);
      if (noteId == null) {
        return;
      }
    }
    final String noteId2 = noteId;
    String patchText = fromMessage.getType("patch", LOG);
    if (patchText == null) {
      return;
    }

    getNotebookService().patchParagraph(noteId, paragraphId, patchText,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<String>(conn) {
          @Override
          public void onSuccess(String result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            Message message = new Message(OP.PATCH_PARAGRAPH).put("patch", result)
                .put("paragraphId", paragraphId);
            getConnectionManager().broadcastExcept(noteId2, message, conn);
          }
        });
  }

  private void cloneNote(NotebookSocket conn,
                         Message fromMessage) throws IOException {
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    String name = (String) fromMessage.get("name");
    getNotebookService().cloneNote(noteId, name, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note newNote, ServiceContext context) throws IOException {
            super.onSuccess(newNote, context);
            getConnectionManager().addNoteConnection(newNote.getId(), conn);
            conn.send(serializeMessage(new Message(OP.NEW_NOTE).put("note", newNote)));
            broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
          }
        });
  }

  private void clearAllParagraphOutput(NotebookSocket conn,
                                       Message fromMessage) throws IOException {
    final String noteId = (String) fromMessage.get("id");
    getNotebookService().clearAllParagraphOutput(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            broadcastNote(note);
          }
        });
  }

  protected void convertNote(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteId = fromMessage.get("noteId").toString();
    Note note = getNotebook().getNote(noteId);
    if (note == null) {
      throw new IOException("No such note: " + noteId);
    } else {
      Message resp = new Message(OP.CONVERTED_NOTE_NBFORMAT)
              .put("nbformat", new JupyterUtil().getNbformat(note.toJson()))
              .put("noteName", fromMessage.get("noteName"));
      conn.send(serializeMessage(resp));
    }
  }

  protected Note importNote(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteJson = null;
    String noteName = (String) ((Map) fromMessage.get("note")).get("name");
    // Checking whether the notebook data is from a Jupyter or a Zeppelin Notebook.
    // Jupyter notebooks have paragraphs under the "cells" label.
    if (((Map) fromMessage.get("note")).get("cells") == null) {
      noteJson = gson.toJson(fromMessage.get("note"));
    } else {
      noteJson = new JupyterUtil().getJson(
              gson.toJson(fromMessage.get("note")), IdHashes.generateId(), "%python", "%md");
    }
    Note note = getNotebookService().importNote(noteName, noteJson, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            try {
              broadcastNote(note);
              broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
            } catch (NullPointerException e) {
              // TODO(zjffdu) remove this try catch. This is only for test of
              // NotebookServerTest#testImportNotebook
            }
          }
        });

    return note;
  }

  private void removeParagraph(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    getNotebookService().removeParagraph(noteId, paragraphId,
        getServiceContext(fromMessage), new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            getConnectionManager().broadcast(p.getNote().getId(), new Message(OP.PARAGRAPH_REMOVED).
                put("id", p.getId()));
          }
        });
  }

  private void clearParagraphOutput(NotebookSocket conn,
                                    Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    getNotebookService().clearParagraphOutput(noteId, paragraphId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            if (p.getNote().isPersonalizedMode()) {
              getConnectionManager().unicastParagraph(p.getNote(), p, context.getAutheInfo().getUser());
            } else {
              broadcastParagraph(p.getNote(), p);
            }
          }
        });
  }

  private void completion(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    String paragraphId = (String) fromMessage.get("id");
    String buffer = (String) fromMessage.get("buf");
    int cursor = (int) Double.parseDouble(fromMessage.get("cursor").toString());
    getNotebookService().completion(noteId, paragraphId, buffer, cursor,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<List<InterpreterCompletion>>(conn) {
          @Override
          public void onSuccess(List<InterpreterCompletion> completions, ServiceContext context)
              throws IOException {
            super.onSuccess(completions, context);
            Message resp = new Message(OP.COMPLETION_LIST).put("id", paragraphId);
            resp.put("completions", completions);
            conn.send(serializeMessage(resp));
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            super.onFailure(ex, context);
            Message resp = new Message(OP.COMPLETION_LIST).put("id", paragraphId);
            resp.put("completions", new ArrayList<>());
            conn.send(serializeMessage(resp));
          }
        });
  }

  /**
   * 1. When angular object updated from client.
   * 2. Save AngularObject to note.
   *
   * @param conn        the web socket.
   * @param fromMessage the message.
   */
  private void angularObjectUpdated(NotebookSocket conn,
                                    Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String paragraphId = (String) fromMessage.get("paragraphId");
    String interpreterGroupId = (String) fromMessage.get("interpreterGroupId");
    String varName = (String) fromMessage.get("name");
    Object varValue = fromMessage.get("value");
    String user = fromMessage.principal;

    getNotebookService().updateAngularObject(noteId, paragraphId, interpreterGroupId,
        varName, varValue, getServiceContext(fromMessage),
        new WebSocketServiceCallback<AngularObject>(conn) {
          @Override
          public void onSuccess(AngularObject ao, ServiceContext context) throws IOException {
            super.onSuccess(ao, context);
            getConnectionManager().broadcastExcept(noteId,
                new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
                    .put("paragraphId", ao.getParagraphId()), conn);
            Note note = getNotebook().getNote(noteId);
            note.addOrUpdateAngularObject(interpreterGroupId, ao);
          }
        });
  }

  /**
   * 1. Push the given Angular variable to the target interpreter angular
   *    registry given a noteId and a paragraph id.
   * 2. Save AngularObject to note.
   */
  protected void angularObjectClientBind(NotebookSocket conn,
                                         Message fromMessage) throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    Object varValue = fromMessage.get("value");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = getNotebook().getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value bind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);
      final RemoteAngularObjectRegistry registry = (RemoteAngularObjectRegistry)
          interpreterGroup.getAngularObjectRegistry();
      AngularObject ao = pushAngularObjectToRemoteRegistry(noteId, paragraphId, varName, varValue, registry,
          interpreterGroup.getId(), conn);
      note.addOrUpdateAngularObject(interpreterGroup.getId(), ao);
    }
  }

  /**
   * 1. Remove the given Angular variable to the target interpreter(s) angular
   *    registry given a noteId and an optional list of paragraph id(s).
   * 2. Delete AngularObject from note.
   */
  protected void angularObjectClientUnbind(NotebookSocket conn,
                                           Message fromMessage) throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = getNotebook().getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value unBind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);
      final RemoteAngularObjectRegistry registry = (RemoteAngularObjectRegistry)
          interpreterGroup.getAngularObjectRegistry();
      AngularObject ao = removeAngularFromRemoteRegistry(noteId, paragraphId, varName, registry,
          interpreterGroup.getId(), conn);
      note.deleteAngularObject(interpreterGroup.getId(), noteId, paragraphId, varName);
    }
  }

  private InterpreterGroup findInterpreterGroupForParagraph(Note note, String paragraphId)
      throws Exception {
    final Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      throw new IllegalArgumentException("Unknown paragraph with id : " + paragraphId);
    }
    return paragraph.getBindedInterpreter().getInterpreterGroup();
  }

  private AngularObject pushAngularObjectToRemoteRegistry(String noteId, String paragraphId, String varName,
                                                 Object varValue,
                                                 RemoteAngularObjectRegistry remoteRegistry,
                                                 String interpreterGroupId,
                                                 NotebookSocket conn) {
    final AngularObject ao =
        remoteRegistry.addAndNotifyRemoteProcess(varName, varValue, noteId, paragraphId);

    getConnectionManager().broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_UPDATE)
        .put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);

    return ao;
  }

  private AngularObject removeAngularFromRemoteRegistry(String noteId, String paragraphId, String varName,
                                               RemoteAngularObjectRegistry remoteRegistry,
                                               String interpreterGroupId,
                                               NotebookSocket conn) {
    final AngularObject ao =
        remoteRegistry.removeAndNotifyRemoteProcess(varName, noteId, paragraphId);
    getConnectionManager().broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_REMOVE)
        .put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);

    return ao;
  }

  private void moveParagraph(NotebookSocket conn,
                             Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    final int newIndex = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    getNotebookService().moveParagraph(noteId, paragraphId, newIndex,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            getConnectionManager().broadcast(result.getNote().getId(),
                new Message(OP.PARAGRAPH_MOVED).put("id", paragraphId).put("index", newIndex));
          }
        });
  }

  private String insertParagraph(NotebookSocket conn,
                                 Message fromMessage) throws IOException {
    final int index = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    Map<String, Object> config;
    if (fromMessage.get("config") != null) {
      config = (Map<String, Object>) fromMessage.get("config");
    } else {
      config = new HashMap<>();
    }

    Paragraph newPara = getNotebookService().insertParagraph(noteId, index, config,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            broadcastNewParagraph(p.getNote(), p);
          }
        });

    return newPara.getId();
  }

  private void copyParagraph(NotebookSocket conn,
                             Message fromMessage) throws IOException {
    String newParaId = insertParagraph(conn, fromMessage);

    if (newParaId == null) {
      return;
    }
    fromMessage.put("id", newParaId);

    updateParagraph(conn, fromMessage);
  }

  private void cancelParagraph(NotebookSocket conn, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    getNotebookService().cancelParagraph(noteId, paragraphId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<>(conn));
  }

  private void runAllParagraphs(NotebookSocket conn,
                                Message fromMessage) throws IOException {
    final String noteId = (String) fromMessage.get("noteId");
    List<Map<String, Object>> paragraphs =
        gson.fromJson(String.valueOf(fromMessage.data.get("paragraphs")),
            new TypeToken<List<Map<String, Object>>>() {
            }.getType());

    getNotebookService().runAllParagraphs(noteId, paragraphs, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn));
  }

  private void broadcastSpellExecution(NotebookSocket conn,
                                       Message fromMessage) throws IOException {

    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    getNotebookService().spell(noteId, fromMessage,
        getServiceContext(fromMessage), new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            // broadcast to other clients only
            getConnectionManager().broadcastExcept(p.getNote().getId(),
                new Message(OP.RUN_PARAGRAPH_USING_SPELL).put("paragraph", p), conn);
          }
        });
  }

  private void runParagraph(NotebookSocket conn,
                            Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);
    String text = (String) fromMessage.get("paragraph");
    String title = (String) fromMessage.get("title");
    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    getNotebookService().runParagraph(noteId, paragraphId, title, text, params, config,
        false, false, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            if (p.getNote().isPersonalizedMode()) {
              Paragraph p2 = p.getNote().clearPersonalizedParagraphOutput(paragraphId,
                  context.getAutheInfo().getUser());
              getConnectionManager().unicastParagraph(p.getNote(), p2, context.getAutheInfo().getUser());
            }

            // if it's the last paragraph and not empty, let's add a new one
            boolean isTheLastParagraph = p.getNote().isLastParagraph(paragraphId);
            if (!(Strings.isNullOrEmpty(p.getText()) ||
                Strings.isNullOrEmpty(p.getScriptText())) &&
                isTheLastParagraph) {
              Paragraph newPara = p.getNote().addNewParagraph(p.getAuthenticationInfo());
              broadcastNewParagraph(p.getNote(), newPara);
            }
          }
        });
  }

  private void sendAllConfigurations(NotebookSocket conn,
                                     Message message) throws IOException {

    getConfigurationService().getAllProperties(getServiceContext(message),
        new WebSocketServiceCallback<Map<String, String>>(conn) {
          @Override
          public void onSuccess(Map<String, String> properties,
                                ServiceContext context) throws IOException {
            super.onSuccess(properties, context);
            properties.put("isRevisionSupported", String.valueOf(getNotebook().isRevisionSupported()));
            conn.send(serializeMessage(
                new Message(OP.CONFIGURATIONS_INFO).put("configurations", properties)));
          }
        });
  }

  private void checkpointNote(NotebookSocket conn, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String commitMessage = (String) fromMessage.get("commitMessage");

    getNotebookService().checkpointNote(noteId, commitMessage, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Revision>(conn) {
          @Override
          public void onSuccess(Revision revision, ServiceContext context) throws IOException {
            super.onSuccess(revision, context);
            if (!Revision.isEmpty(revision)) {
              List<Revision> revisions =
                  getNotebook().listRevisionHistory(noteId, getNotebook().getNote(noteId).getPath(),
                      context.getAutheInfo());
              conn.send(
                  serializeMessage(new Message(OP.LIST_REVISION_HISTORY).put("revisionList",
                      revisions)));
            } else {
              conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
                  "Couldn't checkpoint note revision: possibly no changes found or storage doesn't support versioning. "
                      + "Please check the logs for more details.")));
            }
          }
        });
  }

  private void listRevisionHistory(NotebookSocket conn, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    getNotebookService().listRevisionHistory(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<List<Revision>>(conn) {
          @Override
          public void onSuccess(List<Revision> revisions, ServiceContext context)
              throws IOException {
            super.onSuccess(revisions, context);
            conn.send(serializeMessage(
                new Message(OP.LIST_REVISION_HISTORY).put("revisionList", revisions)));
          }
        });
  }

  private void setNoteRevision(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    getNotebookService().setNoteRevision(noteId, revisionId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            Note reloadedNote = getNotebook().loadNoteFromRepo(noteId, context.getAutheInfo());
            conn.send(serializeMessage(new Message(OP.SET_NOTE_REVISION).put("status", true)));
            broadcastNote(reloadedNote);
          }
        });
  }

  private void getNoteByRevision(NotebookSocket conn, Message fromMessage)
      throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    getNotebookService().getNotebyRevision(noteId, revisionId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            conn.send(serializeMessage(
                new Message(OP.NOTE_REVISION).put("noteId", noteId).put("revisionId", revisionId)
                    .put("note", note)));
          }
        });
  }

  private void getNoteByRevisionForCompare(NotebookSocket conn,
                                           Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    String position = (String) fromMessage.get("position");
    getNotebookService().getNoteByRevisionForCompare(noteId, revisionId,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            conn.send(serializeMessage(
                new Message(OP.NOTE_REVISION_FOR_COMPARE).put("noteId", noteId)
                    .put("revisionId", revisionId).put("position", position).put("note", note)));
          }
        });
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer.
   *
   * @param output output to append
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String output) {
    Message msg = new Message(OP.PARAGRAPH_APPEND_OUTPUT).put("noteId", noteId)
        .put("paragraphId", paragraphId).put("index", index).put("data", output);
    getConnectionManager().broadcast(noteId, msg);
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer.
   *
   * @param output output to update (replace)
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index,
                              InterpreterResult.Type type, String output) {
    Message msg = new Message(OP.PARAGRAPH_UPDATE_OUTPUT).put("noteId", noteId)
        .put("paragraphId", paragraphId).put("index", index).put("type", type).put("data", output);
    try {
      Note note = getNotebook().getNote(noteId);
      if (note == null) {
        LOG.warn("Note " + noteId + " note found");
        return;
      }
      Paragraph paragraph = note.getParagraph(paragraphId);
      paragraph.updateOutputBuffer(index, type, output);
      if (note.isPersonalizedMode()) {
        String user = note.getParagraph(paragraphId).getUser();
        if (null != user) {
          getConnectionManager().multicastToUser(user, msg);
        }
      } else {
        getConnectionManager().broadcast(noteId, msg);
      }
    } catch (IOException e) {
      LOG.warn("Fail to call onOutputUpdated", e);
    }
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer.
   */
  @Override
  public void onOutputClear(String noteId, String paragraphId) {
    try {
      final Note note = getNotebook().getNote(noteId);
      if (note == null) {
        // It is possible the note is removed, but the job is still running
        LOG.warn("Note {} doesn't existed, it maybe deleted.", noteId);
      } else {
        note.clearParagraphOutput(paragraphId);
        Paragraph paragraph = note.getParagraph(paragraphId);
        broadcastParagraph(note, paragraph);
      }
    } catch (IOException e) {
      LOG.warn("Fail to call onOutputClear", e);
    }
  }

  /**
   * When application append output.
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String appId,
                             String output) {
    Message msg =
        new Message(OP.APP_APPEND_OUTPUT).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("index", index).put("appId", appId).put("data", output);
    getConnectionManager().broadcast(noteId, msg);
  }

  /**
   * When application update output.
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, int index, String appId,
                              InterpreterResult.Type type, String output) {
    Message msg =
        new Message(OP.APP_UPDATE_OUTPUT).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("index", index).put("type", type).put("appId", appId).put("data", output);
    getConnectionManager().broadcast(noteId, msg);
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    Message msg = new Message(OP.APP_LOAD).put("noteId", noteId).put("paragraphId", paragraphId)
        .put("appId", appId).put("pkg", pkg);
    getConnectionManager().broadcast(noteId, msg);
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    Message msg =
        new Message(OP.APP_STATUS_CHANGE).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("appId", appId).put("status", status);
    getConnectionManager().broadcast(noteId, msg);
  }


  @Override
  public void runParagraphs(String noteId,
                            List<Integer> paragraphIndices,
                            List<String> paragraphIds,
                            String curParagraphId) throws IOException {
    final Note note = getNotebook().getNote(noteId);
    final List<String> toBeRunParagraphIds = new ArrayList<>();
    if (note == null) {
      throw new IOException("Not existed noteId: " + noteId);
    }
    if (!paragraphIds.isEmpty() && !paragraphIndices.isEmpty()) {
      throw new IOException("Can not specify paragraphIds and paragraphIndices together");
    }
    if (paragraphIds != null && !paragraphIds.isEmpty()) {
      for (String paragraphId : paragraphIds) {
        if (note.getParagraph(paragraphId) == null) {
          throw new IOException("Not existed paragraphId: " + paragraphId);
        }
        if (!paragraphId.equals(curParagraphId)) {
          toBeRunParagraphIds.add(paragraphId);
        }
      }
    }
    if (paragraphIndices != null && !paragraphIndices.isEmpty()) {
      for (int paragraphIndex : paragraphIndices) {
        if (note.getParagraph(paragraphIndex) == null) {
          throw new IOException("Not existed paragraphIndex: " + paragraphIndex);
        }
        if (!note.getParagraph(paragraphIndex).getId().equals(curParagraphId)) {
          toBeRunParagraphIds.add(note.getParagraph(paragraphIndex).getId());
        }
      }
    }
    // run the whole note except the current paragraph
    if (paragraphIds.isEmpty() && paragraphIndices.isEmpty()) {
      for (Paragraph paragraph : note.getParagraphs()) {
        if (!paragraph.getId().equals(curParagraphId)) {
          toBeRunParagraphIds.add(paragraph.getId());
        }
      }
    }
    Runnable runThread = new Runnable() {
      @Override
      public void run() {
        for (String paragraphId : toBeRunParagraphIds) {
          note.run(paragraphId, true);
        }
      }
    };
    executorService.submit(runThread);
  }


  @Override
  public void onParagraphRemove(Paragraph p) {
    try {
      ServiceContext context = new ServiceContext(new AuthenticationInfo(),
              getNotebookAuthorizationService().getOwners(p.getNote().getId()));
      getJobManagerService().getNoteJobInfoByUnixTime(System.currentTimeMillis() - 5000, context,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    try {
      broadcastUpdateNoteJobInfo(note, System.currentTimeMillis() - 5000);
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }

    try {
      getJobManagerService().removeNoteJobInfo(note.getId(), null,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }

  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    try {
      getJobManagerService().getNoteJobInfo(p.getNote().getId(), null,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }
  }

  @Override
  public void onParagraphUpdate(Paragraph p) throws IOException {

  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    try {
      getJobManagerService().getNoteJobInfo(note.getId(), null,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) throws IOException {

  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Status status) {
    try {
      getJobManagerService().getNoteJobInfo(p.getNote().getId(), null,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }
  }


  private class JobManagerServiceCallback
      extends SimpleServiceCallback<List<JobManagerService.NoteJobInfo>> {
    @Override
    public void onSuccess(List<JobManagerService.NoteJobInfo> notesJobInfo,
                          ServiceContext context) throws IOException {
      super.onSuccess(notesJobInfo, context);
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notesJobInfo);
      getConnectionManager().broadcast(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }
  }


  @Override
  public void onProgressUpdate(Paragraph p, int progress) {
    getConnectionManager().broadcast(p.getNote().getId(),
        new Message(OP.PROGRESS).put("id", p.getId()).put("progress", progress));
  }

  @Override
  public void onStatusChange(Paragraph p, Status before, Status after) {
    if (after == Status.ERROR) {
      if (p.getException() != null) {
        LOG.error("Error", p.getException());
      }
    }

    if (p.isTerminated() || after == Status.RUNNING) {
      if (p.getStatus() == Status.FINISHED) {
        LOG.info("Job {} is finished successfully, status: {}", p.getId(), p.getStatus());
      } else if (p.isTerminated()) {
        LOG.warn("Job {} is finished, status: {}, exception: {}, result: {}", p.getId(),
            p.getStatus(), p.getException(), p.getReturn());
      } else {
        LOG.info("Job {} starts to RUNNING", p.getId());
      }

      try {
        if (getNotebook().getNote(p.getNote().getId()) == null) {
          // It is possible the note is removed, but the job is still running
          LOG.warn("Note {} doesn't existed.", p.getNote().getId());
        } else {
          getNotebook().saveNote(p.getNote(), p.getAuthenticationInfo());
        }
      } catch (IOException e) {
        LOG.error(e.toString(), e);
      }
    }

    p.setStatusToUserParagraph(p.getStatus());
    broadcastParagraph(p.getNote(), p);
    try {
      broadcastUpdateNoteJobInfo(p.getNote(), System.currentTimeMillis() - 5000);
    } catch (IOException e) {
      LOG.error("can not broadcast for job manager {}", e);
    }
  }

  @Override
  public void checkpointOutput(String noteId, String paragraphId) {
    try {
      Note note = getNotebook().getNote(noteId);
      note.getParagraph(paragraphId).checkpointOutput();
      getNotebook().saveNote(note, AuthenticationInfo.ANONYMOUS);
    } catch (IOException e) {
      LOG.warn("Fail to save note: " + noteId , e);
    }
  }

  @Override
  public void noteRunningStatusChange(String noteId, boolean newStatus) {
    getConnectionManager().broadcast(
        noteId,
        new Message(OP.NOTE_RUNNING_STATUS
        ).put("status", newStatus));
  }

  private void sendAllAngularObjects(Note note, String user, NotebookSocket conn)
      throws IOException {
    List<InterpreterSetting> settings =
        getNotebook().getBindedInterpreterSettings(note.getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting intpSetting : settings) {
      if (intpSetting.getInterpreterGroup(user, note.getId()) == null) {
        continue;
      }
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
  public void onAddAngularObject(String interpreterGroupId, AngularObject angularObject) {
    onUpdateAngularObject(interpreterGroupId, angularObject);
  }

  @Override
  public void onUpdateAngularObject(String interpreterGroupId, AngularObject angularObject) {
    if (getNotebook() == null) {
      return;
    }

    // not global scope, so we just need to load the corresponded note.
    if (angularObject.getNoteId() != null) {
      try {
        Note note = getNotebook().getNote(angularObject.getNoteId());
        updateNoteAngularObject(note, angularObject, interpreterGroupId);
      } catch (IOException e) {
        LOG.error("AngularObject's note: {} is not found", angularObject.getNoteId());
      }
    } else {
      // global scope angular object needs to load and iterate all notes, this is inefficient.
      getNotebook().getNoteStream().forEach(note -> {
        if (angularObject.getNoteId() != null && !note.getId().equals(angularObject.getNoteId())) {
          return;
        }
        updateNoteAngularObject(note, angularObject, interpreterGroupId);
      });
    }
  }

  private void updateNoteAngularObject(Note note, AngularObject angularObject, String interpreterGroupId) {
    List<InterpreterSetting> intpSettings =
            note.getBindedInterpreterSettings(
                    new ArrayList<>(getNotebookAuthorizationService().getOwners(note.getId())));
    if (intpSettings.isEmpty()) {
      return;
    }
    getConnectionManager().broadcast(note.getId(), new Message(OP.ANGULAR_OBJECT_UPDATE)
            .put("angularObject", angularObject)
            .put("interpreterGroupId", interpreterGroupId).put("noteId", note.getId())
            .put("paragraphId", angularObject.getParagraphId()));
  }

  @Override
  public void onRemoveAngularObject(String interpreterGroupId, AngularObject angularObject) {
    // not global scope, so we just need to load the corresponded note.
    if (angularObject.getNoteId() != null) {
      try {
        Note note = getNotebook().getNote(angularObject.getNoteId());
        removeNoteAngularObject(angularObject.getNoteId(), angularObject, interpreterGroupId);
      } catch (IOException e) {
        LOG.error("AngularObject's note: {} is not found", angularObject.getNoteId());
      }
    } else {
      // global scope angular object needs to load and iterate all notes, this is inefficient.
      getNotebook().getNoteStream().forEach(note -> {
        if (angularObject.getNoteId() != null && !note.getId().equals(angularObject.getNoteId())) {
          return;
        }
        removeNoteAngularObject(note.getId(), angularObject, interpreterGroupId);
      });
    }
  }

  private void removeNoteAngularObject(String noteId, AngularObject angularObject, String interpreterGroupId) {
    List<String> settingIds =
            getNotebook().getInterpreterSettingManager().getSettingIds();
    for (String id : settingIds) {
      if (interpreterGroupId.contains(id)) {
        getConnectionManager().broadcast(noteId,
                new Message(OP.ANGULAR_OBJECT_REMOVE)
                        .put("name", angularObject.getName())
                        .put("noteId", angularObject.getNoteId())
                        .put("paragraphId", angularObject.getParagraphId()));
        break;
      }
    }
  }

  private void getEditorSetting(NotebookSocket conn, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("paragraphId");
    String paragraphText = (String) fromMessage.get("paragraphText");
    String noteId = getConnectionManager().getAssociatedNoteId(conn);

    getNotebookService().getEditorSetting(noteId, paragraphText,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Map<String, Object>>(conn) {
          @Override
          public void onSuccess(Map<String, Object> settings,
                                ServiceContext context) throws IOException {
            super.onSuccess(settings, context);
            Message resp = new Message(OP.EDITOR_SETTING);
            resp.put("paragraphId", paragraphId);
            resp.put("editor", settings);
            conn.send(serializeMessage(resp));
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            LOG.warn(ex.getMessage());
          }
        });
  }

  private void getInterpreterSettings(NotebookSocket conn, Message message)
      throws IOException {
    ServiceContext context = getServiceContext(message);
    List<InterpreterSetting> allSettings = getNotebook().getInterpreterSettingManager().get();
    List<InterpreterSetting> result = new ArrayList<>();
    for (InterpreterSetting setting : allSettings) {
      if (setting.isUserAuthorized(new ArrayList<>(context.getUserAndRoles()))) {
        result.add(setting);
      }
    }
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_SETTINGS).put("interpreterSettings", result)));
  }

  @Override
  public void onParaInfosReceived(String noteId, String paragraphId,
                                  String interpreterSettingId, Map<String, String> metaInfos) {
    try {
      Note note = getNotebook().getNote(noteId);
      if (note != null) {
        Paragraph paragraph = note.getParagraph(paragraphId);
        if (paragraph != null) {
          InterpreterSetting setting = getNotebook().getInterpreterSettingManager()
                  .get(interpreterSettingId);
          String label = metaInfos.get("label");
          String tooltip = metaInfos.get("tooltip");
          List<String> keysToRemove = Arrays.asList("noteId", "paraId", "label", "tooltip");
          for (String removeKey : keysToRemove) {
            metaInfos.remove(removeKey);
          }

          paragraph
                  .updateRuntimeInfos(label, tooltip, metaInfos, setting.getGroup(), setting.getId());
          getNotebook().saveNote(note, AuthenticationInfo.ANONYMOUS);
          getConnectionManager().broadcast(
                  note.getId(),
                  new Message(OP.PARAS_INFO).put("id", paragraphId).put("infos",
                          paragraph.getRuntimeInfos()));
        }
      }
    } catch (IOException e) {
      LOG.warn("Fail to call onParaInfosReceived", e);
    }
  }

  @Override
  public List<ParagraphInfo> getParagraphList(String user, String noteId)
      throws TException, IOException {
    Notebook notebook = getNotebook();
    Note note = notebook.getNote(noteId);
    if (null == note) {
      throw new ServiceException("Not found this note : " + noteId);
    }

    // Check READER permission
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(user);
    AuthorizationService notebookAuthorization = getNotebookAuthorizationService();
    boolean isAllowed = notebookAuthorization.isReader(noteId, userAndRoles);
    Set<String> allowed = notebookAuthorization.getReaders(noteId);
    if (false == isAllowed) {
      String errorMsg = "Insufficient privileges to READER note. " +
          "Allowed users or roles: " + allowed;
      throw new ServiceException(errorMsg);
    }

    // Convert Paragraph to ParagraphInfo
    List<ParagraphInfo> paragraphInfos = new ArrayList();
    List<Paragraph> paragraphs = note.getParagraphs();
    for (Iterator<Paragraph> iter = paragraphs.iterator(); iter.hasNext();) {
      Paragraph paragraph = iter.next();
      ParagraphInfo paraInfo = new ParagraphInfo();
      paraInfo.setNoteId(noteId);
      paraInfo.setParagraphId(paragraph.getId());
      paraInfo.setParagraphTitle(paragraph.getTitle());
      paraInfo.setParagraphText(paragraph.getText());
      paragraphInfos.add(paraInfo);
    }
    return paragraphInfos;
  }

  private void broadcastNoteForms(Note note) {
    GUI formsSettings = new GUI();
    formsSettings.setForms(note.getNoteForms());
    formsSettings.setParams(note.getNoteParams());
    getConnectionManager().broadcast(note.getId(),
        new Message(OP.SAVE_NOTE_FORMS).put("formsData", formsSettings));
  }

  private void saveNoteForms(NotebookSocket conn,
                             Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    Map<String, Object> noteParams = (Map<String, Object>) fromMessage.get("noteParams");

    getNotebookService().saveNoteForms(noteId, noteParams, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            broadcastNoteForms(note);
          }
        });
  }

  private void removeNoteForms(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String formName = (String) fromMessage.get("formName");

    getNotebookService().removeNoteForms(noteId, formName, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            broadcastNoteForms(note);
          }
        });
  }

  @ManagedAttribute
  public Set<String> getConnectedUsers() {
    return getConnectionManager().getConnectedUsers();
  }

  @ManagedOperation
  public void sendMessage(String message) {
    Message m = new Message(OP.NOTICE);
    m.data.put("notice", message);
    getConnectionManager().broadcast(m);
  }

  private ServiceContext getServiceContext(Message message) {
    AuthenticationInfo authInfo =
        new AuthenticationInfo(message.principal, message.roles, message.ticket);
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(message.principal);
    if (message.roles != null && !message.roles.equals("")) {
      HashSet<String> roles =
          gson.fromJson(message.roles, new TypeToken<HashSet<String>>() {
          }.getType());
      if (roles != null) {
        userAndRoles.addAll(roles);
      }
    }
    return new ServiceContext(authInfo, userAndRoles);
  }

  public class WebSocketServiceCallback<T> extends SimpleServiceCallback<T> {

    private NotebookSocket conn;

    WebSocketServiceCallback(NotebookSocket conn) {
      this.conn = conn;
    }

    @Override
    public void onFailure(Exception ex, ServiceContext context) throws IOException {
      super.onFailure(ex, context);
      if (ex instanceof ForbiddenException) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> jsonObject =
            gson.fromJson(((ForbiddenException) ex).getResponse().getEntity().toString(), type);
        conn.send(serializeMessage(new Message(OP.AUTH_INFO)
            .put("info", jsonObject.get("message"))));
      } else {
        String message = ex.getMessage();
        if (ex.getCause() != null) {
          message += ", cause: " + ex.getCause().getMessage();
        }
        conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info", message)));
      }
    }
  }
}
