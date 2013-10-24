package com.nflabs.zeppelin.rest;

import java.io.OutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/zan")
public class ZAN {
	@GET
	@Path("/webapp/${lib}/{path:.*}")
	public OutputStream webapp(){
		
		return null;		
	}
}
