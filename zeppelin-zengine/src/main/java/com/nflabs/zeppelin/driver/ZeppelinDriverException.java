package com.nflabs.zeppelin.driver;

public class ZeppelinDriverException extends Exception{
	public ZeppelinDriverException(Throwable e){
		super(e);
	}

	public ZeppelinDriverException(String m) {
		super(m);
	}
}
