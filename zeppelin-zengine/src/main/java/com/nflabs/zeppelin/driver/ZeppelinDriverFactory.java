package com.nflabs.zeppelin.driver;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ZeppelinDriverFactory {
	Logger logger = Logger.getLogger(ZeppelinDriverFactory.class);
	
	Map<String, URLClassLoader> classLoaders = new HashMap<String, URLClassLoader>();
	Map<String, URI> uris = new HashMap<String, URI>();
	String defaultDriverName = null;

	private ZeppelinConfiguration conf;
	
	public ZeppelinDriverFactory(ZeppelinConfiguration conf, String driverRootDir, URI [] uriList) throws ZeppelinDriverException{
		if (driverRootDir==null || uriList==null) {
			return;
		}
		
		this.conf = conf;
		
		File root = new File(driverRootDir);
		File[] drivers = root.listFiles();
		if (drivers!=null) {
			for (File d : drivers) {
				logger.info("Load driver "+d.getName()+" from "+d.getAbsolutePath());;
				classLoaders.put(d.getName(), loadLibrary(d));
			}
		}
		
		if (uriList!=null) {
			for (URI uri : uriList) {
				try {
					uris.put(uri.getScheme(), new URI(uri.getSchemeSpecificPart()));
					if (defaultDriverName == null) {
						defaultDriverName = uri.getScheme();
					}
				} catch (URISyntaxException e) {
					throw new ZeppelinDriverException(e);
				}
			}
		}
	}
	
	public Collection<String> getAllConfigurationNames(){
		return uris.keySet();
	}
	
	public String getDefaultConfigurationName(){
		return defaultDriverName;
	}
	
	private URLClassLoader loadLibrary(File path){
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		URL[] urls;
		try {
			urls = recursiveBuildLibList(path);

			URLClassLoader cl = new URLClassLoader(urls, oldcl);
			Thread.currentThread().setContextClassLoader(cl);
			return cl;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);	
		}
		return null;
	}
	
	private URL [] recursiveBuildLibList(File path) throws MalformedURLException{
		URL [] urls = new URL[0];
		if (path.exists()==false){ 
			return urls;
		} else if (path.getName().startsWith(".")) {
			return urls;
		} else if (path.isDirectory()) {
			File[] files = path.listFiles();			
			if (files!=null) {				
				for (File f : files) {
					urls = (URL[]) ArrayUtils.addAll(urls, recursiveBuildLibList(f));
				}
			}
			return urls;
		} else {
			return new URL[]{path.toURI().toURL()};
		}
	}
	
	/**
	 * Create new driver instance
	 * @param name friendly name of driver configuration
	 * @return driver instance
	 * @throws ZeppelinDriverException
	 */
	public ZeppelinDriver createDriver(String name) throws ZeppelinDriverException{
		URI uri = uris.get(name);
		try {
			return createDriverByUri(uri);
		} catch (Exception e) {
			throw new ZeppelinDriverException(e);
		}
	}
	
	/**
	 * Create new driver instance
	 * @param uri eg) com.nflabs.zeppelin.hive.driver.HiveDriver:hive://localhost:10000/default
	 * @return
	 * @throws URISyntaxException
	 * @throws ZeppelinDriverException 
	 * @throws ClassNotFoundException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws IllegalArgumentException 
	 * @throws NoSuchMethodException 
	 * @throws SecurityException 
	 */
	private ZeppelinDriver createDriverByUri(URI uri) throws ZeppelinDriverException {
		String driverName = uri.getScheme();
		URI driverUri;
		try {
			driverUri = new URI(uri.getSchemeSpecificPart());
		} catch (URISyntaxException e1) {
			throw new ZeppelinDriverException(e1);
		}
		String driverClassName = driverUri.getScheme();
		
		URLClassLoader cl = classLoaders.get(driverName);
		if (cl==null) {
			throw new ZeppelinDriverException("Can not find driver "+driverName);
		}
		
		Class cls;
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(cl);
			cls = cl.loadClass(driverClassName);
			Constructor<ZeppelinDriver> constructor = cls.getConstructor(new Class []{ZeppelinConfiguration.class, URI.class, ClassLoader.class});
			URI connectionUri = new URI(driverUri.getSchemeSpecificPart());
			if (logger.isDebugEnabled()) {
				logger.debug("Create driver "+driverClassName+"("+connectionUri.toString()+")");
			}
			return constructor.newInstance(conf, connectionUri, cl);	
		} catch (ClassNotFoundException e) {
			throw new ZeppelinDriverException(e);
		} catch (IllegalArgumentException e) {
			throw new ZeppelinDriverException(e);
		} catch (InstantiationException e) {
			throw new ZeppelinDriverException(e);
		} catch (IllegalAccessException e) {
			throw new ZeppelinDriverException(e);
		} catch (InvocationTargetException e) {
			throw new ZeppelinDriverException(e);
		} catch (SecurityException e) {
			throw new ZeppelinDriverException(e);
		} catch (NoSuchMethodException e) {
			throw new ZeppelinDriverException(e);		
		} catch (URISyntaxException e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}
	
}
