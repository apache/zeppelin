package com.nflabs.zeppelin.zengine.context;

public interface ZContext {
	/**
	 * Get params;
	 * 
	 * @return
	 */
	public Object param(String name);

	/**
	 * Get params;
	 * 
	 * @param name
	 *            name of parameter
	 * @param defaultValue
	 *            defaultValue of the param
	 * @return
	 */
	public Object param(String name, Object defaultValue);

	/**
	 * Get input table name
	 * 
	 * @return
	 */
	public String in();

	/**
	 * Get output table name
	 * 
	 * @return
	 */
	public String out();

	/**
	 * Get arguments
	 * 
	 * @return
	 */
	public String arg();
}
