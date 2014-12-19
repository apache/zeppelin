package com.nflabs.zeppelin.rest;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.InterpreterSetting;
import com.nflabs.zeppelin.server.JsonResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interpreter Rest API
 *
 */
@Path("/interpreter")
@Produces("application/json")
public class InterpreterRestApi {
  Logger logger = LoggerFactory.getLogger(InterpreterRestApi.class);
  
  private InterpreterFactory interpreterFactory;

  public InterpreterRestApi() {
    
  }
  
  public InterpreterRestApi(InterpreterFactory interpreterFactory) {
    this.interpreterFactory = interpreterFactory;
  }

  /**
   * List all interpreter settings
   * @param message
   * @return
   */
  @GET
  @Path("setting")
  public Response listSettings(String message) {
    Map<String, InterpreterSetting> interpreterSettings = interpreterFactory.get();
    return new JsonResponse(Status.OK, "", interpreterSettings).build();
  }

  @GET
  @Path("setting/remove/{settingId}")
  public Response removeSetting(@PathParam("settingId") String settingId) throws IOException {
    logger.info("Remove interpreterSetting {}", settingId);
    interpreterFactory.remove(settingId);
    return new JsonResponse(Status.OK).build();
  }

  @GET
  @Path("setting/restart/{settingId}")
  public Response restartSetting(@PathParam("settingId") String settingId) {
    logger.info("Restart interpreterSetting {}", settingId);
    interpreterFactory.restart(settingId);
    InterpreterSetting setting = interpreterFactory.get(settingId);
    return new JsonResponse(Status.OK, "", setting).build();
  }
  
  /**
   * List all available interpreters by group
   */
  @GET
  @Path("interpreter")
  public Response listInterpreter(String message) {
    Map<String, InterpreterSetting> interpreterSettings = interpreterFactory.get();
    Map<String, RegisteredInterpreter> m = Interpreter.registeredInterpreters;    
    return new JsonResponse(Status.OK, "", m).build();
  }
}
