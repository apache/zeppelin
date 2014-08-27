package com.nflabs.zeppelin.notebook.form;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Form {
	Map<String, Object> params = new HashMap<String, Object>();  // actual values from client
	Map<String, Input> forms = new TreeMap<String, Input>();
	

	public Form(){
		
	}
	
	public void setParams(Map<String, Object> values){
		this.params = values;
	}
	
	public Map<String, Object> getParams(){
		return params;
	}
	
	
	
	public Map<String, Input> getForms() {
		return forms;
	}

	public void setForms(Map<String, Input> forms) {
		this.forms = forms;
	}

	public Object input(String id, Object defaultValue) {
		// first find values from client and then use defualt		
		Object value = params.get(id);
		if(value == null) {
			value = defaultValue;
		}
		
		forms.put(id, new Input(id, defaultValue));
		return value;
	}
	
	public Object input(String id) {
		return input(id, "");
	}
	
	public void clearForms(){
		this.forms = new TreeMap<String, Input>();
	}
}