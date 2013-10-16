package com.nflabs.zeppelin.zql;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class L extends Z{
	private String name;
	Map<String, Object> params = new HashMap<String, Object>();

	public L(String name){
		this.name = name;
	}
		
	public L withParam(String key, Object val){
		params.put(key, val);
		return this;
	}
	
	
	public String toString(){
		return name;
	}

	@Override
	public String getQuery() {
		return null;
	}

	@Override
	public List<URI> getResources() {

		return null;
	}
}
