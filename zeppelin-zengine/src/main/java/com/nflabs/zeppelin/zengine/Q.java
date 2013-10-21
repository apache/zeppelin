package com.nflabs.zeppelin.zengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URI;
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
import org.datanucleus.util.StringUtils;

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
	transient static final String ARG_VAR_NAME="arg";
	transient static final String PREV_VAR_NAME="table";
	transient static final String NAME_PREFIX="zp_";
	
	public Q(String query){
		this.query = query;
		name = NAME_PREFIX + this.hashCode();
		cache = false;
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
        	logger.debug("rubyScript to run : \n"+rubyScript.toString());
			engine.eval(rubyScript.toString(), bindings);
		} catch (ScriptException e) {
			throw new ZException(e);
		}
        
        String q = (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get("e");
        return q;
	}
	
	@Override
	public String getQuery() throws ZException{
		ByteArrayInputStream ins = new ByteArrayInputStream(query.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
		
		ZContext zContext = new ZContext( (prev()==null) ? null : prev().name(), null, params);
				
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
	public String getCleanQuery() throws ZException {
		if(name==null) return null;
		if(cache==true){
			return "DROP TABLE "+name;
		} else {
			return "DROP VIEW "+name;
		}
	}

}
