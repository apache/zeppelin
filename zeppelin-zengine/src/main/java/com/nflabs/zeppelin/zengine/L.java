package com.nflabs.zeppelin.zengine;


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

/**
 * L stands for Library
 * @author moon
 *
 */
public class L extends Q{
	Logger logger = Logger.getLogger(L.class);
	
	private String libName;
	Map<String, Object> params = new HashMap<String, Object>();
	
	private URI libUri;
	private Path dir;
	private Path erbFile;
	private FileSystem fs;
	
	public L(String libName) throws ZException{
		this(libName, null);
	}
	public L(String libName, String query) throws ZException{
		super(query);
		
		this.libName = libName;
		fs = fs();
		
		try {
			if(libName.indexOf(":/")>0){
				libUri = new URI(libName);
			} else {
				libUri = new URI(conf().getString(ConfVars.ZEPPELIN_LIBRARY_DIR)+"/"+libName);	
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
	

	@Override
	public String getQuery() throws ZException {
		ScriptEngine engine = getRubyScriptEngine();

		StringBuffer rubyScript = new StringBuffer();
		SimpleBindings bindings = new SimpleBindings();
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

		rubyScript.append("require 'erb'\n");
		rubyScript.append(
				"class Zeppelin\n"+
				"  def initialize(a, p)\n"+
				"    @a = a\n"+
				"    @p = {}\n"+
				"    p.each{|key,value|@p[key]=value}\n"+
				"  end\n"+
				"  def param(key)\n"+
				"    @p[key]\n"+
				"  end\n"+
				"  def "+Q.PREV_VAR_NAME+"\n"+
				"    @a\n"+
				"  end\n"+				
                "end\n");		
		
		// add arg local var
		bindings.put(Q.PREV_VAR_NAME, query);		
		
		// add param local var
		bindings.put("params", params);
		
		// create zeppelin context
		rubyScript.append("z = Zeppelin.new($"+Q.PREV_VAR_NAME+", $params)\n");
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
        	logger.debug("rubyScript to run : \n"+rubyScript.toString());
			engine.eval(rubyScript.toString(), bindings);
		} catch (ScriptException e) {
			throw new ZException(e);
		}
        
        String q = (String) engine.getBindings(ScriptContext.ENGINE_SCOPE).get("e");
        
		String tableCreation = null;
		if(name()==null){
			tableCreation = "";
		} else {
			if(isCache()){
				tableCreation = "CREATE TABLE "+name()+" AS ";
			} else {
				tableCreation = "CREATE VIEW "+name()+" AS ";
			}
		}
		return tableCreation + q;
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
				} else if(f.getName().startsWith(libName+"_")==false){
					continue;
				} else if(f.getName().equals(libName+".erb")){
					continue;
				} else {
					resources.add(f.toUri());
				}
				
			}
		}
		
		resources.addAll(super.getResources());
		return resources;
	}

	@Override
	public String getCleanQuery() throws ZException {
		return super.getCleanQuery();
	}
}
