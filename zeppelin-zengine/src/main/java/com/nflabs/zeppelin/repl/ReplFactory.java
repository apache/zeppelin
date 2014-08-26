package com.nflabs.zeppelin.repl;


import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public class ReplFactory {
	Logger logger = LoggerFactory.getLogger(ReplFactory.class);
	
	private ZeppelinConfiguration conf;
	Map<String, String> replNameClassMap = new HashMap<String, String>();
	String defaultReplName;

	public ReplFactory(ZeppelinConfiguration conf){
		this.conf = conf;
		String replsConf = conf.getString(ConfVars.ZEPPELIN_REPLS);
		String[] confs = replsConf.split(",");
		for(String c : confs) {
			String [] nameAndClass = c.split(":");
			replNameClassMap.put(nameAndClass[0], nameAndClass[1]);
			if(defaultReplName==null){
				defaultReplName = nameAndClass[0];
			}
		}
	}
	
	public String getDefaultReplName(){
		return defaultReplName;
	}
	
	public Repl createRepl(String replName, Properties properties) {
		String className = replNameClassMap.get(replName!=null ? replName : defaultReplName);
		logger.info("find repl class {} = {}", replName, className);
		if(className==null) {
			throw new RuntimeException("Configuration not found for "+replName);
		} 
		return createRepl(replName, className, properties);
	}
	
	public Repl createRepl(String dirName, String className, Properties property) {
		logger.info("Create repl {} from {}", className, dirName);
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();

		try {
			logger.info("Reading "+conf.getString(ConfVars.ZEPPELIN_REPL_DIR)+"/"+dirName);
			File path = new File(conf.getString(ConfVars.ZEPPELIN_REPL_DIR)+"/"+dirName);
			URL [] urls = recursiveBuildLibList(path);
			URLClassLoader cl = new URLClassLoader(urls, oldcl);
			Thread.currentThread().setContextClassLoader(cl);

			Class<Repl> replClass = (Class<Repl>) cl.loadClass(className);
			Constructor<Repl> constructor = replClass.getConstructor(new Class []{Properties.class});
			Repl repl = constructor.newInstance(property);
			return new ClassloaderRepl(repl, cl, property);
		} catch (SecurityException e) {
			throw new ReplException(e);
		} catch (NoSuchMethodException e) {
			throw new ReplException(e);
		} catch (IllegalArgumentException e) {
			throw new ReplException(e);
		} catch (InstantiationException e) {
			throw new ReplException(e);
		} catch (IllegalAccessException e) {
			throw new ReplException(e);
		} catch (InvocationTargetException e) {
			throw new ReplException(e);
		} catch (ClassNotFoundException e) {
			throw new ReplException(e);
		} catch (MalformedURLException e) {
			throw new ReplException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);	
		}
	}
	
	private URL [] recursiveBuildLibList(File path) throws MalformedURLException{
		URL [] urls = new URL[0];
		if (path==null || path.exists()==false){ 
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
}
