package com.nflabs.zeppelin.rest;

import java.io.OutputStream;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.JsonResponse;
import com.nflabs.zeppelin.server.ZQLJob;
import com.nflabs.zeppelin.zan.Info;
import com.nflabs.zeppelin.zan.ZANException;

@Path("/zan")
public class ZAN {
	Logger logger = Logger.getLogger(ZAN.class);
	
	private com.nflabs.zeppelin.zan.ZAN zan;

	public ZAN(com.nflabs.zeppelin.zan.ZAN zan){
		this.zan = zan;
	}
	
	public static class SearchRequest{
		public String query;
	}
	
    @POST
    @Path("search")
    @Produces("application/json")
    public Response search(String json) {
    	Gson gson = new Gson();
    	SearchRequest sr = gson.fromJson(json, SearchRequest.class);
    	try {
			return new JsonResponse<List<Info>>(Status.OK, "", zan.list()).build();
		} catch (ZANException e) {
			logger.error("Search failed", e);
			return new JsonResponse<Object>(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
		}    	
    }
    
}
