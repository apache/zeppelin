package com.nflabs.zeppelin.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.nflabs.zeppelin.server.JsonResponse;


@Path("/analyze")
public class Analyze {
	Logger logger = Logger.getLogger(Analyze.class);

    
    @POST
    @Produces("application/json")
    @Path("zql")
    public Response zql(String data) {
    	logger.info("zql:"+data);
        return new JsonResponse(Status.OK, "zql").build();
    }   

}
