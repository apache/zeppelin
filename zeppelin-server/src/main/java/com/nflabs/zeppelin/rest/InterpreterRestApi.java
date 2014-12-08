package com.nflabs.zeppelin.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.InterpreterSetting;
import com.nflabs.zeppelin.server.JsonResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Interpreter Rest API
 *
 */
@Path("/interpreter")
@Produces("application/json")
public class InterpreterRestApi {
  private InterpreterFactory interpreterFactory;

  public InterpreterRestApi(InterpreterFactory interpreterFactory) {
    this.interpreterFactory = interpreterFactory;
  }

  /**
   * List all interpreter settings
   * @param message
   * @return
   */
  @GET
  @Produces("application/json")
  @Path("settings/list")
  public Response listSettings(String message) {
    Map<String, InterpreterSetting> interpreterSettings = interpreterFactory.get();
    return new JsonResponse(Status.OK, "", interpreterSettings).build();
  }
}
