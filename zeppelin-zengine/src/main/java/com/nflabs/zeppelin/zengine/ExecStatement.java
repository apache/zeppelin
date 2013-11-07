package com.nflabs.zeppelin.zengine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecStatement extends Q{
	public ExecStatement(String command) throws ZException{
		super(command);
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(ExecStatement.class);
	}

	@Override
	public List<URI> getResources() throws ZException {
		return new LinkedList<URI>();
	}

	@Override
	public String getReleaseQuery() throws ZException {
		return null;
	}

	@Override
	public InputStream readWebResource(String path) throws ZException {
		initialize();
		
		ZWebContext zWebContext = null;
		try{
			zWebContext = new ZWebContext(result());
		} catch(ZException e){						
		}
		InputStream ins = this.getClass().getResourceAsStream("/exec.erb");
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));					
		String q = evalWebTemplate(erb, zWebContext);
		try {
			ins.close();
		} catch (IOException e) {
			logger().error("Assert", e);
		}
		return new ByteArrayInputStream(q.getBytes());
	}

	@Override
	public boolean isWebEnabled() {
		return true;
	}

	@Override
	protected void initialize() throws ZException {
	}

	@Override
	protected Map<String, ParamInfo> extractParams() throws ZException {
		return new HashMap<String, ParamInfo>();
	}

}
