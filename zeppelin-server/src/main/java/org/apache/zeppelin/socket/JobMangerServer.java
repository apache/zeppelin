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

import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.notebook.NotebookEventObserver.NotebookChnagedEvent;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.ticket.TicketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Zeppelin websocket service.
 */
public class JobMangerServer extends AppMainServer implements WebSocketServer, Observer{

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

  private static final Logger LOG = LoggerFactory.getLogger(JobMangerServer.class);

  private Notebook notebook() {
    return ZeppelinServer.notebook;
  }

  @Override
  public void onMessage(WebAppSocket conn, String msg) {
    Notebook notebook = notebook();
    try {
      Message messagereceived = deserializeMessage(msg);
      LOG.debug("RECEIVE << " + messagereceived.op);
      LOG.debug("RECEIVE PRINCIPAL << " + messagereceived.principal);
      LOG.debug("RECEIVE TICKET << " + messagereceived.ticket);
      LOG.debug("RECEIVE ROLES << " + messagereceived.roles);

      if (LOG.isTraceEnabled()) {
        LOG.trace("RECEIVE MSG = " + messagereceived);
      }

      String ticket = TicketContainer.instance.getTicket(messagereceived.principal);
      if (ticket != null && !ticket.equals(messagereceived.ticket))
        throw new Exception("Invalid ticket " + messagereceived.ticket + " != " + ticket);

      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      boolean allowAnonymous = conf.
          getBoolean(ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED);
      if (!allowAnonymous && messagereceived.principal.equals("anonymous")) {
        throw new Exception("Anonymous access not allowed ");
      }

      HashSet<String> userAndRoles = new HashSet<String>();
      userAndRoles.add(messagereceived.principal);
      if (!messagereceived.roles.equals("")) {
        HashSet<String> roles = gson.fromJson(messagereceived.roles,
                new TypeToken<HashSet<String>>(){}.getType());
        if (roles != null) {
          userAndRoles.addAll(roles);
        }
      }

      /** Lets be elegant here */
      switch (messagereceived.op) {
          case LIST_NOTEBOOK_JOBS:
            sendNotebookJobInfo(conn);
            break;
          case LIST_UPDATE_NOTEBOOK_JOBS:
            sendUpdateNotebookJobInfo(conn, messagereceived);
            break;
          case UNSUBSCRIBE_JOBMANAGER:
            unsubscribeJobManager(conn);
            break;
          default:
            break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }


  private void addConnectionToJobManagerService(String serviceType, WebAppSocket socket) {
    addConnectionToKey(serviceType, socket);
  }

  private void removeConnectionToJobManagerService(WebAppSocket socket) {
    removeConnectionFromAllKey(socket);
  }

  protected Message deserializeMessage(String msg) {
    return gson.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
    return gson.toJson(m);
  }

  public void unsubscribeJobManager(WebAppSocket conn) {
    LOG.info("unsubscribe");
    removeConnectionToJobManagerService(conn);
  }

  public void sendNotebookJobInfo(WebAppSocket conn) throws IOException {

    List<Map<String, Object>> notebookJobs = generateNotebooksJobInfo(false);
    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs);

    conn.send(serializeMessage(new Message(OP.LIST_NOTEBOOK_JOBS)
      .put("notebookJobs", response)));
  }

  public void sendUpdateNotebookJobInfo(WebAppSocket conn, Message fromMessage)
      throws IOException {
    double lastUpdateUnixTimeRaw = (double) fromMessage.get("lastUpdateUnixTime");
    long lastUpdateUnixTime = new Double(lastUpdateUnixTimeRaw).longValue();
    List<Map<String, Object>> notebookJobs;
    notebookJobs = generateUpdateNotebooksJobInfo(false, lastUpdateUnixTime);
    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs);

    conn.send(serializeMessage(new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS)
      .put("notebookRunningJobs", response)));
  }

  private Map<String, Object> getParagraphPacketItem(Paragraph paragraph) {
    Map<String, Object> paragraphItem = new HashMap<>();

    // set paragraph id
    paragraphItem.put("id", paragraph.getId());

    // set paragraph name
    String paragraphName = paragraph.getTitle();
    if (paragraphName != null) {
      paragraphItem.put("name", paragraphName);
    } else {
      paragraphItem.put("name", paragraph.getId());
    }

    // set status for paragraph.
    paragraphItem.put("status", paragraph.getStatus().toString());

    return paragraphItem;
  }

  private long getUnixTimeLastRunParagraph(Paragraph paragraph) {

    Date lastRunningDate = null;
    long lastRunningUnixTime = 0;

    Date paragaraphDate = paragraph.getDateStarted();
    if (paragaraphDate == null) {
      paragaraphDate = paragraph.getDateCreated();
    }

    // set last update unixtime(ms).
    if (lastRunningDate == null) {
      lastRunningDate = paragaraphDate;
    } else {
      if (lastRunningDate.after(paragaraphDate) == true) {
        lastRunningDate = paragaraphDate;
      }
    }

    lastRunningUnixTime = lastRunningDate.getTime();

    return lastRunningUnixTime;
  }

  public List<Map<String, Object>> generateNotebooksJobInfo(boolean needsReload) {
    Notebook notebook = notebook();

    ZeppelinConfiguration conf = notebook.getConf();
    String homescreenNotebookId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList = conf
            .getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    if (needsReload) {
      try {
        notebook.reloadAllNotes();
      } catch (IOException e) {
        LOG.error("Fail to reload notes from repository");
      }
    }

    List<Note> notes = notebook.getAllNotes();
    List<Map<String, Object>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      boolean isNotebookRunning = false;
      Map<String, Object> info = new HashMap<>();

      if (hideHomeScreenNotebookFromList && note.id().equals(homescreenNotebookId)) {
        continue;
      }

      String CRON_TYPE_NOTEBOOK_KEYWORD = "cron";
      info.put("notebookId", note.id());
      String notebookName = note.getName();
      if (notebookName != null) {
        info.put("notebookName", note.getName());
      } else {
        info.put("notebookName", "Note " + note.id());
      }

      if (note.getConfig().containsKey(CRON_TYPE_NOTEBOOK_KEYWORD) == true
              && !note.getConfig().get(CRON_TYPE_NOTEBOOK_KEYWORD).equals("")) {
        info.put("notebookType", "cron");
      }
      else {
        info.put("notebookType", "normal");
      }

      long lastRunningUnixTime = 0;

      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
      for (Paragraph paragraph : note.getParagraphs()) {
        if (paragraph.getStatus().isRunning() == true) {
          isNotebookRunning = true;
        }

        Map<String, Object> paragraphItem = getParagraphPacketItem(paragraph);

        lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);

        paragraphsInfo.add(paragraphItem);
      }

      // Interpreter is set does not exist.
      String interpreterGroupName = null;
      if (note.getNoteReplLoader().getInterpreterSettings() != null
              && note.getNoteReplLoader().getInterpreterSettings().size() >= 1) {
        interpreterGroupName = note.getNoteReplLoader().getInterpreterSettings().get(0).getGroup();
      }

      // notebook json object root information.
      info.put("interpreter", interpreterGroupName);
      info.put("isRunningJob", isNotebookRunning);
      info.put("unixTimeLastRun", lastRunningUnixTime);
      info.put("paragraphs", paragraphsInfo);
      notesInfo.add(info);
    }
    return notesInfo;
  }


  public List<Map<String, Object>> generateUpdateNotebooksJobInfo(
          boolean needsReload, long lastUpdateServerUnixTime) {
    Notebook notebook = notebook();

    ZeppelinConfiguration conf = notebook.getConf();
    String homescreenNotebookId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList = conf
            .getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    if (needsReload) {
      try {
        notebook.reloadAllNotes();
      } catch (IOException e) {
        LOG.error("Fail to reload notes from repository");
      }
    }

    List<Note> notes = notebook.getAllNotes();
    List<Map<String, Object>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      boolean isNotebookRunning = false;
      boolean isUpdateNotebook = false;

      Map<String, Object> info = new HashMap<>();

      if (hideHomeScreenNotebookFromList && note.id().equals(homescreenNotebookId)) {
        continue;
      }

      // set const keyword for cron type
      String CRON_TYPE_NOTEBOOK_KEYWORD = "cron";
      info.put("notebookId", note.id());
      String notebookName = note.getName();
      if (notebookName != null) {
        info.put("notebookName", note.getName());
      } else {
        info.put("notebookName", note.id());
      }


      if (note.getConfig().containsKey(CRON_TYPE_NOTEBOOK_KEYWORD) == true
              && !note.getConfig().get(CRON_TYPE_NOTEBOOK_KEYWORD).equals("")) {
        info.put("notebookType", "cron");
      }
      else {
        info.put("notebookType", "normal");
      }

      long lastRunningUnixTime = 0;

      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
      for (Paragraph paragraph : note.getParagraphs()) {

        // check date for update time.
        Date startedDate = paragraph.getDateStarted();
        Date createdDate = paragraph.getDateCreated();
        Date finishedDate = paragraph.getDateFinished();

        if (startedDate != null && startedDate.getTime() > lastUpdateServerUnixTime) {
          isUpdateNotebook = true;
        }
        if (createdDate != null && createdDate.getTime() > lastUpdateServerUnixTime) {
          isUpdateNotebook = true;
        }
        if (finishedDate != null && finishedDate.getTime() > lastUpdateServerUnixTime) {
          isUpdateNotebook = true;
        }

        Map<String, Object> paragraphItem = getParagraphPacketItem(paragraph);

        lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);

        if (paragraph.getStatus().isRunning() == true) {
          isNotebookRunning = true;
          isUpdateNotebook = true;
        }
        paragraphsInfo.add(paragraphItem);
      }

      // Insert only data that has changed.
      if (isUpdateNotebook != true) {
        continue;
      }

      // Interpreter is set does not exist.
      String interpreterGroupName = null;
      if (note.getNoteReplLoader().getInterpreterSettings() != null
              && note.getNoteReplLoader().getInterpreterSettings().size() >= 1) {
        interpreterGroupName = note.getNoteReplLoader().getInterpreterSettings().get(0).getGroup();
      }

      // set notebook root information.
      info.put("interpreter", interpreterGroupName);
      info.put("isRunningJob", isNotebookRunning);
      info.put("unixTimeLastRun", lastRunningUnixTime);
      info.put("paragraphs", paragraphsInfo);
      notesInfo.add(info);
    }
    if (notesInfo.size() > 0) {
      LOG.info("update count {}", notesInfo.size());
    }
    return notesInfo;
  }

  public boolean broadUpdateNote(String noteId) {

    Note note = notebook().getNote(noteId);

    if (note == null) {
      LOG.info("broadUpdateNote - not found note");
      return false;
    }

    List<Map<String, Object>> notesList = new LinkedList<>();

    Map<String, Object> noteItem = new HashMap<>();

    noteItem.put("notebookId", note.id());
    String notebookName = note.getName();
    if (notebookName != null) {
      noteItem.put("notebookName", note.getName());
    } else {
      noteItem.put("notebookName", note.id());
    }

    // set const keyword for cron type
    String CRON_TYPE_NOTEBOOK_KEYWORD = "cron";
    if (note.getConfig().containsKey(CRON_TYPE_NOTEBOOK_KEYWORD) == true
            && !note.getConfig().get(CRON_TYPE_NOTEBOOK_KEYWORD).equals("")) {
      noteItem.put("notebookType", "cron");
    }
    else {
      noteItem.put("notebookType", "normal");
    }


    long lastRunningUnixTime = 0;
    boolean isNotebookRunning = false;
    List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
    for (Paragraph paragraph : note.getParagraphs()) {
      Map<String, Object> paragraphItem = getParagraphPacketItem(paragraph);

      if (paragraph.getStatus().isRunning() == true) {
        isNotebookRunning = true;
      }

      lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);

      paragraphsInfo.add(paragraphItem);
    }

    // Interpreter is set does not exist.
    String interpreterGroupName = null;
    if (note.getNoteReplLoader().getInterpreterSettings() != null
            && note.getNoteReplLoader().getInterpreterSettings().size() >= 1) {
      interpreterGroupName = note.getNoteReplLoader().getInterpreterSettings().get(0).getGroup();
    }

    // set notebook root information.
    noteItem.put("interpreter", interpreterGroupName);
    noteItem.put("isRunningJob", isNotebookRunning);
    noteItem.put("unixTimeLastRun", lastRunningUnixTime);
    noteItem.put("paragraphs", paragraphsInfo);
    notesList.add(noteItem);

    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notesList);

    broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
      new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));

    return true;
  }

  public boolean broadRemovedNote(String noteId) {

    List<Map<String, Object>> notesList = new LinkedList<>();
    Map<String, Object> noteItem = new HashMap<>();

    // set notebook root information.
    noteItem.put("notebookId", noteId);
    noteItem.put("isRemoved", true);
    notesList.add(noteItem);

    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notesList);

    broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
            new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));

    return true;
  }

  /**
   * Notebook Observer Event Listener
   */
  @Override
  public void update(Observable observer, Object notebookChnagedEvent) {
    NotebookChnagedEvent noteEvent = (NotebookChnagedEvent) notebookChnagedEvent;
    LOG.info("event note {}-{}", noteEvent.getNoteId(), noteEvent.getAction().name());
    try {
      if (noteEvent.getAction() == NotebookEventObserver.ACTIONS.REMOVED) {
        broadRemovedNote(noteEvent.getNoteId());
      } else {
        broadUpdateNote(noteEvent.getNoteId());
      }

    } catch (Exception e) {
      LOG.info("socket error job {}", e.getMessage());
    }
  }

}

