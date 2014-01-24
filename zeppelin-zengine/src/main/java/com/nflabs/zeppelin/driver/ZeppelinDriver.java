package com.nflabs.zeppelin.driver;

import java.net.URI;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.result.Result;

/**
 * Zeppelin driver is physical layer abstraction.
 * 
 * Each sub-class manages actual connections to backend systems:
 * i.e Socket for JDBC to Hive or Spark or PrestoDB.
 * 
 * It is stateful, as the connection underneath could or could not already exist.
 * 
 */
public abstract class ZeppelinDriver {
    protected ZeppelinConfiguration conf;
	protected ZeppelinConnection connection;

	/**
	 * Constructor
	 * @param conf zeppelin configuration
	 * @throws ZeppelinDriverException 
	 */
	public ZeppelinDriver(ZeppelinConfiguration conf){
		this.conf = conf;
	}
	
	/**
	 * Get zeppelin configuration
	 * @return
	 */
	public ZeppelinConfiguration getConf(){
		return conf;
	}

	/**
	 * Creates actual connection to the backed system
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	protected abstract ZeppelinConnection getConnection() throws ZeppelinDriverException;
	
	/**
	 * Initialize driver.
	 * It's dependencies become available in separate separate classloader.
	 * @throws ZeppelinDriverException
	 */
	public abstract void init() throws ZeppelinDriverException;
	
	/**
	 * Destroy the driver
	 * @throws ZeppelinDriverException
	 */
	public abstract void destroy() throws ZeppelinDriverException;
		
    public void addResource(URI resourceLocation) {
        lazyCheckForConnection();
        this.connection.addResource(resourceLocation);
    }

    public Result query(String query) {
        lazyCheckForConnection();
        return this.connection.query(query);
    }

    public Result select(String tableName, int maxResult) {
        lazyCheckForConnection();
        return this.connection.select(tableName, maxResult);
    }

    public Result createTableFromQuery(String name, String query) {
        lazyCheckForConnection();
        return this.connection.createTableFromQuery(name, query);
    }

    public void dropTable(String name) {
        lazyCheckForConnection();
        this.connection.dropTable(name);
    }

    public void dropView(String name) {
        lazyCheckForConnection();
        this.connection.dropView(name);
    }

    public void abort() {
        lazyCheckForConnection();
        this.connection.abort();
    }


    /**
     * Lazy initialization of actual connection
     */
    private synchronized void lazyCheckForConnection() {
        if (this.connection != null) { return; }
        this.connection = getConnection();
    }

}
