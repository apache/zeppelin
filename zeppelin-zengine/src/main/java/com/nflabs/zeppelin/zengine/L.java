package com.nflabs.zeppelin.zengine;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.result.Result;

/**
 * L stands for Library(aks UDF). This class load and execute Zeppelin User Defined Functions
 * @author moon
 *
 */
public class L extends Q{
	private String libName;
	
	transient private URI libUri;
	transient private Path dir;
	transient private Path webDir;
	transient private Path erbFile;
	transient private FileSystem fs;
	transient private boolean initialized = false;
	
	/**
	 * @param libName library name to load and run
	 * @throws ZException
	 */
	public L(String libName) throws ZException{
		this(libName, null);
	}
	/**
	 * @param libName library name to laod and run
	 * @param arg
	 * @throws ZException
	 */
	public L(String libName, String arg) throws ZException{
		super(arg);
		this.libName = libName;
		initialize();
	}
	
	private Logger logger(){
		return Logger.getLogger(L.class);
	}
	
	protected void initialize() throws ZException{
		super.initialize();
		if(initialized==true){
			return ;
		}
		
		if(libName==null) return;

		fs = fs();
		
		try {
			if(libName.indexOf(":/")>0){
				libUri = new URI(libName);
			} else {
				libUri = new URI(conf().getString(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO)+"/"+libName);	
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
				erbFile = null;
			}
			
			this.webDir = new Path(libUri+"/web");
		} catch (IOException e) {
			throw new ZException(e);
		}		
		
		initialized = true;
	}

	/**
	 * Get query to be executed.
	 * Each library has query template file. and returns evaluated value.
	 * 
	 * @return query to be executed
	 */
	@Override
	public String getQuery() throws ZException {
		initialize();		
		
		if(erbFile==null){
			return null;
		}
		
		String q;		
		
		try {
			FSDataInputStream ins = fs.open(erbFile);
			BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
			
			ZContext zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);			
			q = getQuery(erb, zContext);
			ins.close();
		} catch (IOException e1) {
			throw new ZException(e1);
		}

		return q;
	}
	
	/**
	 * access web resources
	 * 
	 * 
	 * @param path related path from this libraries 'web' dir
	 * @return
	 * @throws ZException 
	 */
	public InputStream readWebResource(String path) throws ZException{
		initialize();
		FileStatus[] files;
		
		if(path==null || path.compareTo("/")==0){
			path = "index.erb";
		}
		
		try {
			if(fs.isDirectory(webDir)==false) return null;
			
			Path resourcePath = new Path(webDir.toUri()+"/"+path);
			if(fs.isFile(resourcePath)==false){
				return null;
			}
			
			if(resourcePath.getName().endsWith(".erb")){
				String q;
				try {					
					ZWebContext zWebContext = null;
					try{
						zWebContext = new ZWebContext(params, result());
					} catch(ZException e){						
					}
					FSDataInputStream ins = fs.open(resourcePath);
					BufferedReader erb = new BufferedReader(new InputStreamReader(ins));					
					q = evalWebTemplate(erb, zWebContext);
					ins.close();
					
					return new ByteArrayInputStream(q.getBytes());
				} catch (IOException e1) {
					throw new ZException(e1);
				}
	
			} else {
				return fs.open(resourcePath);
			}
		} catch (IOException e) {
			throw new ZException(e);
		}
	}

	/**
	 * Check if this library has web capabilities.
	 * 
	 */
	@Override
	public boolean isWebEnabled(){
		Path resourcePath = new Path(webDir.toUri()+"/index.erb");
		try {
			if(fs.isFile(resourcePath)==false){
				return false;
			} else {
				return true;
			}
		} catch (IOException e) {
			logger().error("Error while reading file "+resourcePath, e);
			return false;
		}
	}
	
	/**
	 * Get resources to be required to run this library
	 */
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
				try {
					if(fs.isFile(f)==false){
						continue;
					} else if(f.getName().startsWith(".")){  // ignore hidden file
						continue;
					} else if(f.getName().startsWith(libName+"_")==false){
						continue;
					} else if(f.getName().equals(libName+".erb")){
						continue;
					} else {
						resources.add(f.toUri());
					}
				} catch (IOException e) {
					logger().error("Error while reading library resource "+f.toString(), e);
				}
				
			}
		}
		
		resources.addAll(super.getResources());
		return resources;
	}

	@Override
	public String getReleaseQuery() throws ZException {
		return super.getReleaseQuery();
	}
	
	@Override
	public Z pipe(Z z){
		if(erbFile==null){
			if(prev()!=null){
				withName(z.name());
			} else {
				// should not be reach here
			}
			setNext(z);
			z.setPrev(this);
			
			return z;
		} else {
			return super.pipe(z);
		}
	}
	
	@Override
	protected Map<String, ParamInfo> extractParams() throws ZException {
		initialize();
		
		Map<String, ParamInfo> paramInfos = new HashMap<String, ParamInfo>();
		
		String q;
		ZContext zContext = null;
		if(erbFile!=null){
			FSDataInputStream ins;
			try {
				ins = fs.open(erbFile);
				BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
				
				zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);			
				q = getQuery(erb, zContext);
				ins.close();
			} catch (IOException e1) {
				logger().debug("dry run error", e1);
			} catch (ZException e) {
				logger().debug("dry run error", e);
			}
		}
		
		ZWebContext zWebContext = null;

		try {
			Path resourcePath = new Path(webDir.toUri()+"/index.erb");
			if(fs.isFile(resourcePath)==true){
					
				zWebContext = new ZWebContext(params, null);
				
				FSDataInputStream ins = fs.open(resourcePath);
				BufferedReader erb = new BufferedReader(new InputStreamReader(ins));					
				q = evalWebTemplate(erb, zWebContext);
				ins.close();
			}				
		} catch (IOException e) {
			logger().debug("dry run error", e);
		} catch (ZException e) {
			logger().debug("dry run error", e);
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
