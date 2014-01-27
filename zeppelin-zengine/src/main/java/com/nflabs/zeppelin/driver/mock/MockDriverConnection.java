package com.nflabs.zeppelin.driver.mock;

import java.net.URI;
import java.util.Map;

import org.apache.log4j.Logger;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;

public class MockDriverConnection implements ZeppelinConnection{
	Logger logger = Logger.getLogger(MockDriverConnection.class);
	
	private Map<String, Result> queries;
	private Map<String, Result> views;
	private Map<String, Result> tables;

	public MockDriverConnection(Map<String, Result> queries, Map<String, Result> views, Map<String, Result> tables) {
		this.queries = queries;
		this.views = views;
		this.tables = tables;
	}

	@Override
	public boolean isConnected() throws ZeppelinDriverException {
		return true;
	}

	@Override
	public void close() throws ZeppelinDriverException {
	}

	@Override
	public void abort() throws ZeppelinDriverException {
	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
		logger.info("TestDriver query("+query+")");
		Result r = queries.get(query);
		if (r==null) {
			return new Result();
		} else {
			return r;
		}
	}

	@Override
	public Result addResource(URI resourceLocation)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result select(String tableName, int limit)
			throws ZeppelinDriverException {
		logger.info("select "+tableName);
		if (views.containsKey(tableName)) {
			return views.get(tableName);
		} else if (tables.containsKey(tableName)) {
			return tables.get(tableName);
		} else {
			return query("select * from "+tableName);
		}
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
		logger.info("createView "+viewName);
		views.put(viewName, queries.get(query));
		return new Result();
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
		logger.info("createTable "+tableName);
		tables.put(tableName, queries.get(query));
		return new Result();
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		logger.info("dropTable "+viewName);
		views.remove(viewName);
		return new Result();
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
		logger.info("dropTable "+tableName);
		tables.remove(tableName);
		return new Result();
	}

}
