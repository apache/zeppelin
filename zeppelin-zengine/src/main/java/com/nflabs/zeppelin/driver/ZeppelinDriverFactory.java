package com.nflabs.zeppelin.driver;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

public class ZeppelinDriverFactory {
	Map<String, URLClassLoader> classLoaders = new HashMap<String, URLClassLoader>();
	Map<String, URI> uris = new HashMap<String, URI>();

	
	public ZeppelinDriverFactory(String driverRootDir, URI [] uriList) throws ZeppelinDriverException{		
		File root = new File(driverRootDir);
		File[] drivers = root.listFiles();
		if (drivers!=null) {
			for (File d : drivers) {
				classLoaders.put(d.getName(), loadLibrary(d));
			}
		}
		
		if (uriList!=null) {
			for (URI uri : uriList) {
				try {
					uris.put(uri.getScheme(), new URI(uri.getSchemeSpecificPart()));
				} catch (URISyntaxException e) {
					throw new ZeppelinDriverException(e);
				}
			}
		}
	}
	
	private URLClassLoader loadLibrary(File path){
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		URL[] urls;
		try {
			urls = recursiveBuildLibList(path);
			URLClassLoader cl = new URLClassLoader(urls, oldcl);
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
					ArrayUtils.addAll(urls, recursiveBuildLibList(f));
				}
			}
			return urls;
		} else {
			return new URL[]{path.toURI().toURL()};
		}
	}
	
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
	private ZeppelinDriver createDriverByUri(URI uri) throws URISyntaxException, ZeppelinDriverException, ClassNotFoundException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException{
		String driverClassName = uri.getScheme();
		URI driverUri = new URI(uri.getSchemeSpecificPart());
		String driverName = driverUri.getScheme();
		
		URLClassLoader cl = classLoaders.get(driverName);
		if (cl==null) {
			throw new ZeppelinDriverException("Can not find driver "+driverName);
		}
		
		Class cls = cl.loadClass(driverClassName);
		Constructor<ZeppelinDriver> constructor = cls.getConstructor(new Class []{URI.class});
		return constructor.newInstance(driverUri);	
	}
	
}
