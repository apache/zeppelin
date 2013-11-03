package com.nflabs.zeppelin.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.nflabs.zeppelin.server.ZQLSession;
import com.nflabs.zeppelin.server.ZQLSessionManager;
import com.nflabs.zeppelin.server.JsonResponse;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;


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

	static class SetZqlParam{
		String name;
		String zql;
		List<Map<String, Object>> params;
	} 

    @POST
    @Path("set/{sessionId}")
    @Produces("application/json")
    public Response setZql(@PathParam("sessionId") String sessionId, String json) {
    	
    	SetZqlParam data = gson.fromJson(json, SetZqlParam.class);
    	ZQLSession s = sessionManager.setZql(sessionId, data.zql);
    	if(s==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
    	}
    	
    	s = sessionManager.setParams(sessionId, data.params);
    	if(s==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
    	}
    	
    	s = sessionManager.setName(sessionId, data.name);
    	if(s==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
    	}
    	
    	return new JsonResponse(Status.OK, "", s).build();
    }
    
        
    @GET
    @Path("run/{sessionId}")
    @Produces("application/json")
    public Response run(@PathParam("sessionId") String sessionId) {
    	 ZQLSession s = sessionManager.run(sessionId);
    	 if(s==null){
    		 return new JsonResponse(Status.NOT_FOUND).build(); 
    	 } else {
    		 return new JsonResponse(Status.OK, "", s).build();	 
    	 }
    }
    
    @GET
    @Path("run/{sessionId}/dry")
    @Produces("application/json")
    public Response dryRun(@PathParam("sessionId") String sessionId) {
    	 ZQLSession s = sessionManager.dryRun(sessionId);
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
    

    @GET
    @Path("web/{sessionId}/{zId}/{path:.*}")
    public Response web(@PathParam("sessionId") String sessionId, @PathParam("zId") String zId, @PathParam("path") String path){
    	ZQLSession session = sessionManager.get(sessionId);
    	if(session==null){
    		return new JsonResponse(Status.NOT_FOUND).build();
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
    
    private MediaType typeByExtention(String path){
    	String filename;
		try {
			filename = new URI(path).getPath();
		} catch (URISyntaxException e) {
			logger.error("Invalid path "+path, e);
			return MediaType.APPLICATION_OCTET_STREAM_TYPE;
		}
    	
    	if(filename.endsWith(".erb")){
    		return MediaType.TEXT_HTML_TYPE;
    	} else if(filename.endsWith(".html") || filename.endsWith(".htm")){
    		return MediaType.TEXT_HTML_TYPE;
    	} else if(filename.endsWith(".css") || filename.endsWith(".js")){
    		return MediaType.TEXT_PLAIN_TYPE;
    	} else if(filename.endsWith(".json")){
    		return MediaType.APPLICATION_JSON_TYPE;
    	} else if(filename.endsWith(".xml")){
    		return MediaType.APPLICATION_XML_TYPE;
    	} else if(filename.endsWith(".text") || filename.endsWith(".txt")){
    		return MediaType.TEXT_PLAIN_TYPE;
    	} else {
    		return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    	}
    }
    
}
