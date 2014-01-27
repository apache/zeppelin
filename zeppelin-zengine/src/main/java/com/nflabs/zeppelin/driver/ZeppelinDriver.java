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
    protected ZeppelinConfiguration conf;
    private URI uri;
	private ClassLoader classLoader;
	private boolean initialized = false;
	private boolean closed = false;
	
    protected ThreadLocal<ZeppelinConnection> connection = new ThreadLocal<ZeppelinConnection>() {
    	
    	
	    @Override protected ZeppelinConnection initialValue() { //Lazy Init by subClass impl
	    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(classLoader);
			try {
				return getConnection();
			} catch(ZeppelinDriverException e){
				throw e;
			} catch(Exception e) {
				throw new ZeppelinDriverException(e);
			} finally {
				initialized = true;
				Thread.currentThread().setContextClassLoader(oldcl);
			}
        }
	};


	/**
	 * Constructor
	 * @param conf zeppelin configuration
	 * @param uri driver connection uri
	 * @throws ZeppelinDriverException 
	 */
	public ZeppelinDriver(ZeppelinConfiguration conf, URI uri, ClassLoader classLoader){
		this.conf = conf;
		this.uri = uri;
		this.classLoader = classLoader;
	}
	
	/**
	 * Get zeppelin configuration
	 * @return
	 */
	public ZeppelinConfiguration getConf(){
		return conf;
	}
	
	public URI getUri(){
		return uri;
	}
	
	/**
	 * Creates actual connection to the backed system
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	protected abstract ZeppelinConnection getConnection() throws ZeppelinDriverException;

    public boolean isConnected() {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			return this.connection.get().isConnected();
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}
    }
    
    public void addResource(URI resourceLocation) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			this.connection.get().addResource(resourceLocation);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}
    }

    public Result query(String query) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
	        return this.connection.get().query(query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}    	
    }

    public Result select(String tableName, int maxResult) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			return this.connection.get().select(tableName, maxResult);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }

    public Result createTableFromQuery(String name, String query) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			return this.connection.get().createTableFromQuery(name, query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }
    
    public Result createViewFromQuery(String name, String query) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
	        return this.connection.get().createViewFromQuery(name, query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }

    public void dropTable(String name) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
	        this.connection.get().dropTable(name);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }

    public void dropView(String name) {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
		    this.connection.get().dropView(name);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }

    public void abort() {
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
	        this.connection.get().abort();
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }
    
    public void close() {
    	if(initialized==false) return;
    	if(closed==true) return;
    	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);		
		try {
	        this.connection.get().close();
	        closed = true;
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		} 
    }
}
