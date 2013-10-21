package com.nflabs.zeppelin.zengine;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.datanucleus.util.StringUtils;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

/**
 * L stands for Library
 * @author moon
 *
 */
public class L extends Q{
	Logger logger = Logger.getLogger(L.class);
	
	private String libName;

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
		

	

	@Override
	public String getQuery() throws ZException {
		
		String q;
		try {
			FSDataInputStream ins = fs.open(erbFile);
			BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
			q = getQuery(erb, (prev()==null) ? null : prev().name(), null, params);
			ins.close();
		} catch (IOException e1) {
			throw new ZException(e1);
		}

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
