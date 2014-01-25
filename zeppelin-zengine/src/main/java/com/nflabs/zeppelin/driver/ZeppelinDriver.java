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
	protected ThreadLocal<ZeppelinConnection> connection = new ThreadLocal<ZeppelinConnection>() {
	    @Override protected ZeppelinConnection initialValue() { //Lazy Init by subClass impl
            return getConnection();
        }
	};

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
        this.connection.get().addResource(resourceLocation);
    }

    public Result query(String query) {
        return this.connection.get().query(query);
    }

    public Result select(String tableName, int maxResult) {
        return this.connection.get().select(tableName, maxResult);
    }

    public Result createTableFromQuery(String name, String query) {
        return this.connection.get().createTableFromQuery(name, query);
    }

    public void dropTable(String name) {
        this.connection.get().dropTable(name);
    }

    public void dropView(String name) {
        this.connection.get().dropView(name);
    }

    public void abort() {
        this.connection.get().abort();
    }

}
