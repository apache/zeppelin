package com.nflabs.zeppelin.driver;

import java.net.URI;
import java.net.URLClassLoader;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.result.Result;

/**
 * Zeppelin driver is physical layer abstraction.
 * 
 * Each sub-class manages actual connections to backend systems:
 * i.e Socket for JDBC to Hive or Spark or PrestoDB.
 * 
 * Overall it might be statefull, as the connection underneath could or could not already exist.
 * 
 * In current impl each thread i.e ZQLJob (who uses Driver to .execute() Z's) has it's own copy of Connection
 * per-thread so Driver becomes stateless.
 * 
 * Open : connection opened by Lazy Initialization - will be created as soon as first request to .get() it comes.
 * Close: so far connection is closed ONLY on driver shutdown
 */
public abstract class ZeppelinDriver {    
	private ClassLoader classLoader;	
	
	public void setClassLoader(ClassLoader cl){
		this.classLoader = cl;
	}
	
	/**
	 * Creates actual connection to the backed system
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	protected abstract ZeppelinConnection createConnection(URI uri) throws ZeppelinDriverException;

	public ZeppelinConnection getConnection(URI uri){
		return new ClassLoaderConnection(createConnection(uri), classLoader);
	}
	
	public abstract boolean acceptsURL(String url);
}
