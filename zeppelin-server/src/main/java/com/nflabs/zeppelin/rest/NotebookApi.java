package com.nflabs.zeppelin.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.atmosphere.annotation.Broadcast;
import org.atmosphere.annotation.Suspend;
import org.atmosphere.config.service.AtmosphereService;
import org.atmosphere.jersey.JerseyBroadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.notebook.Notebook;

/**
 * Rest api endpoint for the noteBook.
 * 
 * @author Leemoonsoo
 * @author anthonycorbacho
 */
@Path("/notebook")
@Produces("application/json")
@AtmosphereService(broadcaster = JerseyBroadcaster.class)
public class NotebookApi {
  Logger logger = LoggerFactory.getLogger(NotebookApi.class);
  private Notebook notebook;
  public NotebookApi() {}

  public NotebookApi(Notebook notebook) {
    this.notebook = notebook;
  }

  @Suspend
  @GET
  public String suspend() {
    return "";
  }

  @Broadcast(writeEntity = false)
  @POST
  @Produces("application/json")
  public NotebookResponse broadcast(String message) {
    return new NotebookResponse("hello");
  }
}
