package com.nflabs.zeppelin.driver.exec;

import java.net.URI;
import java.util.regex.Pattern;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;

public class ExecDriver extends ZeppelinDriver {
	
	static {
		ZeppelinDriverFactory.registerDriver(new ExecDriver());
	}
	
	@Override
	public ZeppelinConnection createConnection(URI uri) throws ZeppelinDriverException {
		return new ExecConnection();
	}

	@Override
	public boolean acceptsURL(String url) {
		return Pattern.matches("exec://.*", url);
	}
}
