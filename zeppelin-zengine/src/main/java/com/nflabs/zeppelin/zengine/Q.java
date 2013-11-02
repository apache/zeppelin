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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.result.Result;

/**
 * Q stands for Query
 * @author moon
 *
 */
public class Q extends Z{
	protected String query;
	Map<String, Object> params = new HashMap<String, Object>();
	private List<URI> resources = new LinkedList<URI>();
	Result cachedResultDataObject;
	
	transient static final String ARG_VAR_NAME="arg";
	transient static final String INPUT_VAR_NAME="in";
	transient static final String OUTPUT_VAR_NAME="out";
	
	/**
	 * Create with give query. Query is signle HiveQL statment.
	 * Query can be templated by erb. ZContext is injected to the template
	 * @param query
	 * @throws ZException
	 */
	public Q(String query) throws ZException{
		super();
		this.query = query;
		
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

	/**
	 * add resource required by this query.
	 * ether normal file or jar.
	 * Will be automatically added before executing this query.
	 * ie. ADD FILE [uri] or ADD JAR [uri] is executed automatically.
	 * @param r
	 * @return
	 */
	public Q withResource(URI r){
		resources.add(r);
		return this;
	}
	

	

		
	/**
	 * Add a paramter to pass template environment
	 * @param key name of parameter
	 * @param val value of parameter
	 * @return this object
	 */
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
        return q;
	}
	

	protected String evalWebTemplate(BufferedReader erb, ZWebContext zWebContext) throws ZException{
		return evalErb(erb, zWebContext);
	}

	/**
	 * Get query to be executed.
	 * Template is evaluated with ZContext.
	 * And CREATE TABLE or CREATE VIEW statment is added in front of query according to settings
	 */
	@Override
	public String getQuery() throws ZException{
		ByteArrayInputStream ins = new ByteArrayInputStream(query.getBytes());
		BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
		
		ZContext zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);
				
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

}
