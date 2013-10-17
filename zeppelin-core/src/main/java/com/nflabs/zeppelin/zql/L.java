package com.nflabs.zeppelin.zql;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public class L extends Z{
	Logger logger = Logger.getLogger(L.class);
	
	private String name;
	Map<String, Object> params = new HashMap<String, Object>();
	
	private URI libUri;
	private Path dir;
	private Path erbFile;
	private FileSystem fs;
	
	public L(String name) throws ZException{
		this.name = name;
		fs = fs();
		
		try {
			if(name.indexOf(":/")>0){
				libUri = new URI(name);
			} else {
				libUri = new URI(conf().getString(ConfVars.ZEPPELIN_LIBRARY_DIR)+"/"+name);	
			}
		} catch (URISyntaxException e1) {
			throw new ZException(e1);
		}
		
		try {		
			// search for library dir
			this.dir = new Path(libUri);
			if(fs.isDirectory(dir)==false){
				throw new ZException("Directory "+dir.toUri()+" does not exist");
			}
			this.erbFile = new Path(libUri.toString()+"/"+new Path(libUri).getName()+".erb");

			if(fs.isFile(erbFile)==false){
				throw new ZException("Template "+erbFile.toString()+" does not exist");
			}
		} catch (IOException e) {
			throw new ZException(e);
		}
	}
		
	public L withParam(String key, Object val){
		params.put(key, val);
		return this;
	}
	
	
	public String toString(){
		return name;
	}

	
	
	
	@Override
	public String getQuery() throws ZException {
		ScriptEngine engine = getRubyScriptEngine();

		StringBuffer rubyScript = new StringBuffer();
		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

		rubyScript.append("require 'erb'\n");
		
		String prevQuery = null;
		if(prev()!=null){
			prevQuery = prev().getQuery();
		}
		
		// add arg local var
		bindings.put(Q.PREV_VAR_NAME, prevQuery);		
		rubyScript.append(Q.PREV_VAR_NAME+"=$"+Q.PREV_VAR_NAME+"\n");
		
		// add param local var
		bindings.put("params", params);
		rubyScript.append("params = {}\n");
		rubyScript.append("$params.each{|key,value|params[key]=value}\n");
		
		try {
			FSDataInputStream ins = fs.open(erbFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(ins));
			String line = null;
			rubyScript.append("erb = \"\"\n");
			
			while((line = br.readLine())!=null){
				rubyScript.append("erb += \""+line+"\"\n");
			}
			ins.close();
		} catch (IOException e1) {
			throw new ZException(e1);
		}
		rubyScript.append("$e = ERB.new(erb).result(binding)\n");

        try {
        	logger.info("rubyScript to run : \n"+rubyScript.toString());
			engine.eval(rubyScript.toString(), bindings);
		} catch (ScriptException e) {
			throw new ZException(e);
		}
        return (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get("e");
	}

	@Override
	public List<URI> getResources() throws ZException {
		List<URI> resources = new LinkedList<URI>();
		FileStatus[] files;
		try {
			files = fs.listStatus(dir);
		} catch (IOException e) {
			throw new ZException(e);
		}		
		if(files!=null){
			for(FileStatus status : files){
				Path f = status.getPath();
				if(f.getName().startsWith(".")){  // ignore hidden file
					continue;
				} else if(f.getName().startsWith(name+"_")==false){
					continue;
				} else if(f.getName().equals(name+".erb")){
					continue;
				} else {
					resources.add(f.toUri());
				}
				
			}
		}
		if(prev()!=null){
			resources.addAll(prev().getResources());
		}
		return resources;
	}

	@Override
	public void clean() throws ZException {
		if(prev()!=null){
			prev().clean();
		}		
	}
}
