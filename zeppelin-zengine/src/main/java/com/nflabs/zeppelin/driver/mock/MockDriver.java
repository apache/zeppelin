package com.nflabs.zeppelin.driver.mock;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;

public class MockDriver extends ZeppelinDriver{
	static public Map<String, Result> queries = new HashMap<String, Result>();
	static public Map<String, Result> views = new HashMap<String, Result>();
	static public Map<String, Result> tables = new HashMap<String, Result>();
	
	public MockDriver(ZeppelinConfiguration conf, URI uri, ClassLoader classLoader) {
		super(conf, uri, classLoader);
	}

	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		return new MockDriverConnection(queries, views, tables);
	}
	
}
