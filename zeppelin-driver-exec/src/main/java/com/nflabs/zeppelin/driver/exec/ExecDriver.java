package com.nflabs.zeppelin.driver.exec;

import java.util.regex.Pattern;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;

public class ExecDriver extends ZeppelinDriver {
	
	@Override
	public ZeppelinConnection createConnection(String uri) throws ZeppelinDriverException {
		return new ExecConnection();
	}

	@Override
	public boolean acceptsURL(String url) {
		return Pattern.matches("exec://.*", url);
	}

	@Override
	protected void init() {

	}
}
