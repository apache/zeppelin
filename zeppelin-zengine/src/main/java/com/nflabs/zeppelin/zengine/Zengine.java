package com.nflabs.zeppelin.zengine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.FileSystem;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public class Zengine {

    //TODO(alex): get list of drivers from conf, init() them
    private Map<String, ZeppelinDriver> supportedDrivers = new HashMap<String, ZeppelinDriver>();
    
    //TODO(alex): move driver from Zengine -> Z's instances
    private ZeppelinDriver driver;
    
    private ZeppelinConfiguration conf;
    private JRubyScriptEngineFactory factory;
    private FileSystem fs;


    public ScriptEngine getRubyScriptEngine(){
        return factory.getScriptEngine();
    }
    
    /**
     * Configure Zeppelin environment.
     * zeppelin-site.xml will be loaded from classpath.
     * @throws ZException
     */
    public void configure() throws ZException{
        ZeppelinConfiguration conf;
        try {
            conf = ZeppelinConfiguration.create();
        } catch (ConfigurationException e) {
            conf = new ZeppelinConfiguration();
        }
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
        
        if(driver==null){
            String driverClassName = getConf().getString(ConfVars.ZEPPELIN_DRIVER);
            try {
                Class<?> driverClass = Class.forName(driverClassName);
                Constructor<?> cons = driverClass.getConstructor(ZeppelinConfiguration.class);
                this.driver = (ZeppelinDriver) cons.newInstance(getConf());
                this.driver.init();
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
        } else {
            this.driver = driver;
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

    public void setDriver(ZeppelinDriver driver) {
        this.driver = driver;
    }
    public ZeppelinDriver getDriver() {
        return this.driver;
    }

}
