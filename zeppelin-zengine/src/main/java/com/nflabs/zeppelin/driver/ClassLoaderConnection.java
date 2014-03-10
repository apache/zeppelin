package com.nflabs.zeppelin.driver;

import java.net.URI;

import com.nflabs.zeppelin.result.Result;

public class ClassLoaderConnection implements ZeppelinConnection{

	private ZeppelinConnection conn;
	private ClassLoader cl;

	public ClassLoaderConnection(ZeppelinConnection conn, ClassLoader cl){
		this.conn = conn;
		this.cl = cl;
	}
	@Override
	public boolean isConnected() throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.isConnected();
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public void close() throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			conn.close();
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}	
	}

	@Override
	public void abort() throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			conn.abort();
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}	
	}

	@Override
	public Result query(String query) throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.query(query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result addResource(URI resourceLocation)
			throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.addResource(resourceLocation);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result select(String tableName, int limit)
			throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.select(tableName, limit);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result createViewFromQuery(String viewName, String query)
			throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.createViewFromQuery(viewName, query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result createTableFromQuery(String tableName, String query)
			throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.createTableFromQuery(tableName, query);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result dropView(String viewName) throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.dropView(viewName);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}

	@Override
	public Result dropTable(String tableName) throws ZeppelinDriverException {
	   	ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(cl);
		try {
			return conn.dropTable(tableName);
		} catch(ZeppelinDriverException e){
			throw e;
		} catch(Exception e) {
			throw new ZeppelinDriverException(e);
		} finally {
			cl = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}
}
