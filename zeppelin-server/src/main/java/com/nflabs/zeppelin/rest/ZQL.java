package com.nflabs.zeppelin.rest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.JsonResponse;
import com.nflabs.zeppelin.server.ZQLJob;
import com.nflabs.zeppelin.server.ZQLJobManager;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.api.Z;

@Path("/zql")
public class ZQL {	
    Logger logger = LoggerFactory.getLogger(ZQL.class);
    
	@SuppressWarnings("rawtypes")
    private static final Response STATUS_NOT_FOUND = new JsonResponse(Status.NOT_FOUND).build();
	ZQLJobManager sessionManager;
	private Gson gson;
	
	public ZQL(ZQLJobManager sessionManager){
		this.sessionManager = sessionManager;
		gson = new Gson();
	}
    
    @GET
    @Path("new")
    @Produces("application/json")
    public Response newSession() {
    	ZQLJob s = sessionManager.create();
        return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }

	static class SetZqlParam{
		String name;
		String zql;
		String cron;
		List<Map<String, Object>> params;
	} 

    @POST
    @Path("set/{sessionId}")
    @Produces("application/json")
    public Response set(@PathParam("sessionId") String sessionId, String json) {
    	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLJob s = sessionManager.setZql(sessionId, data.zql);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	
    	s = sessionManager.setParams(sessionId, data.params);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	
    	s = sessionManager.setName(sessionId, data.name);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	
    	s = sessionManager.setCron(sessionId, data.cron);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }

    @POST
    @Path("set/{sessionId}/zql")
    @Produces("application/json")
    public Response setZql(@PathParam("sessionId") String sessionId, String json) {
    	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLJob s = sessionManager.setZql(sessionId, data.zql);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }
    
    @POST
    @Path("set/{sessionId}/name")
    @Produces("application/json")
    public Response setName(@PathParam("sessionId") String sessionId, String json) {	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLJob s = sessionManager.setName(sessionId, data.name);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }    
     
    @POST
    @Path("set/{sessionId}/params")
    @Produces("application/json")
    public Response setParams(@PathParam("sessionId") String sessionId, String json) {	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLJob s = sessionManager.setParams(sessionId, data.params);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    } 
    
    @POST
    @Path("set/{sessionId}/cron")
    @Produces("application/json")
    public Response setCron(@PathParam("sessionId") String sessionId, String json) {	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLJob s = sessionManager.setCron(sessionId, data.cron);
    	if(s==null){
    		return STATUS_NOT_FOUND;
    	}
    	return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }
    
    @GET
    @Path("run/{sessionId}")
    @Produces("application/json")
    public Response run(@PathParam("sessionId") String sessionId) {
    	 ZQLJob s = sessionManager.run(sessionId);
    	 if(s==null){
    		 return STATUS_NOT_FOUND;
    	 } 
    	 return new JsonResponse<ZQLJob>(Status.OK, "", s).build();	  
    }
    
    @GET
    @Path("run/{sessionId}/dry")
    @Produces("application/json")
    public Response dryRun(@PathParam("sessionId") String sessionId) {
    	 ZQLJob s = sessionManager.dryRun(sessionId);
    	 if(s==null){
    		 return STATUS_NOT_FOUND;
    	 }
    	 return new JsonResponse<ZQLJob>(Status.OK, "", s).build();	 
    }
    
    @GET
    @Path("abort/{sessionId}")
    @Produces("application/json")
    public Response abort(@PathParam("sessionId") String sessionId) {
    	 ZQLJob s = sessionManager.abort(sessionId);
    	 if(s==null){
    		 return STATUS_NOT_FOUND;
    	 } 
    	 return new JsonResponse<ZQLJob>(Status.OK, "", s).build();	  
    }
    
    @GET
    @Path("get/{sessionId}")
    @Produces("application/json")
    public Response get(@PathParam("sessionId") String sessionId) {
    	 ZQLJob s = sessionManager.get(sessionId);
    	 if(s==null) {
    		 return STATUS_NOT_FOUND;
    	 }
         return new JsonResponse<ZQLJob>(Status.OK, "", s).build();
    }
    
    /**
     * list session
     * @return
     */
    @GET
    @Path("list")
    @Produces("application/json")    
    public Response find() {
    	TreeMap<String, ZQLJob> sessions = sessionManager.list();
        return new JsonResponse<LinkedList<ZQLJob>>(Status.OK, "",
                new LinkedList<ZQLJob>(sessions.descendingMap().values()))
                .build();
    }   

    @GET
    @Path("del/{sessionId}")
    @Produces("application/json")
    public Response del(@PathParam("sessionId") String sessionId) {
    	 boolean s = sessionManager.delete(sessionId);
    	 if(s==false){
    		 return STATUS_NOT_FOUND; 
    	 }
    	 return new JsonResponse(Status.OK).build();	 
    }

    /**
     * Get session history list
     * @return
     */
    @GET
    @Path("history/list/{sessionId}")
    @Produces("application/json")    
    public Response listHistory(@PathParam("sessionId") String sessionId) {
    	TreeMap<String, ZQLJob> sessions = sessionManager.listHistory(sessionId);
        return new JsonResponse<NavigableMap<String, ZQLJob>>(Status.OK, "", sessions.descendingMap()).build();
    }    

    /**
     * Get a session history
     * @return
     */
    @GET
    @Path("history/get/{sessionId}/{name}")
    @Produces("application/json")    
    public Response getHistory(@PathParam("sessionId") String sessionId, @PathParam("name") String name) {
    	ZQLJob session = sessionManager.getHistory(sessionId, name);
        return new JsonResponse<ZQLJob>(Status.OK, "", session).build();
    }

    /**
     * Delete a session history
     * @return
     */
    @GET
    @Path("history/del/{sessionId}/{name}")
    @Produces("application/json")    
    public Response deleteHistory(@PathParam("sessionId") String sessionId, @PathParam("name") String name) {
    	sessionManager.deleteHistory(sessionId, name);
        return new JsonResponse<ZQLJob>(Status.OK).build();
    }

    /**
     * Delete all session history
     * @return
     */
    @GET
    @Path("history/del/{sessionId}")
    @Produces("application/json")    
    public Response deleteHistory(@PathParam("sessionId") String sessionId) {
    	sessionManager.deleteHistory(sessionId);
        return new JsonResponse<ZQLJob>(Status.OK).build();
    }

    

    @GET
    @Path("web/{sessionId}/{zId}/{path:.*}")
    public Response web(@PathParam("sessionId") String sessionId, @PathParam("zId") String zId, @PathParam("path") String path){
    	ZQLJob session = sessionManager.get(sessionId);
    	if(session==null){
    		return STATUS_NOT_FOUND;
    	}
    	if(path==null || path.equals("/") || path.length()==0){
    		path = "/index.erb";
    	}
    	
    	List<Z> plans = session.getPlan();
    	for(Z plan : plans){
    		for(Z z = plan; z!=null; z=z.prev()){
	    		if(z.getId().equals(zId)){
	    			try {
						InputStream ins = z.readWebResource(path);
						if(ins==null){
							return null;
						} else {
							return Response.ok(IOUtils.toByteArray(ins), typeByExtention(path)).build();
						}
					} catch (ZException e) {
						logger.error("Can't read web resource", e);
						return new JsonResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
					} catch (IOException e) {
						logger.error("IOexception", e);
						return new JsonResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
					}
	    		}
    		}
    	}
		return javax.ws.rs.core.Response.status(Status.NOT_FOUND).entity("").build();    	
    }

    @GET
    @Path("history/web/{sessionId}/{historyId}/{zId}/{path:.*}")
    public Response historyWeb(@PathParam("sessionId") String sessionId, @PathParam("historyId") String historyId, @PathParam("zId") String zId, @PathParam("path") String path){
    	ZQLJob session = sessionManager.getHistory(sessionId, historyId);
    	if(session==null){
    		logger.warn("Session "+sessionId+", history "+historyId+" not found");
    		return STATUS_NOT_FOUND;
    	}
    	if(path==null || path.equals("/") || path.length()==0){
    		path = "/index.erb";
    	}
    	
    	List<Z> plans = session.getPlan();
    	for(Z plan : plans){
    		for(Z z = plan; z!=null; z=z.prev()){
	    		if(z.getId().equals(zId)){
	    			try {
						InputStream ins = z.readWebResource(path);
						if(ins==null){
							return null;
						} else {
							return Response.ok(IOUtils.toByteArray(ins), typeByExtention(path)).build();
						}
					} catch (ZException e) {
						logger.error("Can't read web resource", e);
						return new JsonResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
					} catch (IOException e) {
						logger.error("IOexception", e);
						return new JsonResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
					}
	    		}
    		}
    	}
		return javax.ws.rs.core.Response.status(Status.NOT_FOUND).entity("").build();    	
    }
    
    private String typeByExtention(String path){
    	String filename;
		try {
			filename = new URI(path).getPath();
		} catch (URISyntaxException e) {
			logger.error("Invalid path "+path, e);
			return MediaType.APPLICATION_OCTET_STREAM_TYPE.toString();
		}
    	
    	if(filename.endsWith(".erb")) {
    		return MediaType.TEXT_HTML_TYPE.toString();
    	} else if(filename.endsWith(".html") || filename.endsWith(".htm")){
    		return MediaType.TEXT_HTML_TYPE.toString();
    	} else if(filename.endsWith(".css")){
    		return "text/css";
    	} else if(filename.endsWith(".js")){
    		return "text/javascript";
    	} else if(filename.endsWith(".json")){
    		return MediaType.APPLICATION_JSON_TYPE.toString();
    	} else if(filename.endsWith(".xml")){
    		return MediaType.APPLICATION_XML_TYPE.toString();
    	} else if(filename.endsWith(".text") || filename.endsWith(".txt")){
    		return MediaType.TEXT_PLAIN_TYPE.toString();
    	} else {
    		return MediaType.APPLICATION_OCTET_STREAM_TYPE.toString();
    	}
    }
    
}
