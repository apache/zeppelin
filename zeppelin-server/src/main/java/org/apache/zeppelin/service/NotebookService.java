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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.Folder;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.rest.exception.NoteNotFoundException;
import org.apache.zeppelin.rest.exception.ParagraphNotFoundException;
import org.apache.zeppelin.scheduler.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN;

/**
 * Service class for Notebook related operations.
 */
public class NotebookService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookService.class);

  private ZeppelinConfiguration zConf;
  private Notebook notebook;
  private NotebookAuthorization notebookAuthorization;

  public NotebookService(Notebook notebook) {
    this.notebook = notebook;
    this.notebookAuthorization = notebook.getNotebookAuthorization();
    this.zConf = notebook.getConf();
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
      } else {
        callback.onFailure(new Exception("configured HomePage is not existed"), context);
      }
    }
    callback.onSuccess(note, context);
    return note;
  }

  public Note getNote(String noteId,
                      ServiceContext context,
                      ServiceCallback<Note> callback) throws IOException {
    Note note = notebook.getNote(noteId);
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


  public Note createNote(String defaultInterpreterGroup,
                         String noteName,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    if (defaultInterpreterGroup == null) {
      defaultInterpreterGroup = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    }
    if (noteName == null) {
      noteName = "Untitled Note";
    }
    try {
      Note note = notebook.createNote(defaultInterpreterGroup, context.getAutheInfo());
      note.addNewParagraph(context.getAutheInfo()); // it's an empty note. so add one paragraph
      note.setName(noteName);
      note.setCronSupported(notebook.getConf());
      note.persist(context.getAutheInfo());
      callback.onSuccess(note, context);
      return note;
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to create Note", e), context);
      return null;
    }
  }


  public void removeNote(String noteId,
                         ServiceContext context,
                         ServiceCallback<String> callback) throws IOException {
    if (!checkPermission(noteId, Permission.OWNER, Message.OP.DEL_NOTE, context, callback)) {
      return;
    }
    if (notebook.getNote(noteId) != null) {
      notebook.removeNote(noteId, context.getAutheInfo());
      callback.onSuccess("Delete note successfully", context);
    } else {
      callback.onFailure(new NoteNotFoundException(noteId), context);
    }
  }

  public List<Map<String, String>> listNotes(boolean needsReload,
                                             ServiceContext context,
                                             ServiceCallback<List<Map<String, String>>> callback)
      throws IOException {

    ZeppelinConfiguration conf = notebook.getConf();
    String homeScreenNoteId = conf.getString(ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList =
        conf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);
    if (needsReload) {
      try {
        notebook.reloadAllNotes(context.getAutheInfo());
      } catch (IOException e) {
        LOGGER.error("Fail to reload notes from repository", e);
      }
    }

    List<Note> notes = notebook.getAllNotes(context.getUserAndRoles());
    List<Map<String, String>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      Map<String, String> info = new HashMap<>();
      if (hideHomeScreenNotebookFromList && note.getId().equals(homeScreenNoteId)) {
        continue;
      }
      info.put("id", note.getId());
      info.put("name", note.getName());
      notesInfo.add(info);
    }

    callback.onSuccess(notesInfo, context);
    return notesInfo;
  }

  public void renameNote(String noteId,
                         String newNoteName,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.NOTE_RENAME, context, callback)) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note != null) {
      note.setName(newNoteName);
      note.setCronSupported(notebook.getConf());
      note.persist(context.getAutheInfo());
      callback.onSuccess(note, context);
    } else {
      callback.onFailure(new NoteNotFoundException(noteId), context);
    }

  }

  public Note cloneNote(String noteId,
                        String newNoteName,
                        ServiceContext context,
                        ServiceCallback<Note> callback) throws IOException {
    Note newNote = notebook.cloneNote(noteId, newNoteName, context.getAutheInfo());
    callback.onSuccess(newNote, context);
    return newNote;
  }

  public Note importNote(String noteName,
                         String noteJson,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    Note note = notebook.importNote(noteJson, noteName, context.getAutheInfo());
    note.persist(context.getAutheInfo());
    callback.onSuccess(note, context);
    return note;
  }

  public boolean runParagraph(String noteId,
                              String paragraphId,
                              String title,
                              String text,
                              Map<String, Object> params,
                              Map<String, Object> config,
                              boolean isRunAll,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {

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
    if (!p.isEnabled()) {
      if (!isRunAll) {
        callback.onFailure(new IOException("paragraph is disabled."), context);
      }
      return true;
    }
    p.setText(text);
    p.setTitle(title);
    p.setAuthenticationInfo(context.getAutheInfo());
    p.settings.setParams(params);
    p.setConfig(config);

    if (note.isPersonalizedMode()) {
      p = note.getParagraph(paragraphId);
      p.setText(text);
      p.setTitle(title);
      p.setAuthenticationInfo(context.getAutheInfo());
      p.settings.setParams(params);
      p.setConfig(config);
    }

    try {
      note.persist(p.getAuthenticationInfo());
      boolean result = note.run(p.getId(), true);
      callback.onSuccess(p, context);
      return result;
    } catch (Exception ex) {
      LOGGER.error("Exception from run", ex);
      p.setReturn(new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage()), ex);
      p.setStatus(Job.Status.ERROR);
      callback.onFailure(new Exception("Fail to run paragraph " + paragraphId, ex), context);
      return false;
    }
  }

  public void runAllParagraphs(String noteId,
                               List<Map<String, Object>> paragraphs,
                               ServiceContext context,
                               ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.RUNNER, Message.OP.RUN_ALL_PARAGRAPHS, context,
        callback)) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      callback.onFailure(new NoteNotFoundException(noteId), context);
      return;
    }

    for (Map<String, Object> raw : paragraphs) {
      String paragraphId = (String) raw.get("id");
      if (paragraphId == null) {
        continue;
      }
      String text = (String) raw.get("paragraph");
      String title = (String) raw.get("title");
      Map<String, Object> params = (Map<String, Object>) raw.get("params");
      Map<String, Object> config = (Map<String, Object>) raw.get("config");

      if (!runParagraph(noteId, paragraphId, title, text,
              params, config, true, context, callback)) {
        // stop execution when one paragraph fails.
        break;
      }
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
    note.persist(context.getAutheInfo());
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
    note.persist(context.getAutheInfo());
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
    newPara.setConfig(config);
    note.persist(context.getAutheInfo());
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
    //restore cron
    Map<String, Object> config = note.getConfig();
    if (config.get("cron") != null) {
      notebook.refreshCron(note.getId());
    }

    if (note.isTrash()) {
      String newName = note.getName().replaceFirst(Folder.TRASH_FOLDER_ID + "/", "");
      renameNote(noteId, newName, context, callback);
    } else {
      callback.onFailure(new IOException(String.format("Trying to restore a note {} " +
          "which is not in Trash", noteId)), context);
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
    p.setConfig(config);
    p.setTitle(title);
    p.setText(text);
    if (note.isPersonalizedMode()) {
      p = p.getUserParagraph(context.getAutheInfo().getUser());
      p.settings.setParams(params);
      p.setConfig(config);
      p.setTitle(title);
      p.setText(text);
    }
    note.persist(context.getAutheInfo());
    callback.onSuccess(p, context);
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
    Paragraph returnedParagraph = null;
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
      notebook.refreshCron(note.getId());
    }

    note.persist(context.getAutheInfo());
    callback.onSuccess(note, context);
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
    note.persist(context.getAutheInfo());
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
    note.persist(context.getAutheInfo());
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
        notebook.checkpointNote(noteId, commitMessage, context.getAutheInfo());
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
        notebook.listRevisionHistory(noteId, context.getAutheInfo());
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
      Note resultNote = notebook.setNoteRevision(noteId, revisionId, context.getAutheInfo());
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
    Note revisionNote = notebook.getNoteByRevision(noteId, revisionId, context.getAutheInfo());
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
    Note revisionNote = null;
    if (revisionId.equals("Head")) {
      revisionNote = notebook.getNote(noteId);
    } else {
      revisionNote = notebook.getNoteByRevision(noteId, revisionId, context.getAutheInfo());
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
      List<InterpreterCompletion> completions = note.completion(paragraphId, buffer, cursor);
      callback.onSuccess(completions, context);
      return completions;
    } catch (RuntimeException e) {
      callback.onFailure(new IOException("Fail to get completion", e), context);
      return null;
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
        isAllowed = notebookAuthorization.isReader(noteId, context.getUserAndRoles());
        allowed = notebookAuthorization.getReaders(noteId);
        break;
      case WRITER:
        isAllowed = notebookAuthorization.isWriter(noteId, context.getUserAndRoles());
        allowed = notebookAuthorization.getWriters(noteId);
        break;
      case RUNNER:
        isAllowed = notebookAuthorization.isRunner(noteId, context.getUserAndRoles());
        allowed = notebookAuthorization.getRunners(noteId);
        break;
      case OWNER:
        isAllowed = notebookAuthorization.isOwner(noteId, context.getUserAndRoles());
        allowed = notebookAuthorization.getOwners(noteId);
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
