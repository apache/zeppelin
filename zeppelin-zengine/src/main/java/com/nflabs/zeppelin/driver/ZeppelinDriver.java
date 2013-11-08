package com.nflabs.zeppelin.driver;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

/**
 * Zeppelin driver is physical layer abstraction.
 *
 */
public abstract class ZeppelinDriver {
	private ZeppelinConfiguration conf;

	/**
	 * Constructor
	 * @param conf zeppelin configuration
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
	
	
}
