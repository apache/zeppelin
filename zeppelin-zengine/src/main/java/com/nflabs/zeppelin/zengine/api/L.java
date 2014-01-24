package com.nflabs.zeppelin.zengine.api;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZContext;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZWebContext;
import com.nflabs.zeppelin.zengine.Zengine;

/**
 * L stands for Library(aks UDF). This class load and execute Zeppelin User Defined Functions
 * @author moon
 *
 */
public class L extends Q {
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
	public L(String libName, Zengine z, ZeppelinDriver drv) throws ZException{
		this(libName, null, z, drv);
	}
	/**
	 * @param libName library name to laod and run
	 * @param arg
	 * @param currentDriver 
	 * @throws ZException
	 */
	public L(String libName, String arg, Zengine z, ZeppelinDriver driver) throws ZException{
		super(arg, z, driver);
		this.libName = libName;
		initialize();
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(L.class);
	}
	
	protected void initialize() throws ZException{
		super.initialize();
		if(initialized==true){
			return ;
		}
		
		if(libName==null) return;

		fs = zen.fs();
		
		try {
			if(libName.indexOf(":/")>0){
				libUri = new URI(libName);
			} else {
				libUri = new URI(zen.getConf().getString(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO)+"/"+libName);	
			}
		} catch (URISyntaxException e1) {
			throw new ZException(e1);
		}
		
		try {		
			// search for library dir
			this.dir = new Path(libUri);
			if(!fs.exists(dir) || !fs.getFileStatus(dir).isDir()){
				throw new ZException("Directory "+dir.toUri()+" does not exist");
			}
			this.erbFile = new Path(libUri.toString()+"/zql.erb");

			if(fs.isFile(erbFile) == false){
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
		
		if(path==null || path.compareTo("/")==0){
			path = "index.erb";
		}
		try {
			if(fs.getFileStatus(webDir).isDir() == false){
				return super.readWebResource(path);
			}
			
			Path resourcePath = new Path(webDir.toUri()+"/"+path);
			if (fs.isFile(resourcePath) == false) {
				return null;
			}
			
			if(resourcePath.getName().endsWith(".erb")){
				String q;
				try {					
					ZWebContext zWebContext = null;
					try{
						zWebContext = new ZWebContext(params, result());
					} catch(ZException e){
					    //TODO explain
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
				return super.isWebEnabled();
			} else {
				return true;
			}
		} catch (IOException e) {
			logger().error("Error while reading file "+resourcePath, e);
			// fall back to default view
			return super.isWebEnabled();
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
					} else if(f.getName().equals("zql.erb")){
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
			    assert false; // should never reach here
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
		
		ZContext zContext = null;
		if(erbFile!=null){
			FSDataInputStream ins;
			try {
				ins = fs.open(erbFile);
				BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
				
				zContext = new ZContext( (prev()==null) ? null : prev().name(), name(), query, params);			
				getQuery(erb, zContext);
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
				evalWebTemplate(erb, zWebContext);
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
