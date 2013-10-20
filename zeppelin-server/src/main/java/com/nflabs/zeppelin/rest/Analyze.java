package com.nflabs.zeppelin.rest;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.AnalyzeSession;
import com.nflabs.zeppelin.server.AnalyzeSessionManager;
import com.nflabs.zeppelin.server.JsonResponse;


@Path("/analyze")
public class Analyze {	
	Logger logger = Logger.getLogger(Analyze.class);
	AnalyzeSessionManager sessionManager;
	private Gson gson;
	
	
	public Analyze(AnalyzeSessionManager sessionManager){
		this.sessionManager = sessionManager;
		gson = new Gson();
	}
    
    @GET
    @Path("new")
    @Produces("application/json")
    public Response newSession() {
    	AnalyzeSession s = sessionManager.create();
        return new JsonResponse(Status.OK, "", s).build();
    }

    @POST
    @Path("set/{sessionId}")
    @Produces("application/json")
    public Response set(@PathParam("sessionId") String sessionId, String json) {
    	AnalyzeSession s = sessionManager.get(sessionId);
    	if(s==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
    	}
    	
    	Map<String, Object> data = gson.fromJson(json, Map.class);
    	
    	if(data.containsKey("name")){
    		s.setJobName((String) data.get("name"));
    	}
    	if(data.containsKey("zql")){
    		s.setZQL((String) data.get("zql"));
    	}
    	
    	return new JsonResponse(Status.OK, "", s).build();
    }
    
    @GET
    @Path("run/{sessionId}")
    @Produces("application/json")
    public Response set(@PathParam("sessionId") String sessionId) {
    	 AnalyzeSession s = sessionManager.run(sessionId);
    	 if(s==null){
    		 return new JsonResponse(Status.NOT_FOUND).build(); 
    	 } else {
    		 return new JsonResponse(Status.OK, "", s).build();	 
    	 }
    }
    
    @GET
    @Path("get/{sessionId}")
    @Produces("application/json")
    public Response get(@PathParam("sessionId") String sessionId) {
    	 AnalyzeSession s = sessionManager.get(sessionId);
    	 if(s==null){
    		 return new JsonResponse(Status.NOT_FOUND, "not found").build(); 
    	 } else {
    		 return new JsonResponse(Status.OK, "", s).build();	 
    	 }
    }
    
    
    /**
     * Get all running sessions
     * @param data
     * @return
     */
    @GET
    @Path("getAllRunning")
    @Produces("application/json")    
    public Response getAllRunning(String data) {
    	Map<String, AnalyzeSession> sessions = sessionManager.getRunning();
        return new JsonResponse(Status.OK, "", sessions.values()).build();
    }   


    @GET
    @Path("del/{sessionId}")
    @Produces("application/json")
    public Response del(@PathParam("sessionId") String sessionId) {
    	 boolean s = sessionManager.discard(sessionId);
    	 if(s==false){
    		 return new JsonResponse(Status.NOT_FOUND).build(); 
    	 } else {
    		 return new JsonResponse(Status.OK).build();	 
    	 }
    }
    
    

}
