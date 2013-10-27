package com.nflabs.zeppelin.zengine;

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

	/**
	 * 
	 * @param resultDataObject result data after execution
	 */
	public ZWebContext(Result resultDataObject) {
		this.result = resultDataObject;
	}
	
	/**
	 * Get result data
	 * @return result
	 */
	public Result result(){
		return result;
	}

}
