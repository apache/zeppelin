package com.nflabs.zeppelin.zengine.context;

import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.zengine.ParamInfo;

public class ZLocalContextImpl implements ZContext {
	public String in;
	public String out;
	public String arg;
	private Map<String, Object> params;
	Map<String, ParamInfo> paramInfos = new HashMap<String, ParamInfo>();
	
	/**
	 * Initialize Zeppelin Context
	 * @param tableIn input table name
	 * @param tableOut output table name
	 * @param arg arguments
	 * @param params parameters to UDF
	 */
	public ZLocalContextImpl(String tableIn, String tableOut, String arg, Map<String, Object> params){
		this.in = tableIn;
		this.out = tableOut;
		this.arg = arg;
		this.params = params;
	}
	
	public Map<String, ParamInfo> getParamInfos(){
		return paramInfos;
	}
	
	/**
	 * Get params;
	 * @return 
	 */
	@Override
	public Object param(String name){
		return param(name, null);
	}

	/**
	 * Get params;
	 * @param name name of parameter
	 * @param defaultValue defaultValue of the param
	 * @return 
	 */
	@Override
	public Object param(String name, Object defaultValue){
		if(paramInfos.containsKey(name)==false){
			paramInfos.put(name, new ParamInfo(name, defaultValue));
		}
			
		Object r = params.get(name);
		
		if(r==null){
			return defaultValue;
		} else {
			return r;
		}
	}
	
	/**
	 * Get input table name
	 * @return
	 */
	@Override
	public String in(){
		return in;
	}
	
	/**
	 * Get output table name
	 * @return
	 */
	@Override
	public String out(){
		return out;
	}
	
	/**
	 * Get arguments
	 * @return
	 */
	@Override
	public String arg(){
		return arg;
	}
}
