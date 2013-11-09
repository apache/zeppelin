package com.nflabs.zeppelin.driver.hive;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.service.HiveInterface;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.zengine.Z;

public class HiveZeppelinDriver extends ZeppelinDriver {
	private HiveInterface client;
	
	public HiveZeppelinDriver(ZeppelinConfiguration conf) {
		super(conf);
	}
	
	public void setClient(HiveInterface client){
		this.client = client;
	}

	private String getConnectionUri(){
		return getConf().getString("HIVE_CONNECTION_URI", "hive.connection.uri", "jdbc:hive://");
	}
	
	private String getHiveDriverClass(){
		// for hive2 "org.apache.hive.jdbc.HiveDriver"
		return getConf().getString("HIVE_DRIVER_CLASS", "hive.driver.class", "org.apache.hadoop.hive.jdbc.HiveDriver");
	}
	
	private String getLocalWarehouse(){
		return getConf().getString("HIVE_LOCAL_WAREHOUSE", "hive.local.warehouse", "data");
	}
	
	@Override
	public ZeppelinConnection getConnection() throws ZeppelinDriverException {
		try {
			Connection con; 
			if (client!=null){
				con = new HiveConnection(client);
			} else if(getConnectionUri()==null || getConnectionUri().trim().length()==0){
				con = new HiveConnection(hiveConf());
			} else {				
				Class.forName(getHiveDriverClass());
			    con = DriverManager.getConnection(getConnectionUri());
			}

			return new HiveZeppelinConnection(con);
		} catch (SQLException e) {
			throw new ZeppelinDriverException(e);
		} catch (ClassNotFoundException e) {
			throw new ZeppelinDriverException(e);
		}
	}

	
	private HiveConf hiveConf(){
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	    if (classLoader == null) {
	      classLoader = HiveZeppelinDriver.class.getClassLoader();
	    }
	    
		URL url = classLoader.getResource("hive-site.xml");
		HiveConf hiveConf = null;
		hiveConf = new HiveConf();
		if(url==null){
			// set some default configuration if no hive-site.xml provided
			hiveConf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, getLocalWarehouse());
		}
		return hiveConf;		
	}
}
