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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.helium.HeliumPackageSearchResult;
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
import java.util.Map;

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

  public HeliumRestApi(Helium helium, Notebook notebook) {
    this.helium  = helium;
    this.notebook = notebook;
  }

  /**
   * Get all packages info
   */
  @GET
  @Path("package")
  public Response getAllPackageInfo() {
    try {
      return new JsonResponse<>(Response.Status.OK, "", helium.getAllPackageInfo()).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  /**
   * Get all enabled packages info
   */
  @GET
  @Path("enabledPackage")
  public Response getAllEnabledPackageInfo() {
    try {
      return new JsonResponse<>(Response.Status.OK, "", helium.getAllEnabledPackages()).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  /**
   * Get single package info
   */
  @GET
  @Path("package/{packageName}")
  public Response getSinglePackageInfo(@PathParam("packageName") String packageName) {
    if (StringUtils.isEmpty(packageName)) {
      return new JsonResponse(
          Response.Status.BAD_REQUEST,
          "Can't get package info for empty name").build();
    }

    try {
      return new JsonResponse<>(
          Response.Status.OK, "", helium.getSinglePackageInfo(packageName)).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
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
    try {
      return new JsonResponse<>(Response.Status.OK, "", helium.suggestApp(paragraph)).build();
    }
    catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }

  }

  @POST
  @Path("load/{noteId}/{paragraphId}")
  public Response load(@PathParam("noteId") String noteId,
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
    HeliumPackage pkg = HeliumPackage.fromJson(heliumPackage);
    try {
      return new JsonResponse<>(Response.Status.OK, "",
              helium.getApplicationFactory().loadAndRun(pkg, paragraph)).build();
    }
    catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @GET
  @Path("bundle/load/{packageName}")
  @Produces("text/javascript")
  public Response bundleLoad(@QueryParam("refresh") String refresh,
                             @PathParam("packageName") String packageName) {
    if (StringUtils.isEmpty(packageName)) {
      return new JsonResponse(
          Response.Status.BAD_REQUEST,
          "Can't get bundle due to empty package name").build();
    }

    HeliumPackageSearchResult psr = null;
    List<HeliumPackageSearchResult> enabledPackages = helium.getAllEnabledPackages();
    for (HeliumPackageSearchResult e : enabledPackages) {
      if (e.getPkg().getName().equals(packageName)) {
        psr = e;
        break;
      }
    }

    if (psr == null) {
      // return empty to specify
      return Response.ok().build();
    }

    try {
      File bundle;
      boolean rebuild = (refresh != null && refresh.equals("true"));
      bundle = helium.getBundle(psr.getPkg(), rebuild);

      if (bundle == null) {
        return Response.ok().build();
      } else {
        String stringified = FileUtils.readFileToString(bundle);
        return Response.ok(stringified).build();
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
      if (helium.enable(packageName, artifact)) {
        return new JsonResponse(Response.Status.OK).build();
      }
      else {
        return new JsonResponse(Response.Status.NOT_FOUND).build();
      }
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @POST
  @Path("disable/{packageName}")
  public Response disablePackage(@PathParam("packageName") String packageName) {
    try {
      if (helium.disable(packageName)) {
        return new JsonResponse(Response.Status.OK).build();
      }
      else {
        return new JsonResponse(Response.Status.NOT_FOUND).build();
      }
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @GET
  @Path("spell/config/{packageName}")
  public Response getSpellConfigUsingMagic(@PathParam("packageName") String packageName) {
    if (StringUtils.isEmpty(packageName)) {
      return new JsonResponse(Response.Status.BAD_REQUEST,
          "packageName is empty" ).build();
    }

    try {
      Map<String, Map<String, Object>> config =
          helium.getSpellConfig(packageName);

      if (config == null) {
        return new JsonResponse(Response.Status.BAD_REQUEST,
            "Failed to find enabled package for " + packageName).build();
      }

      return new JsonResponse<>(Response.Status.OK, config).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @GET
  @Path("config")
  public Response getAllPackageConfigs() {
    try {
      Map<String, Map<String, Object>> config = helium.getAllPackageConfig();
      return new JsonResponse<>(Response.Status.OK, config).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @GET
  @Path("config/{packageName}/{artifact}")
  public Response getPackageConfig(@PathParam("packageName") String packageName,
                                   @PathParam("artifact") String artifact) {
    if (StringUtils.isEmpty(packageName) || StringUtils.isEmpty(artifact)) {
      return new JsonResponse(Response.Status.BAD_REQUEST,
          "package name or artifact is empty"
      ).build();
    }

    try {
      Map<String, Map<String, Object>> config =
          helium.getPackageConfig(packageName, artifact);

      if (config == null) {
        return new JsonResponse(Response.Status.BAD_REQUEST,
            "Failed to find package for " + artifact).build();
      }

      return new JsonResponse<>(Response.Status.OK, config).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @POST
  @Path("config/{packageName}/{artifact}")
  public Response updatePackageConfig(@PathParam("packageName") String packageName,
                                      @PathParam("artifact") String artifact,
                                      String rawConfig) {

    if (StringUtils.isEmpty(packageName) || StringUtils.isEmpty(artifact)) {
      return new JsonResponse(Response.Status.BAD_REQUEST,
          "package name or artifact is empty"
      ).build();
    }

    try {
      Map<String, Object> packageConfig = gson.fromJson(
          rawConfig, new TypeToken<Map<String, Object>>(){}.getType());
      helium.updatePackageConfig(artifact, packageConfig);
      return new JsonResponse<>(Response.Status.OK, packageConfig).build();
    } catch (JsonParseException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.BAD_REQUEST,
          e.getMessage()).build();
    } catch (IOException | RuntimeException e) {
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR,
          e.getMessage()).build();
    }
  }

  @GET
  @Path("order/visualization")
  public Response getVisualizationPackageOrder() {
    try {
      List<String> order = helium.getVisualizationPackageOrder();
      return new JsonResponse<>(Response.Status.OK, order).build();
    } catch (RuntimeException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  @POST
  @Path("order/visualization")
  public Response setVisualizationPackageOrder(String orderedPackageNameList) {
    List<String> orderedList = gson.fromJson(
        orderedPackageNameList, new TypeToken<List<String>>(){}.getType());
    try {
      helium.setVisualizationPackageOrder(orderedList);
      return new JsonResponse(Response.Status.OK).build();
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return new JsonResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }
}
