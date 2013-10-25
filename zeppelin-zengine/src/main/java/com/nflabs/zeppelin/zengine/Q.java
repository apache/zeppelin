package com.nflabs.zeppelin.zengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.log4j.Logger;
import org.datanucleus.util.StringUtils;

import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.result.Result;

/**
 * Q stands for Query
 * @author moon
 *
 */
public class Q extends Z{
	private String name;
	protected String query;
	Map<String, Object> params = new HashMap<String, Object>();
	private List<URI> resources = new LinkedList<URI>();
	private boolean cache;
	Result cachedResultDataObject;
	
	transient static final String ARG_VAR_NAME="arg";
	transient static final String INPUT_VAR_NAME="in";
	transient static final String OUTPUT_VAR_NAME="out";
	transient static final String NAME_PREFIX="zp_";
	
	public Q(String query) throws ZException{
		super();
		this.query = query;
		name = NAME_PREFIX + this.hashCode();
		initialize();
	}
	
	private Logger logger(){
		return Logger.getLogger(Q.class);
	}
	
	transient boolean initialized = false;
	protected void initialize() throws ZException {
		if(initialized==true){
			return ;
		}		
		initialized = true;
	}

	public Q withResource(URI r){
		resources.add(r);
		return this;
	}
	
	public Q withName(String name){
		this.name = name;
		return this;
	}
	
	public Q withCache(boolean cache){
		this.cache  = cache;
		return this;
	}
	
	public boolean isCache(){
		return cache;
	}
		
	public Q withParam(String key, Object val){
		params.put(key, val);
		return this;
	}
	
	protected String getQuery(BufferedReader erb, ZContext zcontext) throws ZException{
		return evalErb(erb, zcontext);
	}
	
	protected String evalErb(BufferedReader erb, Object zcontext) throws ZException{
		ScriptEngine engine = getRubyScriptEngine();

		StringBuffer rubyScript = new StringBuffer();
		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		bindings.put("z", zcontext);
		rubyScript.append("require 'erb'\n");
		rubyScript.append("z = $z\n");
		try {
			String line = null;
			rubyScript.append("erb = \"\"\n");
			
			while((line = erb.readLine())!=null){
				rubyScript.append("erb += \""+StringEscapeUtils.escapeJavaScript(line)+"\"\n");
			}
		} catch (IOException e1) {
			throw new ZException(e1);
		}
		rubyScript.append("$e = ERB.new(erb).result(binding)\n");

        try {
        	logger().debug("rubyScript to run : \n"+rubyScript.toString());
			engine.eval(rubyScript.toString(), bindings);
		} catch (ScriptException e) {
			throw new ZException(e);
		}
        
        String q = (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get("e");
        return q;
	}
	

	protected String evalWebTemplate(BufferedReader erb, ZWebContext zWebContext) throws ZException{
		return evalErb(erb, zWebContext);
	}

	
	@Override
	public String getQuery() throws ZException{
		ByteArrayInputStream ins = new ByteArrayInputStream(query.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
		
		ZContext zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);
				
		String q = getQuery(erb, zContext);
		try {ins.close();} catch (IOException e) {}
		
		String tableCreation = null;
		if(name==null){
			tableCreation = "";
		} else {
			if(cache){
				tableCreation = "CREATE TABLE "+name+" AS ";
			} else {
				tableCreation = "CREATE VIEW "+name+" AS ";
			}
		}
		
		return tableCreation+q;
	}
	
	public InputStream readWebResource(String path) throws ZException{
		initialize();
		
		ZWebContext zWebContext = null;
		try{
			zWebContext = new ZWebContext(result());
		} catch(ZException e){						
		}
		InputStream ins = this.getClass().getResourceAsStream("/table.erb");
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
	public List<URI> getResources() throws ZException {	
		if(prev()==null){
			return resources;
		} else {
			List<URI> r = new LinkedList<URI>();
			r.addAll(resources);
			r.addAll(prev().getResources());
			return r;
		}
	}
	
	@Override
	public String name(){
		return name;
	}
	
	@Override
	public String getReleaseQuery() throws ZException {
		if(name==null) return null;
		if(cache==true){
			return "DROP TABLE if exists "+name;
		} else {
			return "DROP VIEW if exists "+name;
		}
	}

	@Override
	public boolean isWebEnabled() {
		return true;
	}

}
