package com.nflabs.zeppelin.rest;

import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.ZQLSession;
import com.nflabs.zeppelin.server.ZQLSessionManager;
import com.nflabs.zeppelin.server.JsonResponse;


@Path("/zql")
public class ZQL {	
	Logger logger = Logger.getLogger(ZQL.class);
	ZQLSessionManager sessionManager;
	private Gson gson;
	
	
	public ZQL(ZQLSessionManager sessionManager){
		this.sessionManager = sessionManager;
		gson = new Gson();
	}
    
    @GET
    @Path("new")
    @Produces("application/json")
    public Response newSession() {
    	ZQLSession s = sessionManager.create();
        return new JsonResponse(Status.OK, "", s).build();
    }

    @POST
    @Path("set/{sessionId}")
    @Produces("application/json")
    public Response set(@PathParam("sessionId") String sessionId, String json) {
    	
    	Map<String, Object> data = gson.fromJson(json, Map.class);
    	ZQLSession s = sessionManager.setZql(sessionId, (String) data.get("zql"));
    	if(s==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
    	} else {    	
    		return new JsonResponse(Status.OK, "", s).build();
    	}
    }
    
    @GET
    @Path("run/{sessionId}")
    @Produces("application/json")
    public Response set(@PathParam("sessionId") String sessionId) {
    	 ZQLSession s = sessionManager.run(sessionId);
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
    	 ZQLSession s = sessionManager.get(sessionId);
    	 if(s==null){
    		 return new JsonResponse(Status.NOT_FOUND, "not found").build(); 
    	 } else {
    		 return new JsonResponse(Status.OK, "", s).build();	 
    	 }
    }
    
   
	static class FindParam{
		long from;
		long to;
		int max;
	} 
	
    /**
     * Find session
     * @param data
     * @return
     */
    @POST
    @Path("find")
    @Produces("application/json")    
    public Response find(String json) {

    	FindParam param = gson.fromJson(json, FindParam.class);
    	
    	Date from = null;
    	Date to = null;
    	int max = 10;

    	if(param.from>0){
    		from = new Date(param.from);
    	}
    	if(param.to>0){
    		to = new Date(param.to);
    	}

    	if(param.max>0){
    		max =  param.max;
    	}

    	TreeMap<String, ZQLSession> sessions = sessionManager.find(from, to, max);
    	
        return new JsonResponse(Status.OK, "", new LinkedList<ZQLSession>(sessions.descendingMap().values())).build();
    }   

    @GET
    @Path("del/{sessionId}")
    @Produces("application/json")
    public Response del(@PathParam("sessionId") String sessionId) {
    	 boolean s = sessionManager.delete(sessionId);
    	 if(s==false){
    		 return new JsonResponse(Status.NOT_FOUND).build(); 
    	 } else {
    		 return new JsonResponse(Status.OK).build();	 
    	 }
    }
    

    
}
