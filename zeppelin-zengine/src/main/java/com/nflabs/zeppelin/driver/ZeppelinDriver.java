package com.nflabs.zeppelin.driver;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

/**
 * Zeppelin driver is physical layer abstraction.
 *
 */
public abstract class ZeppelinDriver {
	private ZeppelinConfiguration conf;

	/**
	 * Constructor
	 * @param conf zeppelin configuration
	 * @throws ZeppelinDriverException 
	 */
	public ZeppelinDriver(ZeppelinConfiguration conf){
		this.conf = conf;
	}
	
	/**
	 * Get zeppelin configuration
	 * @return
	 */
	public ZeppelinConfiguration getConf(){
		return conf;
	}
	

	/**
	 * Get zeppelin connection
	 * 
	 * @return
	 * @throws ZeppelinDriverException
	 */
	public abstract ZeppelinConnection getConnection() throws ZeppelinDriverException;
	
	/**
	 * Initialize driver
	 * @throws ZeppelinDriverException
	 */
	public abstract void init() throws ZeppelinDriverException;
	
	/**
	 * Distroy the driver
	 * @throws ZeppelinDriverException
	 */
	public abstract void shutdown() throws ZeppelinDriverException;
}
