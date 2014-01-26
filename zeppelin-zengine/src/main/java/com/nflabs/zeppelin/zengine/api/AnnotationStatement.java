package com.nflabs.zeppelin.zengine.api;

import java.io.InputStream;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.util.Util;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

/**
 * AnnotationStatement consumes string statement like
 * 
 * @[Annotation] [Command] [Argument]
 * 
 * for example, statement like '@driver set hive'
 * getAnnotation() returns 'driver'
 * getCommand() returns 'set'
 * getArgument retuns 'hive'
 * 
 * @author moon
 *
 */
public class AnnotationStatement extends Q {
	transient static Pattern annotationPattern = Pattern.compile("@([^ ]*)\\s([^ ]*)(\\s(.*))?");
	private ANNOTATION annotation;
	private COMMAND command;
	private String arg;

	public static enum ANNOTATION {
		DRIVER
	};
	
	public static enum COMMAND{
		SET
	};

	public AnnotationStatement(String stmt, Zengine z, ZeppelinDriver driver) throws ZException{
		super(stmt, z, driver);
		
	    Matcher matcher = annotationPattern.matcher(query);
	    if (matcher.find()) {
	    	String annotation = matcher.group(1);
	    	String cmd = matcher.group(2);
	    	String arg = matcher.group(4);
	    	
	    	if (ANNOTATION.DRIVER.name().compareToIgnoreCase(annotation)==0) {
	    		this.annotation = ANNOTATION.DRIVER;
	    		if (COMMAND.SET.name().compareToIgnoreCase(cmd)==0) {
	    			this.command = COMMAND.SET;
	    		} else {
		    		throw new ZException("Unsupported command "+cmd);
	    		}
	    	} else {
	    		throw new ZException("Invalid annotation "+annotation);
	    	}
	    	
	    	this.arg = arg;
	    } else {
	    	throw new ZException("Invalid annotation syntax "+query);
	    }

	    
	}
	
	
	public ANNOTATION getAnnotation(){
		return annotation;
	}
	
	public COMMAND getCommand(){
		return command;
	}
	
	public String getArgument(){
		return arg;
	}
	
	@Override
	public String getQuery() throws ZException {
		return super.getQuery();
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
		return null;
	}

	@Override
	public boolean isWebEnabled() {
		return false;
	}

	@Override
	protected void initialize() throws ZException {
	}

	@Override
	protected Map<String, ParamInfo> extractParams() throws ZException {
		return null;
	}
	
	public Z execute() throws ZException {
		if(executed) { return this; }
		initialize();
		
		if (this.hasPrev()){
			prev().execute();
		}
		executed = true;
		return this;
	}
}
