package com.nflabs.zeppelin.driver;

import java.net.URI;
import java.util.Map;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;

public class TestDriverConnection implements ZeppelinConnection{

	private Map<String, Result> queries;

	public TestDriverConnection(Map<String, Result> queries) {
		this.queries = queries;
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
		return queries.get("select * from "+tableName+" limit "+limit);
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		return null;
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
		return null;
	}

}
