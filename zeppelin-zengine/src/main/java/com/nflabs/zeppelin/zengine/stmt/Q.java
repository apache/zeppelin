package com.nflabs.zeppelin.zengine.stmt;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.util.Util;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZContext;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZWebContext;
import com.sun.script.jruby.JRubyScriptEngineFactory;

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
	 * ERB local variables
	 */
	transient private Map<String,Object> binding = null;

    /**
     * Once getQuery() evaluated, then save result into this variable so, don't evaluate again. 
     */
    transient private String evaluatedQuery = null;

	/**
	 * Create with given query. Query is single HiveQL statement.
	 * Query can erb template. ZContext is injected to the template
	 * @param query
	 * @param z 
	 * @param driver 
	 * @throws ZException
	 */
	public Q(String query) throws ZException{
		super();
		this.query = query;
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
	
	private static ScriptEngine rubyScriptEngine;
	static {
		JRubyScriptEngineFactory factory = new JRubyScriptEngineFactory();
		rubyScriptEngine = factory.getScriptEngine();
		StringBuffer rubyScript = new StringBuffer();
		rubyScript.append("require 'erb'\n");
		try {
			rubyScriptEngine.eval(rubyScript.toString());
		} catch (ScriptException e) {
			e.printStackTrace();
		}
	}
	
	private Map<String, Object> getErbBinding(){
		if(binding==null){
			if(hasPrev() && prev() instanceof Q) {
				binding = ((Q)prev()).getErbBinding();
			} else {
				binding = new HashMap<String, Object>();
			}
		}
		return binding;
	}

	public void withErbBinding(Map<String, Object> b){
		binding = b;
	}

	protected String evalErb(BufferedReader erb, Object zcontext) throws ZException{
		synchronized(rubyScriptEngine){
			StringBuffer rubyScript = new StringBuffer();
			Bindings bindings = rubyScriptEngine.createBindings();
			bindings.put("_zpZ", zcontext);
			bindings.put("_zpLV", getErbBinding());
			rubyScriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
			for(String k : getErbBinding().keySet()){
				rubyScript.append(k+"= $_zpLV.get(\""+k+"\")\n");
			}
			rubyScript.append("z = $_zpZ\n");
			try {
				String line = null;
				rubyScript.append("$_zpErb = \"\"\n");
	
				boolean first = true;
				while((line = erb.readLine())!=null){
					String newline;
					if(first==false){
						newline = "\\n";
					} else {
						newline = "";
						first = false;
					}
					rubyScript.append("$_zpErb += \""+newline+line.replaceAll("\"", "\\\\\"")+"\"\n");
				}
				rubyScript.append("$_zpErb += \"<% local_variables.each do |xx|\n    if xx != 'z' and xx != '_erbout' then $_zpLV[xx] = eval(xx) end\nend %>\"\n");
			} catch (IOException e1) {
				throw new ZException(e1);
			}
			rubyScript.append("$_zpE = ERB.new($_zpErb, \"<>-\").result(binding)\n");
	        try {
	        	rubyScriptEngine.eval(rubyScript.toString(), bindings);
			} catch (ScriptException e) {
				throw new ZException(e);
			}	        
	        String q = (String) bindings.get("_zpE");

	        return nonNullString(q);
		}
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
		if(evaluatedQuery!=null) return evaluatedQuery;

		if(hasPrev()){
			// purpose of calling is evaluating erb. because it need to share the same local variable context
			prev().getQuery();
		}

		ByteArrayInputStream ins;
		try {
			ins = new ByteArrayInputStream(query.getBytes());
			BufferedReader erb = new BufferedReader(new InputStreamReader(ins, Util.getCharsetName()));
			ZContext zContext = new ZContext( hasPrev() ? prev().name() : null, name(), query, params);
			String q = getQuery(erb, zContext);
			ins.close();
			return q;
		} catch (UnsupportedEncodingException e1) {
			throw new ZException(e1);
		} catch (IOException e) {
			throw new ZException(e);
		}
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
