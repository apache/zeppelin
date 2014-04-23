package com.nflabs.zeppelin.driver;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

/**
 * Zeppelin driver is physical layer abstraction.
 * 
 * Driver creates ZeppelinConnections. Each connections maps to each physical connection. i.e Socket for JDBC to Hive or Spark or PrestoDB.
 * connection will used when executing logical plan(s).
 * 
 */
public abstract class ZeppelinDriver {    
	private ClassLoader classLoader;
	private ZeppelinConfiguration conf;

	
	/**
	 * Constructor of Driver. all subclass should implement this.
	 */
	public ZeppelinDriver(){
		
	}
	
	/**
	 * Driver comes with it's own classloader.
	 * Classloader is set by DriverFactory, right after it is created. 
	 * @param cl
	 */
	public void setClassLoader(ClassLoader cl){
		this.classLoader = cl;
	}

	public void setConf(ZeppelinConfiguration conf) {
		this.conf = conf;
	}

	public ZeppelinConfiguration getConf() {
		return conf;
	}

	/**
	 * Initialize driver. automatically called after setClassLoader when driver created.
	 */
	protected abstract void init();

	
	
	/**
	 * Check if the driver can handle provided connection url or not.
	 * @param url
	 * @return
	 */
	public abstract boolean acceptsURL(String url);
	
	/**
	 * Creates actual connection to the backed system
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	protected abstract ZeppelinConnection createConnection(String url) throws ZeppelinDriverException;

	/**
	 * Create connection with given classloader
	 * @param url
	 * @return
	 */
	public ZeppelinConnection getConnection(String url){
		ClassLoader cl = classLoader;
		if (classLoader == null ) {
			cl = Thread.currentThread().getContextClassLoader();
		}
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return new ClassLoaderConnection(createConnection(url), cl);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}
}
