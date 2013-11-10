package com.nflabs.zeppelin.driver;

import java.net.URI;

import com.nflabs.zeppelin.result.Result;

public interface ZeppelinConnection {
	public boolean isConnected() throws ZeppelinDriverException;
	public void close() throws ZeppelinDriverException;
	
	/**
	 * Cancel currently running operation
	 */
	public void abort() throws ZeppelinDriverException;
	
	/**
	 * Add sql to execute
	 * @param query
	 * @throws ZeppelinDriverException 
	 */
	public Result query(String query) throws ZeppelinDriverException;
	
	/**
	 * For example, hive, resource can be added by 'add JAR', 'add FILE'. 
	 * @param resourceLocation
	 * @throws ZeppelinDriverException 
	 */
	public Result addResource(URI resourceLocation) throws ZeppelinDriverException;
	
	/**
	 * Select data from the table with limit 
	 * @param tableName
	 * @param limit
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public Result select(String tableName, int limit) throws ZeppelinDriverException;
	
	/**
	 * Create view from query
	 * @param viewName
	 * @param query
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public Result createViewFromQuery(String viewName, String query) throws ZeppelinDriverException;
	
	/**
	 * Create table from query
	 * @param tableName
	 * @param query
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public Result createTableFromQuery(String tableName, String query) throws ZeppelinDriverException;
	
	/**
	 * Drop view
	 * @param viewName
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public Result dropView(String viewName) throws ZeppelinDriverException;
	
	/**
	 * Drop query
	 * @param tableName
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public Result dropTable(String tableName) throws ZeppelinDriverException;
}
