package com.nflabs.zeppelin.zengine.stmt;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.context.ZLocalContextImpl;
import com.nflabs.zeppelin.zengine.context.ZWebContext;

/**
 * L stands for Library(aks UDF). This class load and execute Zeppelin User Defined Functions
 * @author moon
 *
 */
public class L extends Q {
	private String libName;
	
	transient private URI libUri;
	transient private File dir;
	transient private File webDir;
	transient private File erbFile;
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
	 * @param currentDriver 
	 * @throws ZException
	 */
	public L(String libName, String arg) throws ZException{
		super(arg);
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
		
		ZeppelinConfiguration conf = ZeppelinConfiguration.create();		
		String libUri = conf.getString(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO)+"/"+libName;

		// search for library dir
		this.dir = new File(libUri);
		if (dir.exists()==false || dir.isDirectory()==false) {
			throw new ZException("Directory "+dir.getAbsolutePath()+" does not exist");
		}
		this.erbFile = new File(libUri.toString()+"/zql.erb");

		if(erbFile.isFile() == false){
			erbFile = null;
		}
		
		this.webDir = new File(libUri+"/web");	

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
		
		if(hasPrev()){
			// purpose of calling is evaluating erb. because it need to share the same local variable context
			prev().getQuery();
		}

		if(erbFile==null){
			return null;
		}
		
		String q;		
		
		try {
			FileInputStream ins = new FileInputStream(erbFile);
			BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
			
			ZLocalContextImpl zContext = new ZLocalContextImpl( (prev()==null) ? null : prev().name(), name(), query, params);
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
			if (webDir.exists()==false || webDir.isDirectory()==false ) {
				return super.readWebResource(path);
			}
			
			File resourcePath = new File(webDir, path);
			if (resourcePath.isFile()==false) {
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
					FileInputStream ins = new FileInputStream(resourcePath);
					BufferedReader erb = new BufferedReader(new InputStreamReader(ins));					
					q = evalWebTemplate(erb, zWebContext);
					ins.close();
					
					return new ByteArrayInputStream(q.getBytes());
				} catch (IOException e1) {
					throw new ZException(e1);
				}
	
			} else {
				return new FileInputStream(resourcePath);
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
		File resourcePath = new File(webDir, "index.erb");
		if(resourcePath.isFile()==false){
			return super.isWebEnabled();
		} else {
			return true;
		}
	}
	
	/**
	 * Get resources to be required to run this library
	 */
	@Override
	public List<URI> getResources() throws ZException {
		List<URI> resources = new LinkedList<URI>();
		File[] files = dir.listFiles();
		if(files!=null){
			for(File f : files){
				if(f.isFile()==false){
					continue;
				} else if(f.getName().startsWith(".")){  // ignore hidden file
					continue;
				} else if(f.getName().startsWith(libName+"_")==false){
					continue;
				} else if(f.getName().equals("zql.erb")){
					continue;
				} else {
					resources.add(f.toURI());
				}
				
			}
		}
		
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
		
		ZLocalContextImpl zContext = null;
		if(erbFile!=null){
			InputStream ins;
			try {
				ins = new FileInputStream(erbFile);
				BufferedReader erb = new BufferedReader(new InputStreamReader(ins));
				
				zContext = new ZLocalContextImpl( (prev()==null) ? null : prev().name(), name(), query, params);
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
			File resourcePath = new File(webDir, "index.erb");
			if(resourcePath.isFile()==true){
					
				zWebContext = new ZWebContext(params, null);
				
				FileInputStream ins = new FileInputStream(resourcePath);
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
