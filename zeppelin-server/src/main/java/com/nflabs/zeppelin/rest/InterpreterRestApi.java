package com.nflabs.zeppelin.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.InterpreterSetting;
import com.nflabs.zeppelin.rest.message.NewInterpreterSettingRequest;
import com.nflabs.zeppelin.server.JsonResponse;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * Interpreter Rest API
 *
 */
@Path("/interpreter")
@Produces("application/json")
@Api(value = "/interpreter", description = "Zeppelin Interpreter REST API")
public class InterpreterRestApi {
  Logger logger = LoggerFactory.getLogger(InterpreterRestApi.class);

  private InterpreterFactory interpreterFactory;

  Gson gson = new Gson();

  public InterpreterRestApi() {

  }

  public InterpreterRestApi(InterpreterFactory interpreterFactory) {
    this.interpreterFactory = interpreterFactory;
  }

  /**
   * List all interpreter settings
     * @return
   */
  @GET
  @Path("setting")
  @ApiOperation(httpMethod = "GET", value = "List all interpreter setting")
  @ApiResponses(value = {@ApiResponse(code = 500, message = "When something goes wrong")})
  public Response listSettings() {
    List<InterpreterSetting> interpreterSettings = null;
    interpreterSettings = interpreterFactory.get();
    return new JsonResponse(Status.OK, "", interpreterSettings).build();
  }

  /**
   * Add new interpreter setting
   * @param message
   * @return
   * @throws IOException
   * @throws InterpreterException
   */
  @POST
  @Path("setting")
  @ApiOperation(httpMethod = "GET", value = "Create new interpreter setting")
  @ApiResponses(value = {@ApiResponse(code = 201, message = "On success")})
  public Response newSettings(String message) throws InterpreterException, IOException {
    NewInterpreterSettingRequest request = gson.fromJson(message,
        NewInterpreterSettingRequest.class);
    Properties p = new Properties();
    p.putAll(request.getProperties());
    interpreterFactory.add(request.getName(), request.getGroup(), p);
    return new JsonResponse(Status.CREATED, "").build();
  }

  @PUT
  @Path("setting/{settingId}")
  public Response updateSetting(String message, @PathParam("settingId") String settingId) {
    logger.info("Update interpreterSetting {}", settingId);
    try {
      Properties p = gson.fromJson(message, Properties.class);
      interpreterFactory.setPropertyAndRestart(settingId, p);
    } catch (InterpreterException e) {
      return new JsonResponse(Status.NOT_FOUND, e.getMessage(), e).build();
    }
    InterpreterSetting setting = interpreterFactory.get(settingId);
    if (setting == null) {
      return new JsonResponse(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse(Status.OK, "", setting).build();
  }

  @DELETE
  @Path("setting/{settingId}")
  @ApiOperation(httpMethod = "GET", value = "Remove interpreter setting")
  @ApiResponses(value = {@ApiResponse(code = 500, message = "When something goes wrong")})
  public Response removeSetting(@PathParam("settingId") String settingId) throws IOException {
    logger.info("Remove interpreterSetting {}", settingId);
    interpreterFactory.remove(settingId);
    return new JsonResponse(Status.OK).build();
  }

  @PUT
  @Path("setting/restart/{settingId}")
  @ApiOperation(httpMethod = "GET", value = "restart interpreter setting")
  @ApiResponses(value = {
      @ApiResponse(code = 404, message = "Not found")})
  public Response restartSetting(@PathParam("settingId") String settingId) {
    logger.info("Restart interpreterSetting {}", settingId);
    try {
      interpreterFactory.restart(settingId);
    } catch (InterpreterException e) {
      return new JsonResponse(Status.NOT_FOUND, e.getMessage(), e).build();
    }
    InterpreterSetting setting = interpreterFactory.get(settingId);
    if (setting == null) {
      return new JsonResponse(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse(Status.OK, "", setting).build();
  }

  /**
   * List all available interpreters by group
   */
  @GET
  @ApiOperation(httpMethod = "GET", value = "List all available interpreters")
  @ApiResponses(value = {
      @ApiResponse(code = 500, message = "When something goes wrong")})
  public Response listInterpreter(String message) {
    Map<String, RegisteredInterpreter> m = Interpreter.registeredInterpreters;
    return new JsonResponse(Status.OK, "", m).build();
  }
}
