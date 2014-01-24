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
	 * Get zeppelin connection
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public abstract ZeppelinConnection getConnection() throws ZeppelinDriverException;
	
	/**
	 * Initialize driver
	 * @throws ZeppelinDriverException
	 */
	@Deprecated
	public abstract void init() throws ZeppelinDriverException;
	
	/**
	 * Destroy the driver
	 * @throws ZeppelinDriverException
	 */
	@Deprecated
	public abstract void shutdown() throws ZeppelinDriverException;
	
	
    public void addResource(URI resourceLocation) {
        this.connection.addResource(resourceLocation);
    }

    public Result query(String query) {
        return this.connection.query(query);
    }

    public Result select(String tableName, int maxResult) {
        return this.connection.select(tableName, maxResult);
    }

    public Result createTableFromQuery(String name, String query) {
        return this.connection.createTableFromQuery(name, query);
    }

    public void dropTable(String name) {
        this.connection.dropTable(name);
    }

    public void dropView(String name) {
        this.connection.dropView(name);
    }

    public void abort() {
        this.connection.abort();
    }

}
