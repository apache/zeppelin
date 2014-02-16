package com.nflabs.zeppelin.driver.mock;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;

public class MockDriver extends ZeppelinDriver{
	static public Map<String, Result> queries = new HashMap<String, Result>();
	static public Map<String, Result> views = new HashMap<String, Result>();
	static public Map<String, Result> tables = new HashMap<String, Result>();
	static public List<URI> loadedResources = new LinkedList<URI>();

	@Override
	protected ZeppelinConnection createConnection(String uri) throws ZeppelinDriverException {
		return new MockDriverConnection(queries, views, tables, loadedResources);
	}


	@Override
	public boolean acceptsURL(String url) {
		return true;
	}


	@Override
	protected void init() {

	}
	
}
