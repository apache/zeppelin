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


package org.apache.zeppelin.service;


import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.apache.zeppelin.scheduler.Job.Status.ABORT;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.exception.CorruptedNoteException;
import org.apache.zeppelin.notebook.exception.NotePathAlreadyExistsException;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.rest.exception.NoteNotFoundException;
import org.apache.zeppelin.rest.exception.ParagraphNotFoundException;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Service class for Notebook related operations. It use {@link Notebook} which provides
 * high level api to access notes.
 *
 * In most of methods, this class will check permission first and whether this note existed.
 * If the operation succeeed, {@link ServiceCallback#onSuccess(Object, ServiceContext)} should be
 * called, otherwise {@link ServiceCallback#onFailure(Exception, ServiceContext)} should be called.
 *
 */
public class NotebookService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookService.class);
  private static final DateTimeFormatter TRASH_CONFLICT_TIMESTAMP_FORMATTER =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private final ZeppelinConfiguration zConf;
  private final Notebook notebook;
  private final AuthorizationService authorizationService;
  private final SchedulerService schedulerService;

  @Inject
  public NotebookService(
      Notebook notebook,
      AuthorizationService authorizationService,
      ZeppelinConfiguration zeppelinConfiguration,
      SchedulerService schedulerService) {
    this.notebook = notebook;
    this.authorizationService = authorizationService;
    this.zConf = zeppelinConfiguration;
    this.schedulerService = schedulerService;
  }

  public Note getHomeNote(ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    String noteId = notebook.getConf().getString(ZEPPELIN_NOTEBOOK_HOMESCREEN);
    Note note = null;
    if (noteId != null) {
      note = notebook.getNote(noteId);
      if (note != null) {
        if (!checkPermission(noteId, Permission.READER, Message.OP.GET_HOME_NOTE, context,
                callback)) {
          return null;
        }
      }
    }
    callback.onSuccess(note, context);
    return note;
  }

  public Note getNote(String noteId,
                      ServiceContext context,
                      ServiceCallback<Note> callback) throws IOException {
    return getNote(noteId, false, context, callback);
  }

  public Note getNote(String noteId,
                      boolean reload,
                      ServiceContext context,
                      ServiceCallback<Note> callback) throws IOException {
    Note note = notebook.getNote(noteId, reload);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return null;
    }

    if (!checkPermission(noteId, Permission.READER, Message.OP.GET_NOTE, context,
        callback)) {
      return null;
    }
    if (note.isPersonalizedMode()) {
      note = note.getUserNote(context.getAutheInfo().getUser());
    }
    callback.onSuccess(note, context);
    return note;
  }


  /**
   *
   * @param notePath
   * @param defaultInterpreterGroup
   * @param addingEmptyParagraph
   * @param context
   * @param callback
   * @return
   * @throws IOException
   */
  public Note createNote(String notePath,
                         String defaultInterpreterGroup,
                         boolean addingEmptyParagraph,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {

    if (defaultInterpreterGroup == null) {
      defaultInterpreterGroup = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    }

    try {
      Note note = notebook.createNote(normalizeNotePath(notePath), defaultInterpreterGroup,
          context.getAutheInfo(), false);
      // it's an empty note. so add one paragraph
      if (addingEmptyParagraph) {
        note.addNewParagraph(context.getAutheInfo());
      }
      notebook.saveNote(note, context.getAutheInfo());
      callback.onSuccess(note, context);
      return note;
    } catch (IOException e) {
      callback.onFailure(e, context);
      return null;
    }
  }

  /**
   * normalize both note name and note folder
   *
   * @param notePath
   * @return
   * @throws IOException
   */
  String normalizeNotePath(String notePath) throws IOException {
    if (StringUtils.isBlank(notePath)) {
      notePath = "/Untitled Note";
    }
    if (!notePath.startsWith("/")) {
      notePath = "/" + notePath;
    }

    notePath = notePath.replace("\r", " ").replace("\n", " ");
    int pos = notePath.lastIndexOf("/");
    if ((notePath.length() - pos) > 255) {
      throw new IOException("Note name must be less than 255");
    }

    if (notePath.contains("..")) {
      throw new IOException("Note name can not contain '..'");
    }
    return notePath;
  }

  public void removeNote(String noteId,
                         ServiceContext context,
                         ServiceCallback<String> callback) throws IOException {
    if (!checkPermission(noteId, Permission.OWNER, Message.OP.DEL_NOTE, context, callback)) {
      return;
    }

    Note note = null;
    try {
      note = notebook.getNote(noteId);
    } catch (CorruptedNoteException e) {
      notebook.removeCorruptedNote(noteId, context.getAutheInfo());
      callback.onSuccess("Delete note successfully", context);
      return;
    }

    if (note != null) {
      notebook.removeNote(note, context.getAutheInfo());
      callback.onSuccess("Delete note successfully", context);
    } else {
      callback.onFailure(new NoteNotFoundException(noteId), context);
    }
  }

  public List<NoteInfo> listNotesInfo(boolean needsReload,
                                      ServiceContext context,
                                      ServiceCallback<List<NoteInfo>> callback)
      throws IOException {
    if (needsReload) {
      try {
        notebook.reloadAllNotes(context.getAutheInfo());
      } catch (IOException e) {
        LOGGER.error("Fail to reload notes from repository", e);
      }
    }
    List<NoteInfo> notesInfo = notebook.getNotesInfo(
            noteId -> authorizationService.isReader(noteId, context.getUserAndRoles()));
    callback.onSuccess(notesInfo, context);
    return notesInfo;
  }

  public void renameNote(String noteId,
                         String newNotePath,
                         boolean isRelative,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.OWNER, Message.OP.NOTE_RENAME, context, callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note != null) {
      note.setCronSupported(notebook.getConf());
      if (isRelative && !note.getParentPath().equals("/")) {
        newNotePath = note.getParentPath() + "/" + newNotePath;
      } else {
        if (!newNotePath.startsWith("/")) {
          newNotePath = "/" + newNotePath;
        }
      }
      try {
        notebook.moveNote(noteId, newNotePath, context.getAutheInfo());
        callback.onSuccess(note, context);
      } catch (NotePathAlreadyExistsException e) {
        callback.onFailure(e, context);
      }
    } else {
      callback.onFailure(new NoteNotFoundException(noteId), context);
    }

  }

  public Note cloneNote(String noteId,
                        String newNotePath,
                        ServiceContext context,
                        ServiceCallback<Note> callback) throws IOException {
    //TODO(zjffdu) move these to Notebook
    if (StringUtils.isBlank(newNotePath)) {
      newNotePath = "/Cloned Note_" + noteId;
    }
    try {
      Note newNote = notebook.cloneNote(noteId, normalizeNotePath(newNotePath),
          context.getAutheInfo());
      callback.onSuccess(newNote, context);
      return newNote;
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to clone note", e), context);
      return null;
    }
  }

  public Note importNote(String notePath,
                         String noteJson,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    try {
      // pass notePath when it is null
      Note note = notebook.importNote(noteJson, notePath == null ?
              notePath : normalizeNotePath(notePath),
          context.getAutheInfo());
      callback.onSuccess(note, context);
      return note;
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to import note: " + e.getMessage(), e), context);
      return null;
    }
  }

  /**
   * Executes given paragraph with passed paragraph info like noteId, paragraphId, title, text and etc.
   *
   * @param noteId
   * @param paragraphId
   * @param title
   * @param text
   * @param params
   * @param config
   * @param failIfDisabled
   * @param blocking
   * @param context
   * @param callback
   * @return return true only when paragraph execution finished, it could end with succeed or error due to user code.
   * return false when paragraph execution fails due to zeppelin internal issue.
   * @throws IOException
   */
  public boolean runParagraph(String noteId,
                              String paragraphId,
                              String title,
                              String text,
                              Map<String, Object> params,
                              Map<String, Object> config,
                              String sessionId,
                              boolean failIfDisabled,
                              boolean blocking,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {

    LOGGER.info("Start to run paragraph: {} of note: {}", paragraphId, noteId);
    if (!checkPermission(noteId, Permission.RUNNER, Message.OP.RUN_PARAGRAPH, context, callback)) {
      return false;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return false;
    }
    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      callback.onFailure(new ParagraphNotFoundException(paragraphId), context);
      return false;
    }
    if (failIfDisabled && !p.isEnabled()) {
      callback.onFailure(new IOException("paragraph is disabled."), context);
      return false;
    }
    p.setText(text);
    p.setTitle(title);
    p.setAuthenticationInfo(context.getAutheInfo());
    if (params != null && !params.isEmpty()) {
      p.settings.setParams(params);
    }
    if (config != null && !config.isEmpty()) {
      p.mergeConfig(config);
    }

    if (note.isPersonalizedMode()) {
      p = p.getUserParagraph(context.getAutheInfo().getUser());
      p.setText(text);
      p.setTitle(title);
      p.setAuthenticationInfo(context.getAutheInfo());
      if (params != null && !params.isEmpty()) {
        p.settings.setParams(params);
      }
      if (config != null && !config.isEmpty()) {
        p.mergeConfig(config);
      }
    }

    try {
      notebook.saveNote(note, context.getAutheInfo());
      note.run(p.getId(), sessionId, blocking, context.getAutheInfo().getUser());
      callback.onSuccess(p, context);
      return true;
    } catch (Exception ex) {
      LOGGER.error("Exception from run", ex);
      p.setReturn(new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage()), ex);
      p.setStatus(Job.Status.ERROR);
      // don't call callback.onFailure, we just need to display the error message
      // in paragraph result section instead of pop up the error window.
      return false;
    }
  }

  /**
   * Run list of paragraphs. This method runs provided paragraphs one by one, synchronously.
   * When a paragraph fails, subsequent paragraphs are not going to run and this method returns false.
   * When list of paragraphs provided from argument is null, list of paragraphs stored in the Note will be used.
   *
   * @param noteId
   * @param paragraphs list of paragraphs to run (passed from frontend). Run note directly when it is null.
   * @param context
   * @param callback
   * @return true when all paragraphs successfully run. false when any paragraph fails.
   * @throws IOException
   */
  public boolean runAllParagraphs(String noteId,
                                  List<Map<String, Object>> paragraphs,
                                  ServiceContext context,
                                  ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.RUNNER, Message.OP.RUN_ALL_PARAGRAPHS, context,
        callback)) {
      return false;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return false;
    }

    if (paragraphs != null) {
      for (Map<String, Object> raw : paragraphs) {
        String paragraphId = (String) raw.get("id");
        if (paragraphId == null) {
          LOGGER.warn("No id found in paragraph json: {}", raw);
          continue;
        }
        String text = (String) raw.get("paragraph");
        String title = (String) raw.get("title");
        Map<String, Object> params = (Map<String, Object>) raw.get("params");
        Map<String, Object> config = (Map<String, Object>) raw.get("config");
        Paragraph p = note.getParagraph(paragraphId);
        p.setText(text);
        p.setTitle(title);
        p.setConfig(config);
        p.settings.setParams(params);
      }
    }

    try {
      boolean isolated = zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_RUN_ALL_ISOLATED);
      note.runAll(context.getAutheInfo(), true, isolated, new HashMap<>());
      return true;
    } catch (Exception e) {
      LOGGER.warn("Fail to run note: {}", note.getName(), e);
      return false;
    }
  }

  public void cancelParagraph(String noteId,
                              String paragraphId,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.RUNNER, Message.OP.CANCEL_PARAGRAPH, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      throw new NoteNotFoundException(noteId);
    }
    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      throw new ParagraphNotFoundException(paragraphId);
    }
    p.abort();
    callback.onSuccess(p, context);
  }

  public void moveParagraph(String noteId,
                            String paragraphId,
                            int newIndex,
                            ServiceContext context,
                            ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.MOVE_PARAGRAPH, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      throw new NoteNotFoundException(noteId);
    }
    if (note.getParagraph(paragraphId) == null) {
      throw new ParagraphNotFoundException(paragraphId);
    }
    if (newIndex >= note.getParagraphCount()) {
      callback.onFailure(new BadRequestException("newIndex " + newIndex + " is out of bounds"),
          context);
      return;
    }
    note.moveParagraph(paragraphId, newIndex);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(note.getParagraph(newIndex), context);
  }

  public void removeParagraph(String noteId,
                              String paragraphId,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_REMOVE, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      throw new NoteNotFoundException(noteId);
    }
    if (note.getParagraph(paragraphId) == null) {
      throw new ParagraphNotFoundException(paragraphId);
    }
    Paragraph p = note.removeParagraph(context.getAutheInfo().getUser(), paragraphId);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(p, context);
  }

  public Paragraph insertParagraph(String noteId,
                                   int index,
                                   Map<String, Object> config,
                                   ServiceContext context,
                                   ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.INSERT_PARAGRAPH, context,
        callback)) {
      return null;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      throw new NoteNotFoundException(noteId);
    }
    Paragraph newPara = note.insertNewParagraph(index, context.getAutheInfo());
    newPara.mergeConfig(config);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(newPara, context);
    return newPara;
  }

  public void restoreNote(String noteId,
                          ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.RESTORE_NOTE, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!note.getPath().startsWith("/" + NoteManager.TRASH_FOLDER)) {
      callback.onFailure(new IOException("Can not restore this note " + note.getPath() +
          " as it is not in trash folder"), context);
      return;
    }
    try {
      String destNotePath = note.getPath().replace("/" + NoteManager.TRASH_FOLDER, "");
      notebook.moveNote(noteId, destNotePath, context.getAutheInfo());
      callback.onSuccess(note, context);
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to restore note: " + noteId, e), context);
    }

  }

  public void restoreFolder(String folderPath,
                            ServiceContext context,
                            ServiceCallback<Void> callback) throws IOException {

    if (!folderPath.startsWith("/" + NoteManager.TRASH_FOLDER)) {
      callback.onFailure(new IOException("Can not restore this folder: " + folderPath +
          " as it is not in trash folder"), context);
      return;
    }
    try {
      String destFolderPath = folderPath.replace("/" + NoteManager.TRASH_FOLDER, "");
      notebook.moveFolder(folderPath, destFolderPath, context.getAutheInfo());
      callback.onSuccess(null, context);
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to restore folder: " + folderPath, e), context);
    }

  }


  public void restoreAll(ServiceContext context,
                         ServiceCallback<?> callback) throws IOException {

    try {
      notebook.restoreAll(context.getAutheInfo());
      callback.onSuccess(null, context);
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to restore all", e), context);
    }
  }


  public void updateParagraph(String noteId,
                              String paragraphId,
                              String title,
                              String text,
                              Map<String, Object> params,
                              Map<String, Object> config,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.COMMIT_PARAGRAPH, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }
    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      callback.onFailure(new ParagraphNotFoundException(paragraphId), context);
      return;
    }

    p.settings.setParams(params);
    p.mergeConfig(config);
    p.setTitle(title);
    p.setText(text);
    if (note.isPersonalizedMode()) {
      p = p.getUserParagraph(context.getAutheInfo().getUser());
      p.settings.setParams(params);
      p.mergeConfig(config);
      p.setTitle(title);
      p.setText(text);
    }
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(p, context);
  }

  public Paragraph getNextSessionParagraph(String noteId,
                                        int maxParagraph,
                                        ServiceContext context,
                                        ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_OUTPUT, context,
            callback)) {
      throw new IOException("No privilege to access this note");
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      throw new IOException("No such note");
    }
    synchronized (this) {
      if (note.getParagraphCount() >= maxParagraph) {
        boolean removed = false;
        for (int i = 1; i < note.getParagraphCount(); ++i) {
          if (note.getParagraph(i).getStatus().isCompleted()) {
            note.removeParagraph(context.getAutheInfo().getUser(), note.getParagraph(i).getId());
            removed = true;
            break;
          }
        }
        if (!removed) {
          throw new IOException("All the paragraphs are not completed, unable to find available paragraph");
        }
      }
      return note.addNewParagraph(context.getAutheInfo());
    }
  }

  public void clearParagraphOutput(String noteId,
                                   String paragraphId,
                                   ServiceContext context,
                                   ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_OUTPUT, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }
    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      callback.onFailure(new ParagraphNotFoundException(paragraphId), context);
      return;
    }
    Paragraph returnedParagraph;
    if (note.isPersonalizedMode()) {
      returnedParagraph = note.clearPersonalizedParagraphOutput(paragraphId,
          context.getAutheInfo().getUser());
    } else {
      note.clearParagraphOutput(paragraphId);
      returnedParagraph = note.getParagraph(paragraphId);
    }
    callback.onSuccess(returnedParagraph, context);
  }

  public void clearAllParagraphOutput(String noteId,
                                      ServiceContext context,
                                      ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_ALL_OUTPUT, context,
        callback)) {
      return;
    }
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    note.clearAllParagraphOutput();
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(note, context);
  }


  public void updateNote(String noteId,
                         String name,
                         Map<String, Object> config,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.NOTE_UPDATE, context,
        callback)) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!(Boolean) note.getConfig().get("isZeppelinNotebookCronEnable")) {
      if (config.get("cron") != null) {
        config.remove("cron");
      }
    }
    boolean cronUpdated = isCronUpdated(config, note.getConfig());
    note.setName(name);
    note.setConfig(config);
    if (cronUpdated) {
      schedulerService.refreshCron(note.getId());
    }

    notebook.updateNote(note, context.getAutheInfo());
    callback.onSuccess(note, context);
  }


  private boolean isCronUpdated(Map<String, Object> configA, Map<String, Object> configB) {
    boolean cronUpdated = false;
    if (configA.get("cron") != null && configB.get("cron") != null && configA.get("cron")
        .equals(configB.get("cron"))) {
      cronUpdated = true;
    } else if (configA.get("cron") != null || configB.get("cron") != null) {
      cronUpdated = true;
    }

    return cronUpdated;
  }

  public void saveNoteForms(String noteId,
                            Map<String, Object> noteParams,
                            ServiceContext context,
                            ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.SAVE_NOTE_FORMS, context,
        callback)) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    note.setNoteParams(noteParams);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(note, context);
  }

  public void removeNoteForms(String noteId,
                              String formName,
                              ServiceContext context,
                              ServiceCallback<Note> callback) throws IOException {
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!checkPermission(noteId, Permission.WRITER, Message.OP.REMOVE_NOTE_FORMS, context,
        callback)) {
      return;
    }

    note.getNoteForms().remove(formName);
    note.getNoteParams().remove(formName);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(note, context);
  }

  public NotebookRepoWithVersionControl.Revision checkpointNote(
      String noteId,
      String commitMessage,
      ServiceContext context,
      ServiceCallback<NotebookRepoWithVersionControl.Revision> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return null;
    }

    if (!checkPermission(noteId, Permission.WRITER, Message.OP.REMOVE_NOTE_FORMS, context,
        callback)) {
      return null;
    }

    NotebookRepoWithVersionControl.Revision revision =
        notebook.checkpointNote(noteId, note.getPath(), commitMessage, context.getAutheInfo());
    callback.onSuccess(revision, context);
    return revision;
  }

  public List<NotebookRepoWithVersionControl.Revision> listRevisionHistory(
      String noteId,
      ServiceContext context,
      ServiceCallback<List<NotebookRepoWithVersionControl.Revision>> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return null;
    }

    // TODO(zjffdu) Disable checking permission for now, otherwise zeppelin will send 2 AUTH_INFO
    // message to frontend when frontend try to get note without proper privilege.
    //    if (!checkPermission(noteId, Permission.READER, Message.OP.LIST_REVISION_HISTORY, context,
    //        callback)) {
    //      return null;
    //    }
    List<NotebookRepoWithVersionControl.Revision> revisions =
        notebook.listRevisionHistory(noteId, note.getPath(), context.getAutheInfo());
    callback.onSuccess(revisions, context);
    return revisions;
  }


  public Note setNoteRevision(String noteId,
                              String revisionId,
                              ServiceContext context,
                              ServiceCallback<Note> callback) throws IOException {
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return null;
    }

    if (!checkPermission(noteId, Permission.WRITER, Message.OP.SET_NOTE_REVISION, context,
        callback)) {
      return null;
    }

    try {
      Note resultNote = notebook.setNoteRevision(noteId, note.getPath(), revisionId,
          context.getAutheInfo());
      callback.onSuccess(resultNote, context);
      return resultNote;
    } catch (Exception e) {
      callback.onFailure(new IOException("Fail to set given note revision", e), context);
      return null;
    }
  }

  public void getNotebyRevision(String noteId,
                                String revisionId,
                                ServiceContext context,
                                ServiceCallback<Note> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!checkPermission(noteId, Permission.READER, Message.OP.NOTE_REVISION, context,
        callback)) {
      return;
    }
    Note revisionNote = notebook.getNoteByRevision(noteId, note.getPath(), revisionId,
        context.getAutheInfo());
    callback.onSuccess(revisionNote, context);
  }

  public void getNoteByRevisionForCompare(String noteId,
                                          String revisionId,
                                          ServiceContext context,
                                          ServiceCallback<Note> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!checkPermission(noteId, Permission.READER, Message.OP.NOTE_REVISION_FOR_COMPARE, context,
        callback)) {
      return;
    }
    Note revisionNote;
    if (revisionId.equals("Head")) {
      revisionNote = note;
    } else {
      revisionNote = notebook.getNoteByRevision(noteId, note.getPath(), revisionId,
          context.getAutheInfo());
    }
    callback.onSuccess(revisionNote, context);
  }

  public List<InterpreterCompletion> completion(
      String noteId,
      String paragraphId,
      String buffer,
      int cursor,
      ServiceContext context,
      ServiceCallback<List<InterpreterCompletion>> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return null;
    }

    if (!checkPermission(noteId, Permission.WRITER, Message.OP.COMPLETION, context,
        callback)) {
      return null;
    }

    try {
      List<InterpreterCompletion> completions = note.completion(paragraphId, buffer, cursor,
              context.getAutheInfo());
      callback.onSuccess(completions, context);
      return completions;
    } catch (RuntimeException e) {
      callback.onFailure(new IOException("Fail to get completion", e), context);
      return null;
    }
  }

  public void getEditorSetting(String noteId,
                               String paragraphText,
                               ServiceContext context,
                               ServiceCallback<Map<String, Object>> callback) throws IOException {
    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }
    try {
      Map<String, Object> settings = notebook.getInterpreterSettingManager().
          getEditorSetting(paragraphText, noteId);
      callback.onSuccess(settings, context);
    } catch (Exception e) {
      callback.onFailure(new IOException("Fail to getEditorSetting", e), context);
    }
  }

  public void updatePersonalizedMode(String noteId,
                                     boolean isPersonalized,
                                     ServiceContext context,
                                     ServiceCallback<Note> callback) throws IOException {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    if (!checkPermission(noteId, Permission.WRITER, Message.OP.UPDATE_PERSONALIZED_MODE, context,
        callback)) {
      return;
    }

    note.setPersonalizedMode(isPersonalized);
    notebook.saveNote(note, context.getAutheInfo());
    callback.onSuccess(note, context);
  }

  public void moveNoteToTrash(String noteId,
                              ServiceContext context,
                              ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.OWNER, Message.OP.MOVE_NOTE_TO_TRASH, context, callback)) {
      return;
    }

    String destNotePath = "/" + NoteManager.TRASH_FOLDER + notebook.getNoteManager().getNotesInfo().get(noteId);
    if (notebook.containsNote(destNotePath)) {
      destNotePath = destNotePath + " " + TRASH_CONFLICT_TIMESTAMP_FORMATTER.format(Instant.now());
    }

    Note note = null;
    try {
       note = notebook.getNote(noteId);
    } catch (CorruptedNoteException e) {
        LOGGER.info("Move corrupted note to trash");
        notebook.moveNote(noteId, destNotePath, context.getAutheInfo());
        return;
    }

    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    notebook.moveNote(noteId, destNotePath, context.getAutheInfo());
    callback.onSuccess(note, context);
  }

  public void moveFolderToTrash(String folderPath,
                              ServiceContext context,
                              ServiceCallback<Void> callback) throws IOException {

    //TODO(zjffdu) folder permission check
    //TODO(zjffdu) folderPath is relative path, need to fix it in frontend
    LOGGER.info("Move folder {} to trash", folderPath);

    String destFolderPath = "/" + NoteManager.TRASH_FOLDER + "/" + folderPath;
    if (notebook.containsNote(destFolderPath)) {
      destFolderPath = destFolderPath + " " +
          TRASH_CONFLICT_TIMESTAMP_FORMATTER.format(Instant.now());
    }

    notebook.moveFolder("/" + folderPath, destFolderPath, context.getAutheInfo());
    callback.onSuccess(null, context);
  }

  public void emptyTrash(ServiceContext context,
                         ServiceCallback<Void> callback) throws IOException {

    try {
      notebook.emptyTrash(context.getAutheInfo());
      callback.onSuccess(null, context);
    } catch (IOException e) {
      callback.onFailure(e, context);
    }

  }

  public List<NoteInfo> removeFolder(String folderPath,
                           ServiceContext context,
                           ServiceCallback<List<NoteInfo>> callback) throws IOException {
    try {
      notebook.removeFolder(folderPath, context.getAutheInfo());
      List<NoteInfo> notesInfo = notebook.getNotesInfo(
              noteId -> authorizationService.isReader(noteId, context.getUserAndRoles()));
      callback.onSuccess(notesInfo, context);
      return notesInfo;
    } catch (IOException e) {
      callback.onFailure(e, context);
      return null;
    }
  }

  public List<NoteInfo> renameFolder(String folderPath,
                           String newFolderPath,
                           ServiceContext context,
                           ServiceCallback<List<NoteInfo>> callback) throws IOException {
    //TODO(zjffdu) folder permission check

    try {
      notebook.moveFolder(normalizeNotePath(folderPath),
              normalizeNotePath(newFolderPath), context.getAutheInfo());
      List<NoteInfo> notesInfo = notebook.getNotesInfo(
              noteId -> authorizationService.isReader(noteId, context.getUserAndRoles()));
      callback.onSuccess(notesInfo, context);
      return notesInfo;
    } catch (IOException e) {
      callback.onFailure(e, context);
      return null;
    }
  }

  public void spell(String noteId,
                    Message message,
                    ServiceContext context,
                    ServiceCallback<Paragraph> callback) throws IOException {

    try {
      if (!checkPermission(noteId, Permission.RUNNER, Message.OP.RUN_PARAGRAPH_USING_SPELL, context,
          callback)) {
        return;
      }

      String paragraphId = (String) message.get("id");
      if (paragraphId == null) {
        return;
      }

      String text = (String) message.get("paragraph");
      String title = (String) message.get("title");
      Job.Status status = Job.Status.valueOf((String) message.get("status"));
      Map<String, Object> params = (Map<String, Object>) message.get("params");
      Map<String, Object> config = (Map<String, Object>) message.get("config");

      Note note = notebook.getNote(noteId);
      Paragraph p = setParagraphUsingMessage(note, message, paragraphId,
          text, title, params, config);
      p.setResult((InterpreterResult) message.get("results"));
      p.setErrorMessage((String) message.get("errorMessage"));
      p.setStatusWithoutNotification(status);

      // Spell uses ISO 8601 formatted string generated from moment
      String dateStarted = (String) message.get("dateStarted");
      String dateFinished = (String) message.get("dateFinished");
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");

      try {
        p.setDateStarted(df.parse(dateStarted));
      } catch (ParseException e) {
        LOGGER.error("Failed parse dateStarted", e);
      }

      try {
        p.setDateFinished(df.parse(dateFinished));
      } catch (ParseException e) {
        LOGGER.error("Failed parse dateFinished", e);
      }

      addNewParagraphIfLastParagraphIsExecuted(note, p);
      notebook.saveNote(note, context.getAutheInfo());
      callback.onSuccess(p, context);
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to run spell", e), context);
    }

  }

  private void addNewParagraphIfLastParagraphIsExecuted(Note note, Paragraph p) {
    // if it's the last paragraph and not empty, let's add a new one
    boolean isTheLastParagraph = note.isLastParagraph(p.getId());
    if (!(StringUtils.isEmpty(p.getText()) ||
      StringUtils.isEmpty(p.getScriptText())) &&
        isTheLastParagraph) {
      note.addNewParagraph(p.getAuthenticationInfo());
    }
  }


  private Paragraph setParagraphUsingMessage(Note note, Message fromMessage, String paragraphId,
                                             String text, String title, Map<String, Object> params,
                                             Map<String, Object> config) {
    Paragraph p = note.getParagraph(paragraphId);
    p.setText(text);
    p.setTitle(title);
    AuthenticationInfo subject =
        new AuthenticationInfo(fromMessage.principal, fromMessage.roles, fromMessage.ticket);
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

  public void updateAngularObject(String noteId, String paragraphId, String interpreterGroupId,
                                  String varName, Object varValue,
                                  ServiceContext context,
                                  ServiceCallback<AngularObject> callback) throws IOException {

    String user = context.getAutheInfo().getUser();
    AngularObject ao = null;
    boolean global = false;
    // propagate change to (Remote) AngularObjectRegistry
    Note note = notebook.getNote(noteId);
    if (note != null) {
      List<InterpreterSetting> settings =
              note.getBindedInterpreterSettings(new ArrayList(context.getUserAndRoles()));
      for (InterpreterSetting setting : settings) {
        if (setting.getInterpreterGroup(user, note.getId()) == null) {
          continue;
        }
        if (interpreterGroupId.equals(setting.getInterpreterGroup(user, note.getId())
            .getId())) {
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
                LOGGER.warn("Object {} is not binded", varName);
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

    callback.onSuccess(ao, context);
  }

  public void patchParagraph(final String noteId, final String paragraphId, String patchText,
                             ServiceContext context,
                             ServiceCallback<String> callback) throws IOException {

    try {
      if (!checkPermission(noteId, Permission.WRITER, Message.OP.PATCH_PARAGRAPH, context,
          callback)) {
        return;
      }


      Note note = notebook.getNote(noteId);
      if (note == null) {
        return;
      }
      Paragraph p = note.getParagraph(paragraphId);
      if (p == null) {
        return;
      }

      DiffMatchPatch dmp = new DiffMatchPatch();
      LinkedList<DiffMatchPatch.Patch> patches = null;
      try {
        patches = (LinkedList<DiffMatchPatch.Patch>) dmp.patchFromText(patchText);
      } catch (ClassCastException e) {
        LOGGER.error("Failed to parse patches", e);
      }
      if (patches == null) {
        return;
      }

      String paragraphText = p.getText() == null ? "" : p.getText();
      paragraphText = (String) dmp.patchApply(patches, paragraphText)[0];
      p.setText(paragraphText);
      callback.onSuccess(patchText, context);
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to patch", e), context);
    }
  }


  enum Permission {
    READER,
    WRITER,
    RUNNER,
    OWNER,
  }

  /**
   * Return null when it is allowed, otherwise return the error message which could be
   * propagated to frontend
   *
   * @param noteId
   * @param context
   * @param permission
   * @param op
   * @return
   */
  private <T> boolean checkPermission(String noteId,
                                      Permission permission,
                                      Message.OP op,
                                      ServiceContext context,
                                      ServiceCallback<T> callback) throws IOException {
    boolean isAllowed = false;
    Set<String> allowed = null;
    switch (permission) {
      case READER:
        isAllowed = authorizationService.isReader(noteId, context.getUserAndRoles());
        allowed = authorizationService.getReaders(noteId);
        break;
      case WRITER:
        isAllowed = authorizationService.isWriter(noteId, context.getUserAndRoles());
        allowed = authorizationService.getWriters(noteId);
        break;
      case RUNNER:
        isAllowed = authorizationService.isRunner(noteId, context.getUserAndRoles());
        allowed = authorizationService.getRunners(noteId);
        break;
      case OWNER:
        isAllowed = authorizationService.isOwner(noteId, context.getUserAndRoles());
        allowed = authorizationService.getOwners(noteId);
        break;
    }
    if (isAllowed) {
      return true;
    } else {
      String errorMsg = "Insufficient privileges to " + permission + " note.\n" +
          "Allowed users or roles: " + allowed + "\n" + "But the user " +
          context.getAutheInfo().getUser() + " belongs to: " + context.getUserAndRoles();
      callback.onFailure(new ForbiddenException(errorMsg), context);
      return false;
    }
  }
}
