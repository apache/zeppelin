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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.zeppelin.notebook.Notebook.NoteProcessor;
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

  public String getHomeNote(ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    String noteId = notebook.getConf().getString(ZEPPELIN_NOTEBOOK_HOMESCREEN);
    if (StringUtils.isBlank(noteId)) {
      callback.onSuccess(null, context);
    } else {
      notebook.processNote(noteId,
        note -> {
          if (note != null && !checkPermission(noteId, Permission.READER, Message.OP.GET_HOME_NOTE, context,
            callback)) {
            return null;
          }
          callback.onSuccess(note, context);
          return null;
        });
    }
    return noteId;
  }

  public  <T> T getNote(String noteId,
                      ServiceContext context,
                      ServiceCallback<Note> callback,
                      NoteProcessor<T> noteProcessor) throws IOException {
    return getNote(noteId, false, context, callback, noteProcessor);
  }

  public <T> T getNote(String noteId,
                      boolean reload,
                      ServiceContext context,
                      ServiceCallback<Note> callback,
                      NoteProcessor<T> noteProcessor) throws IOException {
    return notebook.processNote(noteId, reload,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }
        if (!checkPermission(noteId, Permission.READER, Message.OP.GET_NOTE, context,
          callback)) {
          return null;
        }
        Note newNote = note;
        if (note.isPersonalizedMode()) {
          newNote = note.getUserNote(context.getAutheInfo().getUser());
        }
        callback.onSuccess(newNote, context);
        if (noteProcessor == null) {
          return null;
        }
        return noteProcessor.process(newNote);
      });
  }

  /**
   * @param notePath
   * @param reload
   * @param context
   * @param callback
   * @return
   * @throws IOException
   */
  public <T> T getNoteByPath(String notePath,
                             boolean reload,
                             ServiceContext context,
                             ServiceCallback<Note> callback,
                             NoteProcessor<T> noteProcessor) throws IOException {
    String noteId = null;
    try {
      noteId = notebook.getNoteIdByPath(notePath);
    } catch (IOException e) {
      throw new NoteNotFoundException(notePath);
    }
    return getNote(noteId, reload, context, callback, noteProcessor);
  }




  /**
   *
   * @param notePath
   * @param defaultInterpreterGroup
   * @param addingEmptyParagraph
   * @param context
   * @param callback
   * @return noteId
   * @throws IOException
   */
  public String createNote(String notePath,
                         String defaultInterpreterGroup,
                         boolean addingEmptyParagraph,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {

    if (defaultInterpreterGroup == null) {
      defaultInterpreterGroup = zConf.getString(
          ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    }

    try {
      String noteId = notebook.createNote(normalizeNotePath(notePath), defaultInterpreterGroup,
          context.getAutheInfo(), false);
      // it's an empty note. so add one paragraph
      notebook.processNote(noteId,
        note -> {
          if (addingEmptyParagraph) {
            note.addNewParagraph(context.getAutheInfo());
          }
          notebook.saveNote(note, context.getAutheInfo());
          callback.onSuccess(note, context);
          return null;
        });

      return noteId;
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

    notePath = URLDecoder.decode(notePath, StandardCharsets.UTF_8.toString());
    if (notePath.endsWith("/")) {
      throw new IOException("Note name shouldn't end with '/'");
    }

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
    if (notebook.containsNoteById(noteId)) {
      try {
        notebook.removeNote(noteId, context.getAutheInfo());
        callback.onSuccess("Delete note successfully", context);
      } catch (CorruptedNoteException e) {
        notebook.removeCorruptedNote(noteId, context.getAutheInfo());
        callback.onSuccess("Delete note successfully", context);
      }
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
    notebook.processNote(noteId,
      readNote -> {
        if (readNote == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
        }
        readNote.setCronSupported(notebook.getConf());
        String newNotePathReal = newNotePath;
        if (isRelative && !readNote.getParentPath().equals("/")) {
          newNotePathReal = readNote.getParentPath() + "/" + newNotePath;
        } else {
          if (!newNotePath.startsWith("/")) {
            newNotePathReal = "/" + newNotePath;
          }
        }
        try {
          notebook.moveNote(noteId, newNotePathReal, context.getAutheInfo());
          callback.onSuccess(readNote, context);
        } catch (NotePathAlreadyExistsException e) {
          callback.onFailure(e, context);
        }
        return null;
      });

  }

  public String cloneNote(String noteId,
                          String newNotePath,
                          ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    return cloneNote(noteId, "", newNotePath, context, callback);
  }


  public String cloneNote(String noteId,
                          String revisionId,
                          String newNotePath,
                          ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    //TODO(zjffdu) move these to Notebook
    if (StringUtils.isBlank(newNotePath)) {
      newNotePath = "/Cloned Note_" + noteId;
      if(StringUtils.isNotEmpty(revisionId)) {
        // If cloning a revision of the note,
        // append the short commit id of revision to newNoteName
        // to distinguish which commit to be copied.
        newNotePath += "_" + revisionId.substring(0, 7);
      }
    }
    try {
      String newNoteId = notebook.cloneNote(noteId, revisionId, normalizeNotePath(newNotePath),
          context.getAutheInfo());
      return notebook.processNote(newNoteId,
        newNote -> {
          callback.onSuccess(newNote, context);
          return newNote.getId();
        });
    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to clone note", e), context);
      return null;
    }
  }

  /**
   *
   * @param notePath
   * @param noteJson
   * @param context
   * @param callback
   * @return NoteId of the imported Note
   * @throws IOException
   */
  public String importNote(String notePath,
                         String noteJson,
                         ServiceContext context,
                         ServiceCallback<Note> callback) throws IOException {
    try {
      // pass notePath when it is null
      String noteId = notebook.importNote(noteJson, notePath == null ?
              notePath : normalizeNotePath(notePath),
          context.getAutheInfo());
      return notebook.processNote(noteId,
        note -> {
          callback.onSuccess(note, context);
          return note.getId();
        });

    } catch (IOException e) {
      callback.onFailure(new IOException("Fail to import note: " + e.getMessage(), e), context);
      return null;
    }
  }

  /**
   * Executes given paragraph with passed paragraph info like noteId, paragraphId, title, text and etc.
   *
   * @param note
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
  public boolean runParagraph(Note note,
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


    if (note == null) {
      return false;
    }
    LOGGER.info("Start to run paragraph: {} of note: {}", paragraphId, note.getId());
    if (!checkPermission(note.getId(), Permission.RUNNER, Message.OP.RUN_PARAGRAPH, context, callback)) {
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

    return notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return false;
        }

        if (paragraphs != null) {
          // run note via the data passed from frontend
          try {
            note.setRunning(true);
            for (Map<String, Object> raw : paragraphs) {
              String paragraphId = (String) raw.get("id");
              if (paragraphId == null) {
                LOGGER.warn("No id found in paragraph json: {}", raw);
                continue;
              }
              try {
                String text = (String) raw.get("paragraph");
                String title = (String) raw.get("title");
                Map<String, Object> params = (Map<String, Object>) raw.get("params");
                Map<String, Object> config = (Map<String, Object>) raw.get("config");

                if (!runParagraph(note, paragraphId, title, text, params, config, null, false, true,
                        context, callback)) {
                  // stop execution when one paragraph fails.
                  return false;
                }
                // also stop execution when user code in a paragraph fails
                Paragraph p = note.getParagraph(paragraphId);
                InterpreterResult result = p.getReturn();
                if (result != null && result.code() == ERROR) {
                  return false;
                }
                if (p.getStatus() == ABORT || p.isAborted()) {
                  return false;
                }
              } catch (Exception e) {
                throw new IOException("Fail to run paragraph json: " + raw, e);
              }
            }
          } finally {
            note.setRunning(false);
          }
        } else {
          try {
            // run note directly when parameter `paragraphs` is null.
            note.runAll(context.getAutheInfo(), true, false, new HashMap<>());
            return true;
          } catch (Exception e) {
            LOGGER.warn("Fail to run note: {}", note.getName(), e);
            return false;
          }
        }

        return true;
      });

  }

  public void cancelParagraph(String noteId,
                              String paragraphId,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.RUNNER, Message.OP.CANCEL_PARAGRAPH, context,
        callback)) {
      return;
    }

    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          throw new NoteNotFoundException(noteId);
        }
        Paragraph p = note.getParagraph(paragraphId);
        if (p == null) {
          throw new ParagraphNotFoundException(paragraphId);
        }
        p.abort();
        callback.onSuccess(p, context);
        return null;
      });

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
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          throw new NoteNotFoundException(noteId);
        }
        if (note.getParagraph(paragraphId) == null) {
          throw new ParagraphNotFoundException(paragraphId);
        }
        if (newIndex >= note.getParagraphCount()) {
          callback.onFailure(new BadRequestException("newIndex " + newIndex + " is out of bounds"),
              context);
          return null;
        }
        note.moveParagraph(paragraphId, newIndex);
        notebook.saveNote(note, context.getAutheInfo());
        callback.onSuccess(note.getParagraph(newIndex), context);
        return null;
      });
  }

  public void removeParagraph(String noteId,
                              String paragraphId,
                              ServiceContext context,
                              ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_REMOVE, context,
        callback)) {
      return;
    }

    notebook.processNote(noteId,
        note -> {
          if (note == null) {
            throw new NoteNotFoundException(noteId);
          }
          if (note.getParagraph(paragraphId) == null) {
            throw new ParagraphNotFoundException(paragraphId);
          }
          Paragraph p = note.removeParagraph(context.getAutheInfo().getUser(), paragraphId);
          notebook.saveNote(note, context.getAutheInfo());
          callback.onSuccess(p, context);
          return null;
        });

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

    return notebook.processNote(noteId,
        note -> {
          if (note == null) {
            throw new NoteNotFoundException(noteId);
          }
          Paragraph newPara = note.insertNewParagraph(index, context.getAutheInfo());
          newPara.mergeConfig(config);
          notebook.saveNote(note, context.getAutheInfo());
          callback.onSuccess(newPara, context);
          return newPara;
        });
  }

  public void restoreNote(String noteId,
                          ServiceContext context,
                          ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.RESTORE_NOTE, context,
        callback)) {
      return;
    }
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        if (!note.getPath().startsWith("/" + NoteManager.TRASH_FOLDER)) {
          callback.onFailure(new IOException("Can not restore this note " + note.getPath() +
              " as it is not in trash folder"), context);
          return null;
        }
        try {
          String destNotePath = note.getPath().replace("/" + NoteManager.TRASH_FOLDER, "");
          notebook.moveNote(noteId, destNotePath, context.getAutheInfo());
          callback.onSuccess(note, context);
        } catch (IOException e) {
          callback.onFailure(new IOException("Fail to restore note: " + noteId, e), context);
        }
        return null;
      });


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
                         ServiceCallback<Void> callback) throws IOException {

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

    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }
        Paragraph p = note.getParagraph(paragraphId);
        if (p == null) {
          callback.onFailure(new ParagraphNotFoundException(paragraphId), context);
          return null;
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
        return null;
      });
  }

  /**
   *
   * @param noteId
   * @param maxParagraph
   * @param context
   * @param callback
   * @return paragraphId
   * @throws IOException
   */
  public String getNextSessionParagraphId(String noteId,
                                        int maxParagraph,
                                        ServiceContext context,
                                        ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_OUTPUT, context,
            callback)) {
      throw new IOException("No privilege to access this note");
    }
    return notebook.processNote(noteId,
      note -> {
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
          return note.addNewParagraph(context.getAutheInfo()).getId();
        }
      });
  }

  public void clearParagraphOutput(String noteId,
                                   String paragraphId,
                                   ServiceContext context,
                                   ServiceCallback<Paragraph> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_OUTPUT, context,
        callback)) {
      return;
    }
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }
        Paragraph p = note.getParagraph(paragraphId);
        if (p == null) {
          callback.onFailure(new ParagraphNotFoundException(paragraphId), context);
          return null;
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
        return null;
      });
  }

  public void clearAllParagraphOutput(String noteId,
                                      ServiceContext context,
                                      ServiceCallback<Note> callback) throws IOException {
    if (!checkPermission(noteId, Permission.WRITER, Message.OP.PARAGRAPH_CLEAR_ALL_OUTPUT, context,
        callback)) {
      return;
    }


    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }
        note.clearAllParagraphOutput();
        notebook.saveNote(note, context.getAutheInfo());
        callback.onSuccess(note, context);
        return null;
      });
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
    // use write lock because config and name are overwritten
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
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
        return null;
      });
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
    // use write lock because noteParams are overwritten
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        note.setNoteParams(noteParams);
        notebook.saveNote(note, context.getAutheInfo());
        callback.onSuccess(note, context);
        return null;
      });
  }

  public void removeNoteForms(String noteId,
                              String formName,
                              ServiceContext context,
                              ServiceCallback<Note> callback) throws IOException {
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        if (!checkPermission(noteId, Permission.WRITER, Message.OP.REMOVE_NOTE_FORMS, context,
            callback)) {
          return null;
        }

        note.getNoteForms().remove(formName);
        note.getNoteParams().remove(formName);
        notebook.saveNote(note, context.getAutheInfo());
        callback.onSuccess(note, context);
        return null;
      });
  }

  public NotebookRepoWithVersionControl.Revision checkpointNote(
      String noteId,
      String commitMessage,
      ServiceContext context,
      ServiceCallback<NotebookRepoWithVersionControl.Revision> callback) throws IOException {

    NotebookRepoWithVersionControl.Revision revision = notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }
        if (!checkPermission(noteId, Permission.WRITER, Message.OP.REMOVE_NOTE_FORMS, context,
          callback)) {
          return null;
        }
        return notebook.checkpointNote(noteId, note.getPath(), commitMessage, context.getAutheInfo());
      });

    callback.onSuccess(revision, context);
    return revision;
  }

  public List<NotebookRepoWithVersionControl.Revision> listRevisionHistory(
      String noteId,
      ServiceContext context,
      ServiceCallback<List<NotebookRepoWithVersionControl.Revision>> callback) throws IOException {


    List<NotebookRepoWithVersionControl.Revision> revisions = notebook.processNote(noteId,
      note -> {
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
        return notebook.listRevisionHistory(noteId, note.getPath(), context.getAutheInfo());
      });

    callback.onSuccess(revisions, context);
    return revisions;
  }


  public void setNoteRevision(String noteId,
                              String revisionId,
                              ServiceContext context,
                              ServiceCallback<Note> callback) throws IOException {

    notebook.processNote(noteId,
      note -> {
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
        } catch (Exception e) {
          callback.onFailure(new IOException("Fail to set given note revision", e), context);
        }
        return null;
      });

  }

  // notebook.getNoteByRevision(...) does not use the NoteCache,
  // so we can return a Note object here.
  public Note getNotebyRevision(String noteId,
                                String revisionId,
                                ServiceContext context,
                                ServiceCallback<Note> callback) throws IOException {

    return notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        if (!checkPermission(noteId, Permission.READER, Message.OP.NOTE_REVISION, context,
            callback)) {
          return null;
        }
        Note revisionNote = notebook.getNoteByRevision(noteId, note.getPath(), revisionId,
            context.getAutheInfo());
        callback.onSuccess(revisionNote, context);
        return revisionNote;
      });
  }

  public void getNoteByRevisionForCompare(String noteId,
                                          String revisionId,
                                          ServiceContext context,
                                          ServiceCallback<Note> callback) throws IOException {

    notebook.processNote(noteId ,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        if (!checkPermission(noteId, Permission.READER, Message.OP.NOTE_REVISION_FOR_COMPARE, context,
            callback)) {
          return null;
        }
        Note revisionNote;
        if (revisionId.equals("Head")) {
          revisionNote = note;
        } else {
          revisionNote = notebook.getNoteByRevision(noteId, note.getPath(), revisionId,
              context.getAutheInfo());
        }
        callback.onSuccess(revisionNote, context);
        return null;
      });
  }

  public List<InterpreterCompletion> completion(
      String noteId,
      String paragraphId,
      String buffer,
      int cursor,
      ServiceContext context,
      ServiceCallback<List<InterpreterCompletion>> callback) throws IOException {

    return notebook.processNote(noteId,
      note -> {
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
    });

  }

  public void getEditorSetting(String noteId,
                               String paragraphText,
                               ServiceContext context,
                               ServiceCallback<Map<String, Object>> callback) throws IOException {
    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
        }
        try {
          Map<String, Object> settings = notebook.getInterpreterSettingManager().
              getEditorSetting(paragraphText, noteId);
          callback.onSuccess(settings, context);
        } catch (Exception e) {
          callback.onFailure(new IOException("Fail to getEditorSetting", e), context);
        }
        return null;
      });
  }

  public void updatePersonalizedMode(String noteId,
                                     boolean isPersonalized,
                                     ServiceContext context,
                                     ServiceCallback<Note> callback) throws IOException {

    notebook.processNote(noteId,
      note -> {
        if (note == null) {
          callback.onFailure(new NoteNotFoundException(noteId), context);
          return null;
        }

        if (!checkPermission(noteId, Permission.WRITER, Message.OP.UPDATE_PERSONALIZED_MODE, context,
            callback)) {
          return null;
        }

        note.setPersonalizedMode(isPersonalized);
        notebook.saveNote(note, context.getAutheInfo());
        callback.onSuccess(note, context);
        return null;
      });
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

    final String finalDestNotePath = destNotePath;

    try {
      notebook.processNote(noteId,
        note -> {
          if (note == null) {
            callback.onFailure(new NoteNotFoundException(noteId), context);
            return null;
          }
          notebook.moveNote(noteId, finalDestNotePath, context.getAutheInfo());
          callback.onSuccess(note, context);
          return null;
        });
    } catch (CorruptedNoteException e) {
        LOGGER.info("Move corrupted note to trash");
        notebook.moveNote(noteId, destNotePath, context.getAutheInfo());
    }
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
      notebook.processNote(noteId,
        note -> {
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
          return null;
        });
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
    List<InterpreterSetting> settings = notebook.processNote(noteId,
      note -> {
        if (note == null) {
          return Collections.emptyList();
        } else {
          return note.getBindedInterpreterSettings(new ArrayList<>(context.getUserAndRoles()));
        }
      });
    for (InterpreterSetting setting : settings) {
      if (setting.getInterpreterGroup(user, noteId) == null) {
        continue;
      }
      if (interpreterGroupId.equals(setting.getInterpreterGroup(user, noteId)
          .getId())) {
        AngularObjectRegistry angularObjectRegistry =
            setting.getInterpreterGroup(user, noteId).getAngularObjectRegistry();

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


      notebook.processNote(noteId,
        note -> {
          if (note == null) {
            return null;
          }
          Paragraph p = note.getParagraph(paragraphId);
          if (p == null) {
            return null;
          }

          DiffMatchPatch dmp = new DiffMatchPatch();
          LinkedList<DiffMatchPatch.Patch> patches = null;
          try {
            patches = (LinkedList<DiffMatchPatch.Patch>) dmp.patchFromText(patchText);
          } catch (ClassCastException e) {
            LOGGER.error("Failed to parse patches", e);
          }
          if (patches == null) {
            return null;
          }

          String paragraphText = p.getText() == null ? "" : p.getText();
          paragraphText = (String) dmp.patchApply(patches, paragraphText)[0];
          p.setText(paragraphText);
          callback.onSuccess(patchText, context);
          return null;
      });
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
