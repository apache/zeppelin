package com.nflabs.zeppelin.driver.exec;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;

public class ExecDriver extends ZeppelinDriver {

	public ExecDriver(ZeppelinConfiguration conf) {
		super(conf);
	}

	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		return new ExecConnection();
	}

	@Override
	public void init() throws ZeppelinDriverException {
	}

	@Override
	public void shutdown() throws ZeppelinDriverException {
	}

}
