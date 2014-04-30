package com.nflabs.zeppelin.driver.jdbc;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.OperationNotSupportedException;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;

public class JDBCConnection implements ZeppelinConnection {

	private Connection connection;

	public JDBCConnection(Connection connection) {
		this.connection = connection;
	}

	@Override
	public boolean isConnected() throws ZeppelinDriverException {
		try {
			return !connection.isClosed();
		} catch (SQLException e) {
			throw new ZeppelinDriverException(e);
		}
	}

	@Override
	public void close() throws ZeppelinDriverException {
		try {
			connection.close();
		} catch (SQLException e) {
			throw new ZeppelinDriverException(e);
		}
	}

	@Override
	public void abort() throws ZeppelinDriverException {
		throw new ZeppelinDriverException(new OperationNotSupportedException());
	}

	private Result execute(String query) throws ZeppelinDriverException {
		ResultSet res = null;
		Result r = null;
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			if (stmt.execute(query)) {
				res = stmt.getResultSet();
				r = new Result(res);
				r.load();
			}
		} catch (SQLException e) {
			throw new ZeppelinDriverException(e);
		} catch (ResultDataException e) {
			throw new ZeppelinDriverException(e);
		} finally {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return r;
	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
		return execute(query);
	}

	@Override
	public Result addResource(URI resourceLocation)
			throws ZeppelinDriverException {
		return new Result();
	}

	@Override
	public Result select(String tableName, int limit)
			throws ZeppelinDriverException {
		if (limit >= 0) {
			return execute("SELECT * FROM " + tableName + " LIMIT " + limit);
		} else {
			return execute("SELECT * FROM " + tableName);
		}
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
		return execute("CREATE VIEW " + viewName + " AS " + query);
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
		return execute("CREATE TABLE " + tableName + " AS " + query);
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		return execute("DROP VIEW " + viewName);
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
		return execute("DROP TABLE " + tableName);
	}

}
