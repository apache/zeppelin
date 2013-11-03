package com.nflabs.zeppelin.zengine;

public class ParamInfo {
	String name;
	Object defaultValue;
	
	public ParamInfo(String name, Object defaultValue){
		this.name = name;
		this.defaultValue = defaultValue;
	}
	
	public String getName(){
		return name;
	}
	
	public Object getDefaultValue(){
		return defaultValue;
	}
}
