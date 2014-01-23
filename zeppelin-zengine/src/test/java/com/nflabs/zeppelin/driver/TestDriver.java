package com.nflabs.zeppelin.driver;

import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;

public class TestDriver extends ZeppelinDriver{
	public Map<String, Result> queries = new HashMap<String, Result>();

	public TestDriver(ZeppelinConfiguration conf) {
		super(conf);
	}

	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		return new TestDriverConnection(queries);
	}

	@Override
	public void init() throws ZeppelinDriverException {
	}

	@Override
	public void shutdown() throws ZeppelinDriverException {
	}

	
}
