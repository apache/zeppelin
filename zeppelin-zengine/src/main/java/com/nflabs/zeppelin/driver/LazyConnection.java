package com.nflabs.zeppelin.driver;

import java.net.URI;

import com.nflabs.zeppelin.result.Result;


/**
 * Laze initialized driver connection
 *
 */
public class LazyConnection implements ZeppelinConnection {

	private String driverUriConfName;
	private transient ZeppelinConnection connection;
	private transient boolean initialized = false;

	/**
	 * Create lazy initialized driver connection 
	 * @param driverUriConfName driver configuration name. null means default driver configuration
	 */
	public LazyConnection(String driverUriConfName){
		this.driverUriConfName = driverUriConfName;
	}
	
	/**
	 * Initialize lazyConnection. 
	 * @param driverFactory 
	 */
	public void initialize(ZeppelinDriverFactory driverFactory){
		if (connection!=null ) return;
		if (initialized == true) return;
		
		ZeppelinDriver driver;
		String confName;
		if (driverUriConfName == null ){
			confName = driverFactory.getDefaultConfigurationName();
		} else {
			confName = driverUriConfName;
		}
		
		driver = driverFactory.getDriver(confName);
		connection = driver.getConnection(driverFactory.getUrlFromConfiguration(confName));
		
		initialized = true;
	}

	@Override
	public boolean isConnected() throws ZeppelinDriverException {
		if (initialized==false) return false;
		return connection.isConnected();
	}

	@Override
	public void close() throws ZeppelinDriverException {
		if(isConnected()==false) return;
		connection.close();		
	}

	@Override
	public void abort() throws ZeppelinDriverException {
		if(isConnected()==false) return;
		connection.abort();
	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
		return connection.query(query);
	}

	@Override
	public Result addResource(URI resourceLocation)
			throws ZeppelinDriverException {
		return connection.addResource(resourceLocation);
	}

	@Override
	public Result select(String tableName, int limit)
			throws ZeppelinDriverException {
		return connection.select(tableName, limit);
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
		return connection.createViewFromQuery(viewName, query);
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
		return connection.createTableFromQuery(tableName, query);
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		return connection.dropView(viewName);
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
		return connection.dropTable(tableName);
	}
	
}
