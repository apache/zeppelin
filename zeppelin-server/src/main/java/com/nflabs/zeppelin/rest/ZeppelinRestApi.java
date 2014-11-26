package com.nflabs.zeppelin.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;

/**
 * Zeppelin root rest api endpoint.
 *
 * @author anthonycorbacho
 * @since 0.3.4
 */
@Path("/")
@Api(value = "/", description = "Zeppelin REST API root")
public class ZeppelinRestApi {

  /**
   * Required by Swagger.
   */
  public ZeppelinRestApi() {
    super();
  }

  /**
   * Get the root endpoint Return always 200.
   *
   * @return 200 response
   */
  @GET
  public Response getRoot() {
    return Response.ok().build();
  }
}
