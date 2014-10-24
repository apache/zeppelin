package com.nflabs.zeppelin.interpreter;


import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public class InterpreterFactory {
	Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);
	
	private Map<String, Object> share = Collections.synchronizedMap(new HashMap<String, Object>());
	private Map<String, URLClassLoader> cleanCl = Collections.synchronizedMap(new HashMap<String, URLClassLoader>());
	
	private ZeppelinConfiguration conf;
	String [] interpreterClassList;
	String defaultInterpreterName;

	public InterpreterFactory(ZeppelinConfiguration conf){
		this.conf = conf;
		String replsConf = conf.getString(ConfVars.ZEPPELIN_INTERPRETERS);
		interpreterClassList = replsConf.split(",");
		
		init();
	}
	
	
	private void init(){
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		
		File [] interpreterDirs = new File(conf.getInterpreterDir()).listFiles();
		if(interpreterDirs!=null) {
			for (File path : interpreterDirs) {				
				logger.info("Reading "+path.getAbsolutePath());
				URL[] urls = null;
				try {
					urls = recursiveBuildLibList(path);
				} catch (MalformedURLException e1) {
					logger.error("Can't load jars ", e1);
				}
				URLClassLoader ccl = new URLClassLoader(urls, oldcl);
				
				for(String className : interpreterClassList){
					try {
						Class.forName(className, true, ccl);
						Set<String> keys = Interpreter.registeredInterpreters.keySet();
						for (String intName : keys) {
							if(className.equals(Interpreter.registeredInterpreters.get(intName))){
								logger.info("Interpreter "+intName+" found. class="+className);
								cleanCl.put(intName, ccl);
							
								if (className.equals(interpreterClassList[0])) {
									defaultInterpreterName = intName;
								}
							}
						}
					} catch (ClassNotFoundException e) {
						// nothing to do
					}
				}
			}
		}		
	}
	
	
	public String getDefaultInterpreterName(){
		return defaultInterpreterName;
	}
	
	public Interpreter createRepl(String replName, Properties properties) {
		String className = Interpreter.registeredInterpreters.get(replName!=null ? replName : defaultInterpreterName);
		logger.info("find repl class {} = {}", replName, className);
		if(className==null) {
			throw new RuntimeException("Configuration not found for "+replName);
		} 
		return createRepl(replName, className, properties);
	}
	
	public Interpreter createRepl(String dirName, String className, Properties property) {
		logger.info("Create repl {} from {}", className, dirName);
		
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();		
		try {

			URLClassLoader ccl = cleanCl.get(dirName);
			if(ccl==null) {
				// classloader fallback
				ccl = URLClassLoader.newInstance(new URL[]{}, oldcl);
			}
			
			boolean separateCL = true;
			try{ // check if server's classloader has driver already. 
				Class cls = this.getClass().forName(className);
				if(cls!=null) separateCL = false;				
			} catch(Exception e) {
				// nothing to do.
			}
			
			URLClassLoader cl;
			
			if(separateCL==true) {
				cl = URLClassLoader.newInstance(new URL[]{}, ccl);
			} else {
				cl = (URLClassLoader) ccl;
			}
			Thread.currentThread().setContextClassLoader(cl);
			
			Class<Interpreter> replClass = (Class<Interpreter>) cl.loadClass(className);
			Constructor<Interpreter> constructor = replClass.getConstructor(new Class []{Properties.class});
			Interpreter repl = constructor.newInstance(property);
            if(conf.containsKey("args")) {
                property.put("args", conf.getProperty("args"));
            }
			property.put("share", share);
			property.put("classloaderUrls", ccl.getURLs());
			return new ClassloaderInterpreter(repl, cl, property);
		} catch (SecurityException e) {
			throw new InterpreterException(e);
		} catch (NoSuchMethodException e) {
			throw new InterpreterException(e);
		} catch (IllegalArgumentException e) {
			throw new InterpreterException(e);
		} catch (InstantiationException e) {
			throw new InterpreterException(e);
		} catch (IllegalAccessException e) {
			throw new InterpreterException(e);
		} catch (InvocationTargetException e) {
			throw new InterpreterException(e);
		} catch (ClassNotFoundException e) {
			throw new InterpreterException(e);
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
