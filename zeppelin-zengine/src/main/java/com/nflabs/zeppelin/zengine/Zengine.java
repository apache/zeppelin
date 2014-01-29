package com.nflabs.zeppelin.zengine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import javax.script.ScriptEngine;

import org.apache.hadoop.fs.FileSystem;

import com.google.common.collect.ImmutableMap;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;
import com.sun.script.jruby.JRubyScriptEngineFactory;


/**
 * This class is Zeppelin Execution Environment, or context;
 * it hosts all necessary dependencies to run Zengine:
 * 
 *   - zeppelin configuration
 *   - available driver instances  (lazy initialized)
 *   - FileSystem
 *   - Ruby execution engine 
 *   
 *  If anybody needs those to run (i.e ZQL, Q, L etc) - they just ask Zengine for it.
 *  TODO(alex): this is subject of enhancement by real DI implementation later
 * 
 * @author Alex
 */
public class Zengine {

    private ZeppelinConfiguration conf;
    private JRubyScriptEngineFactory factory;
    private FileSystem fs;
	private ZeppelinDriverFactory driverFactory;

	private static ScriptEngine rubyScriptEngine = null;
    public ScriptEngine getRubyScriptEngine(){
    	if (rubyScriptEngine==null ){
    		rubyScriptEngine = factory.getScriptEngine();
    	}
        return rubyScriptEngine;
    }
    
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
        this.factory = new JRubyScriptEngineFactory();
        if (driverFactory == null) {
        	String driverDir = conf.getString(ConfVars.ZEPPELIN_DRIVER_DIR);
        	String [] uriList = conf.getString(ConfVars.ZEPPELIN_DRIVERS).split(",");
        	URI [] uris = new URI[uriList.length];
        	for (int i=0; i<uriList.length; i++) {
        		try {
					uris[i] = new URI(uriList[i]);
				} catch (URISyntaxException e) {
					throw new ZException(e);
				}
        	}
        	this.driverFactory = new ZeppelinDriverFactory(conf, driverDir, uris);
        } else {
        	this.driverFactory = driverFactory;
        }

        if(fs==null){
            try {
                fs = FileSystem.get(new org.apache.hadoop.conf.Configuration());
            } catch (IOException e) {
                throw new ZException(e);
            }
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
     * Get filesystem object
     * @return
     */
    public FileSystem fs(){
        return fs;
    }
    
    /**
     * Get zeppelin driver factory
     * @return
     */
    public ZeppelinDriverFactory getDriverFactory(){
    	return driverFactory;
    }

    /**
     * FacrotyMethod: Returns a new collection of Drivers every time.
     * 
     * IN : collection of names of available drivers (from configuration)
     * OUT: new immutable collection of driver instances
     * 
     * TODO(alex): this should be replaced by direct DI in consumers (i.e in ZQL constructor),
     * instead of them pulling it from Zengine by calling this method.
     * 
     * @return Immutable Map of "driver name" -> "driver instances"
     * @throws ZException
     */
    public Map<String, ZeppelinDriver> createAvailableDrivers() throws ZException {
        ImmutableMap.Builder<String, ZeppelinDriver> drivers = ImmutableMap.<String, ZeppelinDriver>builder();
        
        Collection<String> driverNames = driverFactory.getAllConfigurationNames();
        for (String driverName : driverNames) {
            drivers.put(driverName, driverFactory.createDriver(driverName));
        }
        return drivers.build();
    }
    
    public boolean useFifoJobScheduler() {
        return conf.getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO");
    }

}
