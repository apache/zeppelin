package com.nflabs.zeppelin.zengine;

import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.result.Result;

/**
 * Zeppelin Web Context. Passed to Zeppelin UDF's web template.
 * ZWebContext have result data of execution.
 * Normally result in ZWebContext is used to visualize data in web template file.
 * @author moon
 *
 */
public class ZWebContext {

	private Result result;
	private Map<String, Object> params;

	
	public ZWebContext(Result resultDataObject) {
		this.result = resultDataObject;
		this.params = new HashMap<String, Object>();
	}
	/**
	 * 
	 * @param params 
	 * @param resultDataObject result data after execution
	 */
	public ZWebContext(Map<String, Object> params, Result resultDataObject) {
		this.result = resultDataObject;
		this.params = params;
	}
	
	/**
	 * Get result data
	 * @return result
	 */
	public Result result(){
		return result;
	}
	
	/**
	 * Get params;
	 * @return 
	 */
	public Object param(String name){
		return param(name, null);
	}

	/**
	 * Get params;
	 * @param name name of parameter
	 * @param defaultValue defaultValue of the param
	 * @return 
	 */
	public Object param(String name, Object defaultValue){		
		Object r = params.get(name);
		if(r==null){
			return defaultValue;
		} else {
			return r;
		}
	}

}
