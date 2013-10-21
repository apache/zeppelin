package com.nflabs.zeppelin.zengine;

import java.util.Map;

public class ZContext {
	public String table;
	public String arg;
	private Map<String, Object> params;

	public ZContext(String table, String arg, Map<String, Object> params){
		this.table = table;
		this.arg = arg;
		this.params = params;
	}
	
	public Object param(String paramName){
		return params.get(paramName);
	}
	
	public String table(){
		return table;
	}
	public String arg(){
		return arg;
	}
}
