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

package org.apache.zeppelin.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.utils.InterpreterBindingUtils;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.message.CronRequest;
import org.apache.zeppelin.types.InterpreterSettingsList;
import org.apache.zeppelin.rest.message.NewNotebookRequest;
import org.apache.zeppelin.rest.message.NewParagraphRequest;
import org.apache.zeppelin.rest.message.RunParagraphWithParametersRequest;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.SecurityUtils;

/**
 * Rest api endpoint for the noteBook.
 */
@Path("/notebook")
@Produces("application/json")
public class NotebookRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRestApi.class);
  Gson gson = new Gson();
  private Notebook notebook;
  private NotebookServer notebookServer;
  private SearchService notebookIndex;
  private NotebookAuthorization notebookAuthorization;

  public NotebookRestApi() {
  }

  public NotebookRestApi(Notebook notebook, NotebookServer notebookServer, SearchService search) {
    this.notebook = notebook;
    this.notebookServer = notebookServer;
    this.notebookIndex = search;
    this.notebookAuthorization = notebook.getNotebookAuthorization();
  }

  /**
   * get note authorization information
   */
  @GET
  @Path("{noteId}/permissions")
  @ZeppelinApi
  public Response getNotePermissions(@PathParam("noteId") String noteId) {
    HashMap<String, Set<String>> permissionsMap = new HashMap<>();
    permissionsMap.put("owners", notebookAuthorization.getOwners(noteId));
    permissionsMap.put("readers", notebookAuthorization.getReaders(noteId));
    permissionsMap.put("writers", notebookAuthorization.getWriters(noteId));
    return new JsonResponse<>(Status.OK, "", permissionsMap).build();
  }

  private String ownerPermissionError(Set<String> current, Set<String> allowed) throws IOException {
    LOG.info("Cannot change permissions. Connection owners {}. Allowed owners {}",
        current.toString(), allowed.toString());
    return "Insufficient privileges to change permissions.\n\n" +
        "Allowed owners: " + allowed.toString() + "\n\n" +
        "User belongs to: " + current.toString();
  }

  /**
   * set note authorization information
   */
  @PUT
  @Path("{noteId}/permissions")
  @ZeppelinApi
  public Response putNotePermissions(@PathParam("noteId") String noteId, String req)
      throws IOException {
    /**
     * TODO(jl): Fixed the type of HashSet
     * https://issues.apache.org/jira/browse/ZEPPELIN-1162
     */
    HashMap<String, HashSet<String>> permMap =
        gson.fromJson(req, new TypeToken<HashMap<String, HashSet<String>>>() {
        }.getType());
    Note note = notebook.getNote(noteId);
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    LOG.info("Set permissions {} {} {} {} {}", noteId, principal, permMap.get("owners"),
        permMap.get("readers"), permMap.get("writers"));

    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    if (!notebookAuthorization.isOwner(noteId, userAndRoles)) {
      return new JsonResponse<>(Status.FORBIDDEN,
          ownerPermissionError(userAndRoles, notebookAuthorization.getOwners(noteId))).build();
    }

    HashSet<String> readers = permMap.get("readers");
    HashSet<String> owners = permMap.get("owners");
    HashSet<String> writers = permMap.get("writers");
    // Set readers, if writers and owners is empty -> set to user requesting the change
    if (readers != null && !readers.isEmpty()) {
      if (writers.isEmpty()) {
        writers = Sets.newHashSet(SecurityUtils.getPrincipal());
      }
      if (owners.isEmpty()) {
        owners = Sets.newHashSet(SecurityUtils.getPrincipal());
      }
    }
    // Set writers, if owners is empty -> set to user requesting the change
    if (writers != null && !writers.isEmpty()) {
      if (owners.isEmpty()) {
        owners = Sets.newHashSet(SecurityUtils.getPrincipal());
      }
    }

    notebookAuthorization.setReaders(noteId, readers);
    notebookAuthorization.setWriters(noteId, writers);
    notebookAuthorization.setOwners(noteId, owners);
    LOG.debug("After set permissions {} {} {}", notebookAuthorization.getOwners(noteId),
        notebookAuthorization.getReaders(noteId), notebookAuthorization.getWriters(noteId));
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    note.persist(subject);
    notebookServer.broadcastNote(note);
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * bind a setting to note
   *
   * @throws IOException
   */
  @PUT
  @Path("interpreter/bind/{noteId}")
  @ZeppelinApi
  public Response bind(@PathParam("noteId") String noteId, String req) throws IOException {
    List<String> settingIdList = gson.fromJson(req, new TypeToken<List<String>>() {
    }.getType());
    notebook.bindInterpretersToNote(noteId, settingIdList);
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * list binded setting
   */
  @GET
  @Path("interpreter/bind/{noteId}")
  @ZeppelinApi
  public Response bind(@PathParam("noteId") String noteId) {
    List<InterpreterSettingsList> settingList =
        InterpreterBindingUtils.getInterpreterBindings(notebook, noteId);
    notebookServer.broadcastInterpreterBindings(noteId, settingList);
    return new JsonResponse<>(Status.OK, "", settingList).build();
  }

  @GET
  @Path("/")
  @ZeppelinApi
  public Response getNotebookList() throws IOException {
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    List<Map<String, String>> notesInfo = notebookServer
          .generateNotebooksInfo(false, subject, userAndRoles);
    return new JsonResponse<>(Status.OK, "", notesInfo).build();
  }

  @GET
  @Path("{notebookId}")
  @ZeppelinApi
  public Response getNotebook(@PathParam("notebookId") String notebookId) throws IOException {
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    return new JsonResponse<>(Status.OK, "", note).build();
  }

  /**
   * export note REST API
   *
   * @param noteId ID of Note
   * @return note JSON with status.OK
   * @throws IOException
   */
  @GET
  @Path("export/{id}")
  @ZeppelinApi
  public Response exportNoteBook(@PathParam("id") String noteId) throws IOException {
    String exportJson = notebook.exportNote(noteId);
    return new JsonResponse<>(Status.OK, "", exportJson).build();
  }

  /**
   * import new note REST API
   *
   * @param req - notebook Json
   * @return JSON with new note ID
   * @throws IOException
   */
  @POST
  @Path("import")
  @ZeppelinApi
  public Response importNotebook(String req) throws IOException {
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    Note newNote = notebook.importNote(req, null, subject);
    return new JsonResponse<>(Status.CREATED, "", newNote.getId()).build();
  }

  /**
   * Create new note REST API
   *
   * @param message - JSON with new note name
   * @return JSON with new note ID
   * @throws IOException
   */
  @POST
  @Path("/")
  @ZeppelinApi
  public Response createNote(String message) throws IOException {
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    LOG.info("Create new notebook by JSON {}", message);
    NewNotebookRequest request = gson.fromJson(message, NewNotebookRequest.class);
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    Note note = notebook.createNote(subject);
    List<NewParagraphRequest> initialParagraphs = request.getParagraphs();
    if (initialParagraphs != null) {
      for (NewParagraphRequest paragraphRequest : initialParagraphs) {
        Paragraph p = note.addParagraph();
        p.setTitle(paragraphRequest.getTitle());
        p.setText(paragraphRequest.getText());
      }
    }
    note.addParagraph(); // add one paragraph to the last
    String noteName = request.getName();
    if (noteName.isEmpty()) {
      noteName = "Note " + note.getId();
    }

    note.setName(noteName);
    note.persist(subject);
    notebookServer.broadcastNote(note);
    notebookServer.broadcastNoteList(subject, userAndRoles);
    return new JsonResponse<>(Status.CREATED, "", note.getId()).build();
  }

  /**
   * Delete note REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException
   */
  @DELETE
  @Path("{notebookId}")
  @ZeppelinApi
  public Response deleteNote(@PathParam("notebookId") String notebookId) throws IOException {
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    LOG.info("Delete notebook {} ", notebookId);
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    if (!(notebookId.isEmpty())) {
      Note note = notebook.getNote(notebookId);
      if (note != null) {
        notebook.removeNote(notebookId, subject);
      }
    }

    notebookServer.broadcastNoteList(subject, userAndRoles);
    return new JsonResponse<>(Status.OK, "").build();
  }

  /**
   * Clone note REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.CREATED
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  @POST
  @Path("{notebookId}")
  @ZeppelinApi
  public Response cloneNote(@PathParam("notebookId") String notebookId, String message)
      throws IOException, CloneNotSupportedException, IllegalArgumentException {
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    LOG.info("clone notebook by JSON {}", message);
    NewNotebookRequest request = gson.fromJson(message, NewNotebookRequest.class);
    String newNoteName = null;
    if (request != null) {
      newNoteName = request.getName();
    }
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    Note newNote = notebook.cloneNote(notebookId, newNoteName, subject);
    notebookServer.broadcastNote(newNote);
    notebookServer.broadcastNoteList(subject, userAndRoles);
    return new JsonResponse<>(Status.CREATED, "", newNote.getId()).build();
  }

  /**
   * Insert paragraph REST API
   *
   * @param message - JSON containing paragraph's information
   * @return JSON with status.OK
   * @throws IOException
   */
  @POST
  @Path("{notebookId}/paragraph")
  @ZeppelinApi
  public Response insertParagraph(@PathParam("notebookId") String notebookId, String message)
      throws IOException {
    LOG.info("insert paragraph {} {}", notebookId, message);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    NewParagraphRequest request = gson.fromJson(message, NewParagraphRequest.class);

    Paragraph p;
    Double indexDouble = request.getIndex();
    if (indexDouble == null) {
      p = note.addParagraph();
    } else {
      p = note.insertParagraph(indexDouble.intValue());
    }
    p.setTitle(request.getTitle());
    p.setText(request.getText());

    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    note.persist(subject);
    notebookServer.broadcastNote(note);
    return new JsonResponse<>(Status.CREATED, "", p.getId()).build();
  }

  /**
   * Get paragraph REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with information of the paragraph
   * @throws IOException
   */
  @GET
  @Path("{notebookId}/paragraph/{paragraphId}")
  @ZeppelinApi
  public Response getParagraph(@PathParam("notebookId") String notebookId,
      @PathParam("paragraphId") String paragraphId) throws IOException {
    LOG.info("get paragraph {} {}", notebookId, paragraphId);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      return new JsonResponse(Status.NOT_FOUND, "paragraph not found.").build();
    }

    return new JsonResponse<>(Status.OK, "", p).build();
  }

  /**
   * Move paragraph REST API
   *
   * @param newIndex - new index to move
   * @return JSON with status.OK
   * @throws IOException
   */
  @POST
  @Path("{notebookId}/paragraph/{paragraphId}/move/{newIndex}")
  @ZeppelinApi
  public Response moveParagraph(@PathParam("notebookId") String notebookId,
      @PathParam("paragraphId") String paragraphId, @PathParam("newIndex") String newIndex)
      throws IOException {
    LOG.info("move paragraph {} {} {}", notebookId, paragraphId, newIndex);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      return new JsonResponse(Status.NOT_FOUND, "paragraph not found.").build();
    }

    try {
      note.moveParagraph(paragraphId, Integer.parseInt(newIndex), true);

      AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
      note.persist(subject);
      notebookServer.broadcastNote(note);
      return new JsonResponse(Status.OK, "").build();
    } catch (IndexOutOfBoundsException e) {
      LOG.error("Exception in NotebookRestApi while moveParagraph ", e);
      return new JsonResponse(Status.BAD_REQUEST, "paragraph's new index is out of bound").build();
    }
  }

  /**
   * Delete paragraph REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException
   */
  @DELETE
  @Path("{notebookId}/paragraph/{paragraphId}")
  @ZeppelinApi
  public Response deleteParagraph(@PathParam("notebookId") String notebookId,
      @PathParam("paragraphId") String paragraphId) throws IOException {
    LOG.info("delete paragraph {} {}", notebookId, paragraphId);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      return new JsonResponse(Status.NOT_FOUND, "paragraph not found.").build();
    }

    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    note.removeParagraph(paragraphId);
    note.persist(subject);
    notebookServer.broadcastNote(note);

    return new JsonResponse(Status.OK, "").build();
  }

  /**
   * Run notebook jobs REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("job/{notebookId}")
  @ZeppelinApi
  public Response runNoteJobs(@PathParam("notebookId") String notebookId)
      throws IOException, IllegalArgumentException {
    LOG.info("run notebook jobs {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    try {
      note.runAll();
    } catch (Exception ex) {
      LOG.error("Exception from run", ex);
      return new JsonResponse<>(Status.PRECONDITION_FAILED,
          ex.getMessage() + "- Not selected or Invalid Interpreter bind").build();
    }

    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Stop(delete) notebook jobs REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("job/{notebookId}")
  @ZeppelinApi
  public Response stopNoteJobs(@PathParam("notebookId") String notebookId)
      throws IOException, IllegalArgumentException {
    LOG.info("stop notebook jobs {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    for (Paragraph p : note.getParagraphs()) {
      if (!p.isTerminated()) {
        p.abort();
      }
    }
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Get notebook job status REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("job/{notebookId}")
  @ZeppelinApi
  public Response getNoteJobStatus(@PathParam("notebookId") String notebookId)
      throws IOException, IllegalArgumentException {
    LOG.info("get notebook job status.");
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    return new JsonResponse<>(Status.OK, null, note.generateParagraphsInfo()).build();
  }

  /**
   * Get notebook paragraph job status REST API
   *
   * @param notebookId ID of Notebook
   * @param paragraphId ID of Paragraph
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("job/{notebookId}/{paragraphId}")
  @ZeppelinApi
  public Response getNoteParagraphJobStatus(@PathParam("notebookId") String notebookId, 
      @PathParam("paragraphId") String paragraphId)
      throws IOException, IllegalArgumentException {
    LOG.info("get notebook paragraph job status.");
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "paragraph not found.").build();
    }

    return new JsonResponse<>(Status.OK, null, note.generateSingleParagraphInfo(paragraphId)).
      build();
  }

  /**
   * Run asynchronously paragraph job REST API
   *
   * @param message - JSON with params if user wants to update dynamic form's value
   *                null, empty string, empty json if user doesn't want to update
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("job/{notebookId}/{paragraphId}")
  @ZeppelinApi
  public Response runParagraph(@PathParam("notebookId") String notebookId,
      @PathParam("paragraphId") String paragraphId, String message)
      throws IOException, IllegalArgumentException {
    LOG.info("run paragraph job asynchronously {} {} {}", notebookId, paragraphId, message);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "paragraph not found.").build();
    }

    // handle params if presented
    handleParagraphParams(message, note, paragraph);

    note.run(paragraph.getId());
    return new JsonResponse<>(Status.OK).build();
  }

/**
   * Run synchronously a paragraph REST API
   *
   * @param noteId - noteId
   * @param paragraphId - paragraphId
   * @param message - JSON with params if user wants to update dynamic form's value
   *                null, empty string, empty json if user doesn't want to update
   *
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("run/{notebookId}/{paragraphId}")
  @ZeppelinApi
  public Response runParagraphSynchronously(@PathParam("notebookId") String noteId,
                                            @PathParam("paragraphId") String paragraphId,
                                            String message) throws
                                            IOException, IllegalArgumentException {
    LOG.info("run paragraph synchronously {} {} {}", noteId, paragraphId, message);

    Note note = notebook.getNote(noteId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "paragraph not found.").build();
    }

    // handle params if presented
    handleParagraphParams(message, note, paragraph);

    if (paragraph.getListener() == null) {
      note.initializeJobListenerForParagraph(paragraph);
    }

    paragraph.run();

    final InterpreterResult result = paragraph.getResult();

    if (result.code() == InterpreterResult.Code.SUCCESS) {
      return new JsonResponse<>(Status.OK, result).build();
    } else {
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, result).build();
    }
  }

  /**
   * Stop(delete) paragraph job REST API
   *
   * @param notebookId  ID of Notebook
   * @param paragraphId ID of Paragraph
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("job/{notebookId}/{paragraphId}")
  @ZeppelinApi
  public Response stopParagraph(@PathParam("notebookId") String notebookId,
      @PathParam("paragraphId") String paragraphId) throws IOException, IllegalArgumentException {
    /**
     * TODO(jl): Fixed notebookId to noteId
     * https://issues.apache.org/jira/browse/ZEPPELIN-1163
     */
    LOG.info("stop paragraph job {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "paragraph not found.").build();
    }
    p.abort();
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Register cron job REST API
   *
   * @param message - JSON with cron expressions.
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("cron/{notebookId}")
  @ZeppelinApi
  public Response registerCronJob(@PathParam("notebookId") String notebookId, String message)
      throws IOException, IllegalArgumentException {
    // TODO(jl): Fixed notebookId to noteId
    LOG.info("Register cron job note={} request cron msg={}", notebookId, message);

    CronRequest request = gson.fromJson(message, CronRequest.class);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    if (!CronExpression.isValidExpression(request.getCronString())) {
      return new JsonResponse<>(Status.BAD_REQUEST, "wrong cron expressions.").build();
    }

    Map<String, Object> config = note.getConfig();
    config.put("cron", request.getCronString());
    note.setConfig(config);
    notebook.refreshCron(note.getId());

    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Remove cron job REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("cron/{notebookId}")
  @ZeppelinApi
  public Response removeCronJob(@PathParam("notebookId") String notebookId)
      throws IOException, IllegalArgumentException {
    // TODO(jl): Fixed notebookId to noteId
    LOG.info("Remove cron job note {}", notebookId);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    Map<String, Object> config = note.getConfig();
    config.put("cron", null);
    note.setConfig(config);
    notebook.refreshCron(note.getId());

    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Get cron job REST API
   *
   * @param notebookId ID of Notebook
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("cron/{notebookId}")
  @ZeppelinApi
  public Response getCronJob(@PathParam("notebookId") String notebookId)
      throws IOException, IllegalArgumentException {
    // TODO(jl): Fixed notebookId to noteId
    LOG.info("Get cron job note {}", notebookId);

    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "note not found.").build();
    }

    return new JsonResponse<>(Status.OK, note.getConfig().get("cron")).build();
  }

  /**
   * Get notebook jobs for job manager
   *
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("jobmanager/")
  @ZeppelinApi
  public Response getJobListforNotebook() throws IOException, IllegalArgumentException {
    LOG.info("Get notebook jobs for job manager");

    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    List<Map<String, Object>> notebookJobs = notebook
      .getJobListByUnixTime(false, 0, subject);
    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs);

    return new JsonResponse<>(Status.OK, response).build();
  }

  /**
   * Get updated notebook jobs for job manager
   *
   * Return the `Note` change information within the post unix timestamp.
   *
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("jobmanager/{lastUpdateUnixtime}/")
  @ZeppelinApi
  public Response getUpdatedJobListforNotebook(
      @PathParam("lastUpdateUnixtime") long lastUpdateUnixTime)
      throws IOException, IllegalArgumentException {
    LOG.info("Get updated notebook jobs lastUpdateTime {}", lastUpdateUnixTime);

    List<Map<String, Object>> notebookJobs;
    AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
    notebookJobs = notebook.getJobListByUnixTime(false, lastUpdateUnixTime, subject);
    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs);

    return new JsonResponse<>(Status.OK, response).build();
  }

  /**
   * Search for a Notes with permissions
   */
  @GET
  @Path("search")
  @ZeppelinApi
  public Response search(@QueryParam("q") String queryTerm) {
    LOG.info("Searching notebooks for: {}", queryTerm);
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    List<Map<String, String>> notebooksFound = notebookIndex.query(queryTerm);
    for (int i = 0; i < notebooksFound.size(); i++) {
      String[] Id = notebooksFound.get(i).get("id").split("/", 2);
      String noteId = Id[0];
      if (!notebookAuthorization.isOwner(noteId, userAndRoles) &&
          !notebookAuthorization.isReader(noteId, userAndRoles) &&
          !notebookAuthorization.isWriter(noteId, userAndRoles)) {
        notebooksFound.remove(i);
        i--;
      }
    }
    LOG.info("{} notebooks found", notebooksFound.size());
    return new JsonResponse<>(Status.OK, notebooksFound).build();
  }


  private void handleParagraphParams(String message, Note note, Paragraph paragraph)
      throws IOException {
    // handle params if presented
    if (!StringUtils.isEmpty(message)) {
      RunParagraphWithParametersRequest request =
              gson.fromJson(message, RunParagraphWithParametersRequest.class);
      Map<String, Object> paramsForUpdating = request.getParams();
      if (paramsForUpdating != null) {
        paragraph.settings.getParams().putAll(paramsForUpdating);
        AuthenticationInfo subject = new AuthenticationInfo(SecurityUtils.getPrincipal());
        note.persist(subject);
      }
    }
  }

}
