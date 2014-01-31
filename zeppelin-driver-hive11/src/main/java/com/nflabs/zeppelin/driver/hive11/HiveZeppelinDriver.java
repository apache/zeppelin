package com.nflabs.zeppelin.driver.hive11;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.service.HiveInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;

public class HiveZeppelinDriver extends ZeppelinDriver {
	
	static {
		ZeppelinDriverFactory.registerDriver(new HiveZeppelinDriver());
	}
	
	Logger logger = LoggerFactory.getLogger(HiveZeppelinDriver.class);
    private static String HIVE_SERVER = "org.apache.hadoop.hive.jdbc.HiveDriver";
    private static String HIVE_SERVER_2 = "org.apache.hive.jdbc.HiveDriver";

	private HiveInterface client;	
	
	public void setClient(HiveInterface client){
		this.client = client;
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
        return className;        
	}
	
	private ZeppelinConfiguration getConf(){
		return ZeppelinConfiguration.create();
	}
	
	private String getLocalMetastore(){
		return new File(getConf().getString(ConfVars.ZEPPELIN_HOME)+"/metastore_db").getAbsolutePath();
	}
	
	private String getLocalWarehouse(){
		return new File(getConf().getString(ConfVars.ZEPPELIN_HOME)+"/warehouse").getAbsolutePath();
	}
	
	@Override
	public ZeppelinConnection createConnection(URI uri) throws ZeppelinDriverException {
		try {
			Connection con;
			String uriString = "jdbc:"+uri.toString();
			if (client!=null){ // create connection with given client instance
				logger.debug("Create connection from provided client instance");
				con = new HiveConnection(client);
			//else create instance using configuration files
			} else if(isEmpty(uriString)){ 
				logger.debug("Create connection from hive configuration");
				con = new HiveConnection(hiveConf());
			} else if(uriString.equals("jdbc:hive://local") || uriString.equals("jdbc:hive2://local")) { //local mode detected 
				logger.debug("Create connection from local mode");
				con = new HiveConnection(localHiveConf());
			} else { // remote connection using jdbc uri
				logger.debug("Create connection from given jdbc uri");
				Class.forName(getHiveDriverClass(uriString));
			    con = DriverManager.getConnection(uriString);
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
		logger.info("Local Hive Conf. warehouse="+getLocalWarehouse()+", metastore="+getLocalMetastore());
		// set some default configuration if no hive-site.xml provided
		hiveConf.set("javax.jdo.option.ConnectionURL", "jdbc:derby:;databaseName="+getLocalMetastore()+";create=true");
		hiveConf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, getLocalWarehouse());
		new File(getLocalWarehouse()).mkdirs();
		hiveConf.set(HiveConf.ConfVars.HADOOPJT.varname, "local");
		return hiveConf;
	}	

	@Override
	public boolean acceptsURL(String url) {
		return ( Pattern.matches("hive://.*", url) || Pattern.matches("hive2://.*", url) );
	}
}
