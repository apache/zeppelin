package com.nflabs.zeppelin.server.form;

import java.util.Map;
import java.util.TreeMap;

public class Form {
	Map<String, Object> params;  // actual values from client
	Map<String, Base> forms = new TreeMap<String, Base>();
	
	public static enum Type {
		INPUT
	}
	
	public Form(){
		
	}
	
	public void setParams(Map<String, Object> values){
		this.params = values;
	}
	
	public Map<String, Object> getParams(){
		return params;
	}
	
	public Object input(String id, Object defaultValue) {
		// first find values from client and then use defualt		
		Object value = params.get(id);
		if(value == null) {
			value = defaultValue;
		}
		
		forms.put(id, new Input(value));
		return value;
	}
	
	public Object input(String id) {
		return input(id, "");
	}
}