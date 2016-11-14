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

import com.google.gson.Gson;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * Helium Rest Api
 */
@Path("/helium")
@Produces("application/json")
public class HeliumRestApi {
  Logger logger = LoggerFactory.getLogger(HeliumRestApi.class);

  private Helium helium;
  private HeliumApplicationFactory applicationFactory;
  private Notebook notebook;
  private Gson gson = new Gson();

  public HeliumRestApi() {
  }

  public HeliumRestApi(Helium helium,
                       HeliumApplicationFactory heliumApplicationFactory,
                       Notebook notebook) {
    this.helium  = helium;
    this.applicationFactory = heliumApplicationFactory;
    this.notebook = notebook;
  }

  /**
   * Get all packages
   * @return
   */
  @GET
  @Path("all")
  public Response getAll() {
    return new JsonResponse(Response.Status.OK, "", helium.getAllPackageInfo()).build();
  }

  @GET
  @Path("suggest/{noteId}/{paragraphId}")
  public Response suggest(@PathParam("noteId") String noteId,
                          @PathParam("paragraphId") String paragraphId) {
    Note note = notebook.getNote(noteId);
    if (note == null) {
      return new JsonResponse(Response.Status.NOT_FOUND, "Note " + noteId + " not found").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse(Response.Status.NOT_FOUND, "Paragraph " + paragraphId + " not found")
          .build();
    }

    return new JsonResponse(Response.Status.OK, "", helium.suggestApp(paragraph)).build();
  }

  @POST
  @Path("load/{noteId}/{paragraphId}")
  public Response suggest(@PathParam("noteId") String noteId,
                          @PathParam("paragraphId") String paragraphId,
                          String heliumPackage) {

    Note note = notebook.getNote(noteId);
    if (note == null) {
      return new JsonResponse(Response.Status.NOT_FOUND, "Note " + noteId + " not found").build();
    }

    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      return new JsonResponse(Response.Status.NOT_FOUND, "Paragraph " + paragraphId + " not found")
          .build();
    }
    HeliumPackage pkg = gson.fromJson(heliumPackage, HeliumPackage.class);

    String appId = applicationFactory.loadAndRun(pkg, paragraph);
    return new JsonResponse(Response.Status.OK, "", appId).build();
  }

}
