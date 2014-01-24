package com.nflabs.zeppelin.rest;

import java.io.OutputStream;
import java.util.Collection;
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
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.server.JsonResponse;
import com.nflabs.zeppelin.server.ZANJob;
import com.nflabs.zeppelin.server.ZANJobManager;
import com.nflabs.zeppelin.server.ZQLJob;
import com.nflabs.zeppelin.zan.Info;
import com.nflabs.zeppelin.zan.ZANException;

@Path("/zan")
public class ZANRestApi {
	Logger logger = Logger.getLogger(ZANRestApi.class);
	
	private com.nflabs.zeppelin.zan.ZAN zan;

	private ZANJobManager jobManager;

	public ZANRestApi(com.nflabs.zeppelin.zan.ZAN zan, ZANJobManager zanJobManager){
		this.zan = zan;
		this.jobManager = zanJobManager;
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
    	// TODO implement search instead of list
    	try {
			return new JsonResponse<List<Info>>(Status.OK, "", zan.list()).build();
		} catch (ZANException e) {
			logger.error("Search failed", e);
			return new JsonResponse<Object>(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
		}    	
    }
    
    @GET
    @Path("update")
    @Produces("application/json")
    public Response update() {
    	jobManager.update();
    	return new JsonResponse<Object>(Status.OK, "Job submitted").build();
    }
    
    @GET
    @Path("running")
    @Produces("application/json")
    public Response getJobsRunning(){
    	List<ZANJob> job = jobManager.getJobsRunning();
    	return new JsonResponse<List<ZANJob>>(Status.OK, "", job).build();
    }
    
    @GET
    @Path("install/{libName}")
    @Produces("application/json")
    public Response install(@PathParam("libName") String libName){
    	jobManager.install(libName);
    	return new JsonResponse<List<ZANJob>>(Status.OK, "").build();
    }

    @GET
    @Path("uninstall/{libName}")
    @Produces("application/json")
    public Response uninstall(@PathParam("libName") String libName){
    	jobManager.uninstall(libName);
    	return new JsonResponse<List<ZANJob>>(Status.OK, "").build();
    }

    @GET
    @Path("upgrade/{libName}")
    @Produces("application/json")
    public Response upgrade(@PathParam("libName") String libName){
    	jobManager.upgrade(libName);
    	return new JsonResponse<List<ZANJob>>(Status.OK, "").build();
    }

}
