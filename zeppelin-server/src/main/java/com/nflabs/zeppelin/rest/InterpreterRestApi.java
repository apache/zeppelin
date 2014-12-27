package com.nflabs.zeppelin.rest;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.google.gson.Gson;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.InterpreterSetting;
import com.nflabs.zeppelin.rest.message.NewInterpreterSettingRequest;
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
  public Response listSettings() {
    Map<String, InterpreterSetting> interpreterSettings = interpreterFactory.get();
    return new JsonResponse(Status.OK, "", interpreterSettings).build();
  }

  /**
   * Add new interpreter setting
   * @param message
   * @return
   * @throws IOException
   * @throws InterpreterException
   */
  @PUT
  @Path("setting")
  public Response newSettings(String message) throws InterpreterException, IOException {
    NewInterpreterSettingRequest request = gson.fromJson(message,
        NewInterpreterSettingRequest.class);
    Properties p = new Properties();
    p.putAll(request.getProperties());
    interpreterFactory.add(request.getName(), request.getGroup(), p);
    return new JsonResponse(Status.OK, "").build();
  }

  @DELETE
  @Path("setting/{settingId}")
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
