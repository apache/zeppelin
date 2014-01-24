package com.nflabs.zeppelin.driver.hive;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;

public class HiveZeppelinConnection implements ZeppelinConnection {

	private Connection connection;
	
	public HiveZeppelinConnection(Connection connection) {
		this.connection = connection;
	}

	@Override
	public boolean isConnected() throws ZeppelinDriverException {
		try {
			if(connection.isClosed()){
				return false;
			} else {
				return true;
			}
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
	
	private Result execute(String query) {

		try{
			ResultSet res = null;
			Statement stmt = connection.createStatement();
			res = stmt.executeQuery(query);
			Result r = new Result(res);
			r.load();
			stmt.close();
			return r;
		} catch (SQLException e) {
			if (e.getMessage().startsWith("The query did not generate a result set")) {
				try {
					return new Result();
				} catch (ResultDataException e1) {
					throw new ZeppelinDriverException(e1);
				}
			} else {
				throw new ZeppelinDriverException(e);
			}
		} catch (ResultDataException e) {
			throw new ZeppelinDriverException(e);
		}

	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
		return execute(query);
	}

	@Override
	public Result addResource(URI resourceLocation) throws ZeppelinDriverException {
		if(resourceLocation.getPath().endsWith(".jar")){
			return execute("ADD JAR "+resourceLocation.toString());			
		} else {
			return execute("ADD FILE "+resourceLocation.toString());			
		}
	}

	@Override
	public Result createViewFromQuery(String viewName, String query) {// throws ZeppelinDriverException {
		return execute("CREATE VIEW "+viewName+" AS "+query);
	}

	@Override
	public Result createTableFromQuery(String tableName, String query){// throws ZeppelinDriverException {
		return execute("CREATE TABLE "+tableName+" AS "+query);
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
		return execute("DROP VIEW "+viewName);		
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException{
		return execute("DROP TABLE "+tableName);		
	}

	@Override
	public Result select(String tableName, int limit) throws ZeppelinDriverException {
		if (limit >=0 ){
			return execute("SELECT * FROM "+tableName+" LIMIT "+limit);
		} else {
			return execute("SELECT * FROM "+tableName);
		}
	}

	@Override
	public void abort() throws ZeppelinDriverException {
		throw new ZeppelinDriverException("Abort not supported");
	}

}
