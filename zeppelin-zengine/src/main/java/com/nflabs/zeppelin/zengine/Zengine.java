package com.nflabs.zeppelin.zengine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.common.collect.ImmutableMap;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;
import com.nflabs.zeppelin.util.Util;


/**
 * This class is Zeppelin Execution Environment, or context;
 * it hosts all necessary dependencies to run Zengine:
 * 
 *   - zeppelin configuration
 *   - driver factory
 *   
 *  If anybody needs those to run (i.e ZQL, Q, L etc) - they just ask Zengine for it.
 * 
 * @author Alex
 */
public class Zengine {
	Logger logger = Logger.getLogger(Zengine.class);
    private ZeppelinConfiguration conf;
	private ZeppelinDriverFactory driverFactory;
    
    /**
     * Create Zeppelin environment
     * zeppelin-site.xml will be loaded from classpath
     * 
     * @throws ZException
     */
    public Zengine() throws ZException{
    	this(ZeppelinConfiguration.create(), null);
    }
        
    /**
     * Configures Zengine with dependencies: 
     * 
     * @param conf - configuration from FileSysyem
     * @param driver - driver implementation
     *                 could be NULL, then default from configuration is used
     * @throws ZException
     */
    public Zengine(ZeppelinConfiguration conf, ZeppelinDriverFactory driverFactory) throws ZException{
        this.conf = conf;      
        if (driverFactory == null) {
        	this.driverFactory = new ZeppelinDriverFactory(conf);
        } else {
        	this.driverFactory = driverFactory;
        }
    }

    /**
     * Get zeppelin configuration
     * @return
     */
    public ZeppelinConfiguration getConf(){
        return conf;
    }
    
    /**
     * Get zeppelin driver factory
     * @return
     */
    public ZeppelinDriverFactory getDriverFactory(){
    	return driverFactory;
    }

    public boolean useFifoJobScheduler() {
        return conf.getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO");
    }

}
