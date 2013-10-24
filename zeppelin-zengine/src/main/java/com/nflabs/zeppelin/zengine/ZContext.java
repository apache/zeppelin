package com.nflabs.zeppelin.zengine;

import java.util.Map;

public class ZContext {
	public String in;
	public String out;
	public String arg;
	private Map<String, Object> params;
	
	public ZContext(String tableIn, String tableOut, String arg, Map<String, Object> params){
		this.in = tableIn;
		this.out = tableOut;
		this.arg = arg;
		this.params = params;
	}
	
	public Object param(String paramName){
		return params.get(paramName);
	}
	
	public String in(){
		return in;
	}
	public String tableOut(){
		return out;
	}
	public String arg(){
		return arg;
	}
}
