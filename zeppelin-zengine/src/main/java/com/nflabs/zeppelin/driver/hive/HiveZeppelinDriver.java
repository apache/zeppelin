package com.nflabs.zeppelin.driver.hive;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.service.HiveInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;

public class HiveZeppelinDriver extends ZeppelinDriver {
	Logger logger = LoggerFactory.getLogger(HiveZeppelinDriver.class);
    private static String HIVE_SERVER = "org.apache.hadoop.hive.jdbc.HiveDriver";
    private static String HIVE_SERVER_2 = "org.apache.hive.jdbc.HiveDriver";

	private HiveInterface client;
	
	public HiveZeppelinDriver(ZeppelinConfiguration conf) {
		super(conf);
	}
	
	public void setClient(HiveInterface client){
		this.client = client;
	}

	private String getConnectionUri(){
		return getConf().getString("HIVE_CONNECTION_URI", "hive.connection.uri", "jdbc:hive2://");
	}
	
	/**
	 * HiveServer1 by default, but if uri starts with hive2 - assumes HiveServer2
	 * @param uri Hive URI
	 * @return JDBC driver class name
	 */
	private String getHiveDriverClass(String uri){
	    String className;
        if (uri.startsWith("jdbc:hive://")) { // HiveServer 
	        className = HIVE_SERVER;
	    } else { // HiveServer2
	        className = HIVE_SERVER_2;
	    }
		return getConf().getString("HIVE_DRIVER_CLASS", "hive.driver.class", className);
	}
	
	private String getLocalMetastore(){
		return getConf().getString(ConfVars.ZEPPELIN_HOME)+"/metastore_db";
	}
	
	private String getLocalWarehouse(){
		return new File(getConf().getString(ConfVars.ZEPPELIN_HOME)+"/warehouse").getAbsolutePath();
	}
	
	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		try {
			Connection con;
			String uri = getConnectionUri();
			if (client!=null){ // create connection with given client instance
				logger.debug("Create connection from provided client instance");
				con = new HiveConnection(client);
			//else create instance using configuration files
			} else if(isEmpty(uri)){ 
				logger.debug("Create connection from hive configuration");
				con = new HiveConnection(hiveConf());
			} else if(uri.equals("jdbc:hive://") || uri.equals("jdbc:hive2://")) { //local mode detected 
				logger.debug("Create connection from local mode");
				con = new HiveConnection(localHiveConf());
			} else { // remote connection using jdbc uri
				logger.debug("Create connection from given jdbc uri");
				Class.forName(getHiveDriverClass(uri));
			    con = DriverManager.getConnection(getConnectionUri());
			}

			return new HiveZeppelinConnection(con);
		} catch (SQLException e) {
			throw new ZeppelinDriverException(e);
		} catch (ClassNotFoundException e) {
			throw new ZeppelinDriverException(e);
		}
	}

	
	private boolean isEmpty(String string) {
        return string==null || string.trim().length()==0;
    }

    private HiveConf hiveConf(){
		HiveConf hiveConf = null;
		hiveConf = new HiveConf(SessionState.class);
		return hiveConf;		
	}
	
	private HiveConf localHiveConf(){
		HiveConf hiveConf = null;
		hiveConf = new HiveConf(SessionState.class);

		// set some default configuration if no hive-site.xml provided
		hiveConf.set("javax.jdo.option.ConnectionURL", "jdbc:derby:;databaseName="+getLocalMetastore()+";create=true");
		hiveConf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, getLocalWarehouse());
		new File(getLocalWarehouse()).mkdirs();
		hiveConf.set(HiveConf.ConfVars.HADOOPJT.varname, "local");
		return hiveConf;		
	}

	@Override
	public void init() throws ZeppelinDriverException {
		logger.info("Initialize "+HiveZeppelinDriver.class.getName());
	}

	@Override
	public void destroy() throws ZeppelinDriverException {
		logger.info("Shutdown "+HiveZeppelinDriver.class.getName());
		connection.get().close();
	}
	    
}
