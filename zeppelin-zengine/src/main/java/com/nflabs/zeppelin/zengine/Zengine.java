package com.nflabs.zeppelin.zengine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;

import org.apache.hadoop.fs.FileSystem;

import com.google.common.collect.ImmutableMap;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
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
    private List<String> availableDriverNames;

    public ScriptEngine getRubyScriptEngine(){
        return factory.getScriptEngine();
    }
    
    /**
     * Configure Zeppelin environment
     * zeppelin-site.xml will be loaded from classpath
     * 
     * @throws ZException
     */
    public void configure() throws ZException{
        ZeppelinConfiguration conf = ZeppelinConfiguration.create();
        configure(conf, null);
    }
        
    /**
     * Configures Zengine with dependencies: 
     * 
     * @param conf - configuration from FileSysyem
     * @param driver - driver implementation
     *                 could be NULL, then default from configuration is used
     * @throws ZException
     */
    public void configure(ZeppelinConfiguration conf, ZeppelinDriver driver) throws ZException{
        this.conf = conf;      
        this.factory = new JRubyScriptEngineFactory();
        this.availableDriverNames = getAvailableDriverNames();
        
        //TODO: delete, replaced by getAvailableDrivers()
        //if(driver==null){
        //    List<String> drivers = getAvailableDriverNames();
        //    this.driver = DriverFactory_createDriver(drivers.get(0));//got only 1 configured
        //} else {
        //    this.driver = driver;
        //}
        
        if(fs==null){
            try {
                fs = FileSystem.get(new org.apache.hadoop.conf.Configuration());
            } catch (IOException e) {
                throw new ZException(e);
            }
        }
    }

    private ZeppelinDriver DriverFactory_createDriver(String driverClassName) throws ZException {
        // TODO this is fake of DriverFactory before we merged with PR#33
        ZeppelinDriver dr; 
        try {
            Class<?> driverClass = Class.forName(driverClassName);
            Constructor<?> cons = driverClass.getConstructor(ZeppelinConfiguration.class);
            dr = (ZeppelinDriver) cons.newInstance(getConf());
            dr.init();
        } catch (ClassNotFoundException e) {
            throw new ZException(e);
        } catch (SecurityException e) {
            throw new ZException(e);
        } catch (NoSuchMethodException e) {
            throw new ZException(e);
        } catch (IllegalArgumentException e) {
            throw new ZException(e);
        } catch (InstantiationException e) {
            throw new ZException(e);
        } catch (IllegalAccessException e) {
            throw new ZException(e);
        } catch (InvocationTargetException e) {
            throw new ZException(e);
        } catch (ZeppelinDriverException e) {
            throw new ZException(e);
        }
        return dr;
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
        if (_dirversMock != null) {
            return _dirversMock; 
        }
        ImmutableMap.Builder<String, ZeppelinDriver> drivers = ImmutableMap.<String, ZeppelinDriver>builder();
        for (String driverName : availableDriverNames) {
            drivers.put(driverName, DriverFactory_createDriver(driverName));
        }
        return drivers.build();
    }
    
    /**
     * This is for UnitTests Dependency Injection only.
     * TODO(alex): replace this with a real DI!
     */
    private Map<String, ZeppelinDriver> _dirversMock;
    public void _mockSingleAvailableDriver(Map<String, ZeppelinDriver> mock) {
        this._dirversMock = mock;
    }

    
    
    /**
     * All guys below are good candidate to go to ZeppelinConfiguration 
     */
    private List<String> getAvailableDriverNames() {
        List<String> result = new ArrayList<String>();
        result.add(getConf().getString(ConfVars.ZEPPELIN_DRIVER));
        return result;
    }
    public boolean useFifoJobScheduler() {
        return conf.getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO");
    }

}
