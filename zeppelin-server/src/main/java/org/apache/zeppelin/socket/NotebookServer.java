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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.NotebookImportDeserializer;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.notebook.ParagraphWithRuntimeInfo;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl.Revision;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.JobManagerService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.types.InterpreterSettingsList;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.CorsUtils;
import org.apache.zeppelin.utils.InterpreterBindingUtils;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Zeppelin websocket service.
 */
public class NotebookServer extends WebSocketServlet
    implements NotebookSocketListener,
    AngularObjectRegistryListener,
    RemoteInterpreterProcessListener,
    ApplicationEventListener,
    ParagraphJobListener,
    NoteEventListener,
    NotebookServerMBean {

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

  private ConnectionManager connectionManager;
  private NotebookService notebookService;
  private ConfigurationService configurationService;
  private JobManagerService jobManagerService;

  private ExecutorService executorService = Executors.newFixedThreadPool(10);


  public NotebookServer() {
    this.connectionManager = new ConnectionManager();
    // TODO(jl): Replace this code with @PostConstruct after finishing injection
    new Thread(
        () -> {
          Logger innerLogger = LoggerFactory.getLogger("NotebookServerRegister");
          innerLogger.info("Started registration thread");
          try {
            NotebookService notebookService = null;
            while (null == notebookService) {
              try {
                notebookService = getNotebookService();
              } catch (IllegalStateException ignored) {
                // ignored
              }
              Thread.sleep(100);
            }
            getNotebookService().injectNotebookServer(this);
            innerLogger.info("Completed registration thread");
          } catch (InterruptedException e) {
            innerLogger.info("Interrupted. will stop registration process");
          }
        }, "NotebookServerRegister")
      .start();
  }

  private Notebook notebook() {
    return getNotebookService().getNotebook();
  }

  public synchronized NotebookService getNotebookService() {
    if (this.notebookService == null) {
      this.notebookService = NotebookService.getInstance();
    }
    return this.notebookService;
  }

  public synchronized ConfigurationService getConfigurationService() {
    if (this.configurationService == null) {
      this.configurationService = ConfigurationService.getInstance();
    }
    return this.configurationService;
  }

  public synchronized JobManagerService getJobManagerService() {
    if (this.jobManagerService == null) {
      this.jobManagerService = new JobManagerService(notebook());
    }
    return this.jobManagerService;
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
    connectionManager.addConnection(conn);
  }

  @Override
  public void onMessage(NotebookSocket conn, String msg) {
    Notebook notebook = notebook();
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
        throw new Exception("Anonymous access not allowed ");
      }

      if (Message.isDisabledForRunningNotes(messagereceived.op)) {
        Note note = notebook.getNote((String) messagereceived.get("noteId"));
        if (note != null && note.isRunning()) {
          throw new Exception("Note is now running sequentially. Can not be performed: " +
                  messagereceived.op);
        }
      }

      if (StringUtils.isEmpty(conn.getUser())) {
        connectionManager.addUserConnection(messagereceived.principal, conn);
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
        case EDITOR_SETTING:
          getEditorSetting(conn, messagereceived);
          break;
        case GET_INTERPRETER_SETTINGS:
          getInterpreterSettings(conn);
          break;
        case WATCHER:
          connectionManager.switchConnectionToWatcher(conn);
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
    connectionManager.removeConnection(conn);
    connectionManager.removeConnectionFromAllNote(conn);
    connectionManager.removeUserConnection(conn.getUser(), conn);
  }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  public void broadcast(Message m) {
    connectionManager.broadcast(m);
  }

  public void unicastNoteJobInfo(NotebookSocket conn, Message fromMessage) throws IOException {

    connectionManager.addNoteConnection(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(), conn);
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

  public void broadcastUpdateNoteJobInfo(long lastUpdateUnixTime) throws IOException {
    getJobManagerService().getNoteJobInfoByUnixTime(lastUpdateUnixTime, null,
        new WebSocketServiceCallback<List<JobManagerService.NoteJobInfo>>(null) {
          @Override
          public void onSuccess(List<JobManagerService.NoteJobInfo> notesJobInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesJobInfo, context);
            Map<String, Object> response = new HashMap<>();
            response.put("lastResponseUnixTime", System.currentTimeMillis());
            response.put("jobs", notesJobInfo);
            connectionManager.broadcast(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(),
                new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
          }

          @Override
          public void onFailure(Exception ex, ServiceContext context) throws IOException {
            LOG.warn(ex.getMessage());
          }
        });
  }

  public void unsubscribeNoteJobInfo(NotebookSocket conn) {
    connectionManager.removeNoteConnection(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(), conn);
  }

  public void getInterpreterBindings(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.data.get("noteId");
    List<InterpreterSettingsList> settingList =
        InterpreterBindingUtils.getInterpreterBindings(notebook(), noteId);
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_BINDINGS).put("interpreterBindings", settingList)));
  }

  public void broadcastNote(Note note) {
    connectionManager.broadcast(note.getId(), new Message(OP.NOTE).put("note", note));
  }

  public void broadcastParagraph(Note note, Paragraph p) {
    broadcastNoteForms(note);

    if (note.isPersonalizedMode()) {
      broadcastParagraphs(p.getUserParagraphMap(), p);
    } else {
      connectionManager.broadcast(note.getId(),
          new Message(OP.PARAGRAPH).put("paragraph", new ParagraphWithRuntimeInfo(p)));
    }
  }

  public void broadcastParagraphs(Map<String, Paragraph> userParagraphMap,
                                  Paragraph defaultParagraph) {
    if (null != userParagraphMap) {
      for (String user : userParagraphMap.keySet()) {
        connectionManager.multicastToUser(user,
            new Message(OP.PARAGRAPH).put("paragraph", userParagraphMap.get(user)));
      }
    }
  }

  private void broadcastNewParagraph(Note note, Paragraph para) {
    LOG.info("Broadcasting paragraph on run call instead of note.");
    int paraIndex = note.getParagraphs().indexOf(para);
    connectionManager.broadcast(note.getId(),
        new Message(OP.PARAGRAPH_ADDED).put("paragraph", para).put("index", paraIndex));
  }

  public void broadcastNoteList(AuthenticationInfo subject, Set<String> userAndRoles) {
    if (subject == null) {
      subject = new AuthenticationInfo(StringUtils.EMPTY);
    }
    //send first to requesting user
    List<NoteInfo> notesInfo = notebook().getNotesInfo(userAndRoles);
    connectionManager.multicastToUser(subject.getUser(),
        new Message(OP.NOTES_INFO).put("notes", notesInfo));
    //to others afterwards
    connectionManager.broadcastNoteListExcept(notesInfo, subject);
  }

  public void listNotesInfo(NotebookSocket conn, Message message) throws IOException {
    getNotebookService().listNotesInfo(false, getServiceContext(message),
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> notesInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesInfo, context);
            connectionManager.unicast(new Message(OP.NOTES_INFO).put("notes", notesInfo), conn);
          }
        });
  }

  public void broadcastReloadedNoteList(NotebookSocket conn, ServiceContext context)
      throws IOException {
    getNotebookService().listNotesInfo(false, context,
        new WebSocketServiceCallback<List<NoteInfo>>(conn) {
          @Override
          public void onSuccess(List<NoteInfo> notesInfo,
                                ServiceContext context) throws IOException {
            super.onSuccess(notesInfo, context);
            connectionManager.multicastToUser(context.getAutheInfo().getUser(),
                new Message(OP.NOTES_INFO).put("notes", notesInfo));
            //to others afterwards
            connectionManager.broadcastNoteListExcept(notesInfo, context.getAutheInfo());
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
    NotebookAuthorization notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(noteId, userAndRoles)) {
      permissionError(conn, op, principal, userAndRoles,
          notebookAuthorization.getOwners(noteId));
      return false;
    }

    return true;
  }

  private void getNote(NotebookSocket conn,
                       Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }
    getNotebookService().getNote(noteId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            connectionManager.addNoteConnection(note.getId(), conn);
            conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
            sendAllAngularObjects(note, context.getAutheInfo().getUser(), conn);
          }
        });
  }

  private void getHomeNote(NotebookSocket conn,
                           Message fromMessage) throws IOException {

    getNotebookService().getHomeNote(getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note note, ServiceContext context) throws IOException {
            super.onSuccess(note, context);
            if (note != null) {
              connectionManager.addNoteConnection(note.getId(), conn);
              conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
              sendAllAngularObjects(note, context.getAutheInfo().getUser(), conn);
            } else {
              connectionManager.removeConnectionFromAllNote(conn);
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
            connectionManager.broadcast(note.getId(), new Message(OP.NOTE_UPDATED).put("name", name)
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
            connectionManager.broadcastNote(note);
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
            connectionManager.addNoteConnection(note.getId(), conn);
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
            connectionManager.removeNoteConnection(noteId);
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
              connectionManager.removeNoteConnection(noteInfo.getId());
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
        new WebSocketServiceCallback(conn));
  }

  private void updateParagraph(NotebookSocket conn,
                               Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String noteId = connectionManager.getAssociatedNoteId(conn);
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

    String noteId = connectionManager.getAssociatedNoteId(conn);
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
            connectionManager.broadcastExcept(noteId2, message, conn);
          }
        });
  }

  private void cloneNote(NotebookSocket conn,
                         Message fromMessage) throws IOException {
    String noteId = connectionManager.getAssociatedNoteId(conn);
    String name = (String) fromMessage.get("name");
    getNotebookService().cloneNote(noteId, name, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Note>(conn) {
          @Override
          public void onSuccess(Note newNote, ServiceContext context) throws IOException {
            super.onSuccess(newNote, context);
            connectionManager.addNoteConnection(newNote.getId(), conn);
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

  protected Note importNote(NotebookSocket conn, Message fromMessage) throws IOException {
    String noteName = (String) ((Map) fromMessage.get("note")).get("name");
    String noteJson = gson.toJson(fromMessage.get("note"));
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
    String noteId = connectionManager.getAssociatedNoteId(conn);
    getNotebookService().removeParagraph(noteId, paragraphId,
        getServiceContext(fromMessage), new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            connectionManager.broadcast(p.getNote().getId(), new Message(OP.PARAGRAPH_REMOVED).
                put("id", p.getId()));
          }
        });
  }

  private void clearParagraphOutput(NotebookSocket conn,
                                    Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    String noteId = connectionManager.getAssociatedNoteId(conn);
    getNotebookService().clearParagraphOutput(noteId, paragraphId, getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            if (p.getNote().isPersonalizedMode()) {
              connectionManager.unicastParagraph(p.getNote(), p, context.getAutheInfo().getUser());
            } else {
              broadcastParagraph(p.getNote(), p);
            }
          }
        });
  }

  private void completion(NotebookSocket conn,
                          Message fromMessage) throws IOException {
    String noteId = connectionManager.getAssociatedNoteId(conn);
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
   * When angular object updated from client.
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
            connectionManager.broadcastExcept(noteId,
                new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
                    .put("paragraphId", ao.getParagraphId()), conn);
          }
        });
  }

  /**
   * Push the given Angular variable to the target interpreter angular registry given a noteId
   * and a paragraph id.
   */
  protected void angularObjectClientBind(NotebookSocket conn,
                                         Message fromMessage) throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    Object varValue = fromMessage.get("value");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook().getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value bind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);
      final RemoteAngularObjectRegistry registry = (RemoteAngularObjectRegistry)
          interpreterGroup.getAngularObjectRegistry();
      pushAngularObjectToRemoteRegistry(noteId, paragraphId, varName, varValue, registry,
          interpreterGroup.getId(), conn);
    }
  }

  /**
   * Remove the given Angular variable to the target interpreter(s) angular registry given a noteId
   * and an optional list of paragraph id(s).
   */
  protected void angularObjectClientUnbind(NotebookSocket conn,
                                           Message fromMessage)
      throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook().getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException(
          "target paragraph not specified for " + "angular value unBind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note, paragraphId);
      final RemoteAngularObjectRegistry registry = (RemoteAngularObjectRegistry)
          interpreterGroup.getAngularObjectRegistry();
      removeAngularFromRemoteRegistry(noteId, paragraphId, varName, registry,
          interpreterGroup.getId(), conn);

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

  private void pushAngularObjectToRemoteRegistry(String noteId, String paragraphId, String varName,
                                                 Object varValue,
                                                 RemoteAngularObjectRegistry remoteRegistry,
                                                 String interpreterGroupId,
                                                 NotebookSocket conn) {
    final AngularObject ao =
        remoteRegistry.addAndNotifyRemoteProcess(varName, varValue, noteId, paragraphId);

    connectionManager.broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_UPDATE)
        .put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);
  }

  private void removeAngularFromRemoteRegistry(String noteId, String paragraphId, String varName,
                                               RemoteAngularObjectRegistry remoteRegistry,
                                               String interpreterGroupId,
                                               NotebookSocket conn) {
    final AngularObject ao =
        remoteRegistry.removeAndNotifyRemoteProcess(varName, noteId, paragraphId);
    connectionManager.broadcastExcept(noteId, new Message(OP.ANGULAR_OBJECT_REMOVE)
        .put("angularObject", ao)
        .put("interpreterGroupId", interpreterGroupId).put("noteId", noteId)
        .put("paragraphId", paragraphId), conn);
  }

  private void moveParagraph(NotebookSocket conn,
                             Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    final int newIndex = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = connectionManager.getAssociatedNoteId(conn);
    getNotebookService().moveParagraph(noteId, paragraphId, newIndex,
        getServiceContext(fromMessage),
        new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph result, ServiceContext context) throws IOException {
            super.onSuccess(result, context);
            connectionManager.broadcast(result.getNote().getId(),
                new Message(OP.PARAGRAPH_MOVED).put("id", paragraphId).put("index", newIndex));
          }
        });
  }

  private String insertParagraph(NotebookSocket conn,
                                 Message fromMessage) throws IOException {
    final int index = (int) Double.parseDouble(fromMessage.get("index").toString());
    String noteId = connectionManager.getAssociatedNoteId(conn);
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
    String noteId = connectionManager.getAssociatedNoteId(conn);
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

    String noteId = connectionManager.getAssociatedNoteId(conn);
    getNotebookService().spell(noteId, fromMessage,
        getServiceContext(fromMessage), new WebSocketServiceCallback<Paragraph>(conn) {
          @Override
          public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
            super.onSuccess(p, context);
            // broadcast to other clients only
            connectionManager.broadcastExcept(p.getNote().getId(),
                new Message(OP.RUN_PARAGRAPH_USING_SPELL).put("paragraph", p), conn);
          }
        });
  }

  private void runParagraph(NotebookSocket conn,
                            Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String noteId = connectionManager.getAssociatedNoteId(conn);
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
              connectionManager.unicastParagraph(p.getNote(), p2, context.getAutheInfo().getUser());
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
            properties.put("isRevisionSupported", String.valueOf(notebook().isRevisionSupported()));
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
                  notebook().listRevisionHistory(noteId, notebook().getNote(noteId).getPath(),
                      context.getAutheInfo());
              conn.send(
                  serializeMessage(new Message(OP.LIST_REVISION_HISTORY).put("revisionList",
                      revisions)));
            } else {
              conn.send(serializeMessage(new Message(OP.ERROR_INFO).put("info",
                  "Couldn't checkpoint note revision: possibly storage doesn't support versioning. "
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
            Note reloadedNote = notebook().loadNoteFromRepo(noteId, context.getAutheInfo());
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
    connectionManager.broadcast(noteId, msg);
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
    Note note = notebook().getNote(noteId);

    if (note.isPersonalizedMode()) {
      String user = note.getParagraph(paragraphId).getUser();
      if (null != user) {
        connectionManager.multicastToUser(user, msg);
      }
    } else {
      connectionManager.broadcast(noteId, msg);
    }
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer.
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
   * When application append output.
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, int index, String appId,
                             String output) {
    Message msg =
        new Message(OP.APP_APPEND_OUTPUT).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("index", index).put("appId", appId).put("data", output);
    connectionManager.broadcast(noteId, msg);
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
    connectionManager.broadcast(noteId, msg);
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    Message msg = new Message(OP.APP_LOAD).put("noteId", noteId).put("paragraphId", paragraphId)
        .put("appId", appId).put("pkg", pkg);
    connectionManager.broadcast(noteId, msg);
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    Message msg =
        new Message(OP.APP_STATUS_CHANGE).put("noteId", noteId).put("paragraphId", paragraphId)
            .put("appId", appId).put("status", status);
    connectionManager.broadcast(noteId, msg);
  }


  @Override
  public void runParagraphs(String noteId,
                            List<Integer> paragraphIndices,
                            List<String> paragraphIds,
                            String curParagraphId) throws IOException {
    Notebook notebook = notebook();
    final Note note = notebook.getNote(noteId);
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
      getJobManagerService().getNoteJobInfoByUnixTime(System.currentTimeMillis() - 5000, null,
          new JobManagerServiceCallback());
    } catch (IOException e) {
      LOG.warn("can not broadcast for job manager: " + e.getMessage(), e);
    }
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    try {
      broadcastUpdateNoteJobInfo(System.currentTimeMillis() - 5000);
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
      connectionManager.broadcast(JobManagerServiceType.JOB_MANAGER_PAGE.getKey(),
          new Message(OP.LIST_UPDATE_NOTE_JOBS).put("noteRunningJobs", response));
    }
  }


  @Override
  public void onProgressUpdate(Paragraph p, int progress) {
    connectionManager.broadcast(p.getNote().getId(),
        new Message(OP.PROGRESS).put("id", p.getId()).put("progress", progress));
  }

  @Override
  public void onStatusChange(Paragraph p, Status before, Status after) {
    if (after == Status.ERROR) {
      if (p.getException() != null) {
        LOG.error("Error", p.getException());
      }
    }

    if (p.isTerminated()) {
      if (p.getStatus() == Status.FINISHED) {
        LOG.info("Job {} is finished successfully, status: {}", p.getId(), p.getStatus());
      } else {
        LOG.warn("Job {} is finished, status: {}, exception: {}, result: {}", p.getId(),
            p.getStatus(), p.getException(), p.getReturn());
      }

      try {
        notebook().saveNote(p.getNote(), p.getAuthenticationInfo());
      } catch (IOException e) {
        LOG.error(e.toString(), e);
      }
    }

    p.setStatusToUserParagraph(p.getStatus());
    broadcastParagraph(p.getNote(), p);
    //    for (NoteEventListener listener : notebook().getNoteEventListeners()) {
    //      listener.onParagraphStatusChange(p, after);
    //    }

    try {
      broadcastUpdateNoteJobInfo(System.currentTimeMillis() - 5000);
    } catch (IOException e) {
      LOG.error("can not broadcast for job manager {}", e);
    }
  }


  /**
   * This callback is for paragraph that runs on RemoteInterpreterProcess.
   */
  @Override
  public void onOutputAppend(Paragraph paragraph, int idx, String output) {
    Message msg =
        new Message(OP.PARAGRAPH_APPEND_OUTPUT).put("noteId", paragraph.getNote().getId())
            .put("paragraphId", paragraph.getId()).put("data", output);
    connectionManager.broadcast(paragraph.getNote().getId(), msg);
  }

  /**
   * This callback is for paragraph that runs on RemoteInterpreterProcess.
   */
  @Override
  public void onOutputUpdate(Paragraph paragraph, int idx, InterpreterResultMessage result) {
    Message msg =
        new Message(OP.PARAGRAPH_UPDATE_OUTPUT).put("noteId", paragraph.getNote().getId())
            .put("paragraphId", paragraph.getId()).put("data", result.getData());
    connectionManager.broadcast(paragraph.getNote().getId(), msg);
  }

  @Override
  public void onOutputUpdateAll(Paragraph paragraph, List<InterpreterResultMessage> msgs) {
    // TODO
  }

  @Override
  public void noteRunningStatusChange(String noteId, boolean newStatus) {
    connectionManager.broadcast(
        noteId,
        new Message(OP.NOTE_RUNNING_STATUS
        ).put("status", newStatus));
  }

  private void sendAllAngularObjects(Note note, String user, NotebookSocket conn)
      throws IOException {
    List<InterpreterSetting> settings =
        notebook().getInterpreterSettingManager().getInterpreterSettings(note.getId());
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

      connectionManager.broadcast(note.getId(), new Message(OP.ANGULAR_OBJECT_UPDATE)
          .put("angularObject", object)
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
          notebook.getInterpreterSettingManager().getSettingIds();
      for (String id : settingIds) {
        if (interpreterGroupId.contains(id)) {
          connectionManager.broadcast(note.getId(),
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
    String noteId = connectionManager.getAssociatedNoteId(conn);

    getNotebookService().getEditorSetting(noteId, replName,
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

  private void getInterpreterSettings(NotebookSocket conn)
      throws IOException {
    List<InterpreterSetting> availableSettings = notebook().getInterpreterSettingManager().get();
    conn.send(serializeMessage(
        new Message(OP.INTERPRETER_SETTINGS).put("interpreterSettings", availableSettings)));
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
        String label = metaInfos.get("label");
        String tooltip = metaInfos.get("tooltip");
        List<String> keysToRemove = Arrays.asList("noteId", "paraId", "label", "tooltip");
        for (String removeKey : keysToRemove) {
          metaInfos.remove(removeKey);
        }

        paragraph
            .updateRuntimeInfos(label, tooltip, metaInfos, setting.getGroup(), setting.getId());
        connectionManager.broadcast(
            note.getId(),
            new Message(OP.PARAS_INFO).put("id", paragraphId).put("infos",
                paragraph.getRuntimeInfos()));
      }
    }
  }

  private void broadcastNoteForms(Note note) {
    GUI formsSettings = new GUI();
    formsSettings.setForms(note.getNoteForms());
    formsSettings.setParams(note.getNoteParams());
    connectionManager.broadcast(note.getId(),
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

  @Override
  public Set<String> getConnectedUsers() {
    return connectionManager.getConnectedUsers();
  }

  @Override
  public void sendMessage(String message) {
    Message m = new Message(OP.NOTICE);
    m.data.put("notice", message);
    connectionManager.broadcast(m);
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
