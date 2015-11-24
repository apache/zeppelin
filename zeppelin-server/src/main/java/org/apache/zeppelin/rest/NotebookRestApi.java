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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.rest.message.InterpreterSettingListForNoteBind;
import org.apache.zeppelin.rest.message.NewInterpreterSettingRequest;
import org.apache.zeppelin.rest.message.NewNotebookRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.NotebookServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Rest api endpoint for the noteBook.
 */
@Path("/notebook")
@Produces("application/json")
public class NotebookRestApi {
  Logger logger = LoggerFactory.getLogger(NotebookRestApi.class);
  Gson gson = new Gson();
  private Notebook notebook;
  private NotebookServer notebookServer;

  public NotebookRestApi() {}

  public NotebookRestApi(Notebook notebook, NotebookServer notebookServer) {

    this.notebook = notebook;
    this.notebookServer = notebookServer;
  }

  /**
   * bind a setting to note
   * @throws IOException
   */
  @PUT
  @Path("interpreter/bind/{noteId}")
  public Response bind(@PathParam("noteId") String noteId, String req) throws IOException {
    List<String> settingIdList = gson.fromJson(req, new TypeToken<List<String>>(){}.getType());
    notebook.bindInterpretersToNote(noteId, settingIdList);
    return new JsonResponse(Status.OK).build();
  }

  /**
   * list binded setting
   */
  @GET
  @Path("interpreter/bind/{noteId}")
  public Response bind(@PathParam("noteId") String noteId) {
    List<InterpreterSettingListForNoteBind> settingList
      = new LinkedList<InterpreterSettingListForNoteBind>();

    List<InterpreterSetting> selectedSettings = notebook.getBindedInterpreterSettings(noteId);
    for (InterpreterSetting setting : selectedSettings) {
      settingList.add(new InterpreterSettingListForNoteBind(
          setting.id(),
          setting.getName(),
          setting.getGroup(),
          setting.getInterpreterGroup(),
          true)
      );
    }

    List<InterpreterSetting> availableSettings = notebook.getInterpreterFactory().get();
    for (InterpreterSetting setting : availableSettings) {
      boolean selected = false;
      for (InterpreterSetting selectedSetting : selectedSettings) {
        if (selectedSetting.id().equals(setting.id())) {
          selected = true;
          break;
        }
      }

      if (!selected) {
        settingList.add(new InterpreterSettingListForNoteBind(
            setting.id(),
            setting.getName(),
            setting.getGroup(),
            setting.getInterpreterGroup(),
            false)
        );
      }
    }
    return new JsonResponse(Status.OK, "", settingList).build();
  }

  @GET
  @Path("/")
  public Response getNotebookList() throws IOException {
    List<Map<String, String>> notesInfo = notebookServer.generateNotebooksInfo();
    return new JsonResponse(Status.OK, "", notesInfo ).build();
  }

  /**
   * Create new note REST API
   * @param message - JSON with new note name
   * @return JSON with new note ID
   * @throws IOException
   */
  @POST
  @Path("/")
  public Response createNote(String message) throws IOException {
    logger.info("Create new notebook by JSON {}" , message);
    NewNotebookRequest request = gson.fromJson(message,
        NewNotebookRequest.class);
    Note note = notebook.createNote();
    note.addParagraph(); // it's an empty note. so add one paragraph
    String noteName = request.getName();
    if (noteName.isEmpty()) {
      noteName = "Note " + note.getId();
    }
    note.setName(noteName);
    note.persist();
    notebookServer.broadcastNote(note);
    notebookServer.broadcastNoteList();
    return new JsonResponse(Status.CREATED, "", note.getId() ).build();
  }

  /**
   * Delete note REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException
   */
  @DELETE
  @Path("{notebookId}")
  public Response deleteNote(@PathParam("notebookId") String notebookId) throws IOException {
    logger.info("Delete notebook {} ", notebookId);
    if (!(notebookId.isEmpty())) {
      Note note = notebook.getNote(notebookId);
      if (note != null) {
        notebook.removeNote(notebookId);
      }
    }
    notebookServer.broadcastNoteList();
    return new JsonResponse(Status.OK, "").build();
  }
  
  /**
   * Clone note REST API
   * @param
   * @return JSON with status.CREATED
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  @POST
  @Path("{notebookId}")
  public Response cloneNote(@PathParam("notebookId") String notebookId, String message) throws
      IOException, CloneNotSupportedException, IllegalArgumentException {
    logger.info("clone notebook by JSON {}" , message);
    NewNotebookRequest request = gson.fromJson(message,
        NewNotebookRequest.class);
    String newNoteName = request.getName();
    Note newNote = notebook.cloneNote(notebookId, newNoteName);
    notebookServer.broadcastNote(newNote);
    notebookServer.broadcastNoteList();
    return new JsonResponse(Status.CREATED, "", newNote.getId()).build();
  }
  
  /**
   * Run notebook jobs REST API
   * @param
   * @return JSON with status.ACCEPTED
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("job/{notebookId}")
  public Response runNoteJobs(@PathParam("notebookId") String notebookId) throws
      IOException, IllegalArgumentException {
    logger.info("run notebook jobs {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }
    
    note.runAll();
    return new JsonResponse(Status.ACCEPTED).build();
  }

  /**
   * Stop(delete) notebook jobs REST API
   * @param
   * @return JSON with status.ACCEPTED
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("job/{notebookId}")
  public Response stopNoteJobs(@PathParam("notebookId") String notebookId) throws
      IOException, IllegalArgumentException {
    logger.info("stop notebook jobs {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    for (Paragraph p : note.getParagraphs()) {
      if (!p.isTerminated()) {
        p.abort();
      }
    }
    return new JsonResponse(Status.ACCEPTED).build();
  }
  
  /**
   * Get notebook job status REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  @Path("job/{notebookId}")
  public Response getNoteJobStatus(@PathParam("notebookId") String notebookId) throws
      IOException, IllegalArgumentException {
    logger.info("get notebook job status.");
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    return new JsonResponse(Status.OK, null, note.generateParagraphsInfo()).build();
  }
  
  /**
   * Run paragraph job REST API
   * @param
   * @return JSON with status.ACCEPTED
   * @throws IOException, IllegalArgumentException
   */
  @POST
  @Path("job/{notebookId}/{paragraphId}")
  public Response runParagraph(@PathParam("notebookId") String notebookId, 
                               @PathParam("paragraphId") String paragraphId) throws
                               IOException, IllegalArgumentException {
    logger.info("run paragraph job {} {} ", notebookId, paragraphId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }
    
    if (note.getParagraph(paragraphId) == null) {
      return new JsonResponse(Status.NOT_FOUND, "paragraph not found.").build();
    }

    note.run(paragraphId);
    return new JsonResponse(Status.ACCEPTED).build();
  }

  /**
   * Stop(delete) paragraph job REST API
   * @param
   * @return JSON with status.ACCEPTED
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("job/{notebookId}/{paragraphId}")
  public Response stopParagraph(@PathParam("notebookId") String notebookId, 
                                @PathParam("paragraphId") String paragraphId) throws
                                IOException, IllegalArgumentException {
    logger.info("stop paragraph job {} ", notebookId);
    Note note = notebook.getNote(notebookId);
    if (note == null) {
      return new JsonResponse(Status.NOT_FOUND, "note not found.").build();
    }

    Paragraph p = note.getParagraph(paragraphId);
    if (p == null) {
      return new JsonResponse(Status.NOT_FOUND, "paragraph not found.").build();
    }
    p.abort();
    return new JsonResponse(Status.ACCEPTED).build();
  }
}
