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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ZeppelinDriverFactory {
	Logger logger = Logger.getLogger(ZeppelinDriverFactory.class);
	
	static List<ZeppelinDriver> drivers = new LinkedList<ZeppelinDriver>();
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
				loadLibrary(d);
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
	
	public URI getUriFromConfiguration(String configurationName){
		return uris.get(configurationName);
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
	public ZeppelinDriver getDriver(String name) throws ZeppelinDriverException{
		URI uri = uris.get(name);
		try {
			return getDriver(uri);
		} catch (Exception e) {
			throw new ZeppelinDriverException(e);
		}
	}
	
	/**
	 * Create new driver instance
	 * @param uri eg) hive://localhost:10000/default
	 * @return
	 */
	private ZeppelinDriver getDriver(URI uri) throws ZeppelinDriverException {
		for (ZeppelinDriver d : drivers) {
			if(d.acceptsURL(uri.toString())){
				return d;
			}
		}
		throw new ZeppelinDriverException("Can't find driver for "+uri.toString());
	}
	
	public static void registerDriver(ZeppelinDriver driver) {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		driver.setClassLoader(cl);
		drivers.add(driver);
	}
	
}
