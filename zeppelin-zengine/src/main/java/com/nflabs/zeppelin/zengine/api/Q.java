package com.nflabs.zeppelin.zengine.api;

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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZContext;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZWebContext;
import com.nflabs.zeppelin.zengine.Zengine;

/**
 * Q stands for Query
 * @author moon
 *
 */
public class Q extends Z {
	protected String query;
	private List<URI> resources = new LinkedList<URI>();
	Result cachedResultDataObject;
	transient boolean initialized = false;
    
	transient static final String ARG_VAR_NAME="arg";
	transient static final String INPUT_VAR_NAME="in";
	transient static final String OUTPUT_VAR_NAME="out";
	
	/**
	 * Create with given query. Query is single HiveQL statement.
	 * Query can erb template. ZContext is injected to the template
	 * @param query
	 * @param z 
	 * @param driver 
	 * @throws ZException
	 */
	public Q(String query, Zengine z, ZeppelinDriver driver) throws ZException{
		super(z);
		this.query = query;
		this.driver = driver;
		
		initialize();
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(Q.class);
	}
	
	protected void initialize() throws ZException {
		if(initialized){
			return ;
		}		
		initialized = true;
	}

	/**
	 * Adds resource required by this query.
	 * Either normal file or jar lib.
	 * Will be automatically added before executing this query.
	 * i.e. <code>ADD FILE [uri]</code> or <code>ADD JAR [uri]</code> is executed automatically.
	 * @param r
	 * @return
	 */
	public Q withResource(URI r){
		resources.add(r);
		return this;
	}
	
	protected String getQuery(BufferedReader erb, ZContext zcontext) throws ZException{
		return evalErb(erb, zcontext);
	}
	
	protected String evalErb(BufferedReader erb, Object zcontext) throws ZException{
		ScriptEngine engine = zen.getRubyScriptEngine();

		StringBuffer rubyScript = new StringBuffer();
		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		bindings.put("z", zcontext);
		rubyScript.append("require 'erb'\n");
		rubyScript.append("z = $z\n");
		try {
			String line = null;
			rubyScript.append("erb = \"\"\n");

			boolean first = true;
			while((line = erb.readLine())!=null){
				String newline;
				if(first==false){
					newline = "\\n";
				} else {
					newline = "";
					first = false;
				}
				rubyScript.append("erb += \""+newline+StringEscapeUtils.escapeJavaScript(line)+"\"\n");
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
        return nonNullString(q);
	}

	private String nonNullString(String q) {
	    return q == null ? "" : q;
	}

    protected String evalWebTemplate(BufferedReader erb, ZWebContext zWebContext) throws ZException{
		return evalErb(erb, zWebContext);
	}

	/**
	 * Get query to be executed.
	 * Template is evaluated with ZContext.
	 * And CREATE TABLE or CREATE VIEW statement is added in front of query according to settings
	 */
	@Override
	public String getQuery() throws ZException{
		ByteArrayInputStream ins = new ByteArrayInputStream(query.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));

		ZContext zContext = new ZContext( hasPrev() ? prev().name() : null, name(), query, params);

		String q = getQuery(erb, zContext);
		try {ins.close();} catch (IOException e) {}

		return q;
	}
	
	/**
	 * Get web representation of result.
	 * Printed as a table
	 */
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

	
	/**
	 * Return URI of resources
	 */
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
	
	/**
	 * When this object is no longer used, this method is invoked to cleanup results.
	 */
	@Override
	public String getReleaseQuery() throws ZException {
		return null;
	}

	/**
	 * Check if it is capable of rendering web
	 */
	@Override
	public boolean isWebEnabled() {
		if(next()==null) return true;
		else return false;
	}

	@Override
	protected Map<String, ParamInfo> extractParams() throws ZException {
		Map<String, ParamInfo> paramInfos = new HashMap<String, ParamInfo>();
		
		ByteArrayInputStream ins = new ByteArrayInputStream(query.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
		
		ZContext zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);
				
		try {
			getQuery(erb, zContext);
		} catch (ZException e1) {
			logger().debug("dry run error", e1);
		}
		try {ins.close();} catch (IOException e) {}

		ZWebContext zWebContext = null;
		if(isWebEnabled()==true){

			zWebContext = new ZWebContext(null);
			InputStream in = this.getClass().getResourceAsStream("/table.erb");
			erb = new BufferedReader(new InputStreamReader(in));					
			try {
				evalWebTemplate(erb, zWebContext);
			} catch (ZException e1) {
				logger().debug("dry run error", e1);
			}
			try {
				ins.close();
			} catch (IOException e) {
				logger().error("Assert", e);
			}
		}

		if(zWebContext!=null){
			paramInfos.putAll(zWebContext.getParamInfos());
		}
		
		if(zContext!=null){
			paramInfos.putAll(zContext.getParamInfos());
		}
		
		return paramInfos;
	}

}
