package com.nflabs.zeppelin.driver.exec;

import java.net.URI;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;

public class ExecDriver extends ZeppelinDriver {

	public ExecDriver(ZeppelinConfiguration conf, URI uri, ClassLoader classLoader) {
		super(conf, uri, classLoader);
	}

	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		return new ExecConnection();
	}
}
