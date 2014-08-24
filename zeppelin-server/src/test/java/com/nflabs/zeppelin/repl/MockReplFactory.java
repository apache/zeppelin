package com.nflabs.zeppelin.repl;

import java.util.Properties;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class MockReplFactory extends ReplFactory {

	public MockReplFactory(ZeppelinConfiguration conf) {
		super(conf);
	}
	
	public Repl createRepl(String replName, Properties properties) {
		if("MockRepl1".equals(replName) || replName==null) {
			return new MockRepl1(properties);
		} else if("MockRepl2".equals(replName)) {
			return new MockRepl2(properties);
		} else {
			return null;
		}
	}
}
