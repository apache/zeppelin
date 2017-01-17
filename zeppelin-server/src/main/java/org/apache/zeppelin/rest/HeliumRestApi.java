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

import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Helium Rest Api
 */
@Path("/helium")
@Produces("application/json")
public class HeliumRestApi {
  Logger logger = LoggerFactory.getLogger(HeliumRestApi.class);

  private Helium helium;
  private Notebook notebook;
  private Gson gson = new Gson();

  public HeliumRestApi() {
  }

  public HeliumRestApi(Helium helium, Notebook notebook) {
    this.helium  = helium;
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

    String appId = helium.getApplicationFactory().loadAndRun(pkg, paragraph);
    return new JsonResponse(Response.Status.OK, "", appId).build();
  }

  @GET
  @Path("bundle/load")
  @Produces("text/javascript")
  public Response bundleLoad(@QueryParam("refresh") String refresh) {
    try {
      File bundle;
      if (refresh != null && refresh.equals("true")) {
        bundle = helium.recreateBundle();
      } else {
        bundle = helium.getBundleFactory().getCurrentCacheBundle();
      }

      if (bundle == null) {
        return Response.ok().build();
      } else {
        String stringifiedBundle = FileUtils.readFileToString(bundle);
        return Response.ok(stringifiedBundle).build();
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);

      // returning error will prevent zeppelin front-end render any notebook.
      // visualization load fail doesn't need to block notebook rendering work.
      // so it's better return ok instead of any error.
      return Response.ok("ERROR: " + e.getMessage()).build();
    }
  }

  @POST
  @Path("enable/{packageName}")
  public Response enablePackage(@PathParam("packageName") String packageName,
                                String artifact) {
    try {
      helium.enable(packageName, artifact);
      return new JsonResponse(Response.Status.OK).build();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @POST
  @Path("disable/{packageName}")
  public Response enablePackage(@PathParam("packageName") String packageName) {
    try {
      helium.disable(packageName);
      return new JsonResponse(Response.Status.OK).build();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @GET
  @Path("bundleOrder")
  public Response getBundlePackageOrder() {
    List<String> order = helium.getBundlePackageOrder();
    return new JsonResponse(Response.Status.OK, order).build();
  }

  @POST
  @Path("bundleOrder")
  public Response setBundlePackageOrder(String orderedPackageNameList) {
    List<String> orderedList = gson.fromJson(
        orderedPackageNameList, new TypeToken<List<String>>(){}.getType());

    try {
      helium.setVisualizationPackageOrder(orderedList);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
    return new JsonResponse(Response.Status.OK).build();
  }
}
