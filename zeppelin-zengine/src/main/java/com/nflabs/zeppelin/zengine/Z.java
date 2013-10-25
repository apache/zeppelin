package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import javax.script.ScriptEngine;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.jdbc.HiveConnection;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.util.Util;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public abstract class Z {
	String id; // z object identifier
	Z prev;
	transient Z next;
	private Result result;
	private Result lastQueryResult;
	boolean executed = false;
	int maxResult = 10000;
	boolean webEnabled = false;
	
	protected Z(){
		this.id = Integer.toString(hashCode());
	}
	
	public String getId(){
		return id;
	}
	
	private Logger logger(){
		return Logger.getLogger(Z.class);
	}
	
	public Z withMaxResult(int maxResult){
		this.maxResult = maxResult;
		return this;
	}
	
	public Z pipe(Z z){
		setNext(z);
		z.setPrev(this);
		return z;
	}
	
	public Z unPipe(){
		if(next()!=null){
			next().setPrev(null);
			setNext(null);
		}
		return this;
	}
	
	public Z prev(){
		return prev;
	}
	
	public Z next(){
		return next;
	}
	
	public void setPrev(Z prev){
		this.prev = prev;
	}
	
	public void setNext(Z next){
		this.next = next;
	}
	
	public abstract String name(); // table or view name
	public abstract String getQuery() throws ZException;
	public abstract List<URI> getResources() throws ZException;
	public abstract String getReleaseQuery() throws ZException;
	public abstract InputStream readWebResource(String path) throws ZException;
	public abstract boolean isWebEnabled(); 
	protected abstract void initialize() throws ZException;
	
	public void release() throws ZException{
		initialize();
		if(executed==false) return;
		
		String q = getReleaseQuery();
		executeQuery(q, maxResult);
		
		if(prev()!=null){
			prev().release();
		}
	}
	
	public Z execute() throws ZException{
		if(executed==true) return this;
		initialize();

		if(prev()!=null){
			prev().execute();
		}		
		String query = getQuery();
		lastQueryResult = executeQuery(query, maxResult);
		webEnabled = isWebEnabled();
		executed = true;
		return this;
	}
		
	public Result result() throws ZException{
		if(executed==false){
			throw new ZException("Can not get result because of this is not executed");
		}

		if(result==null){
			if(name()==null){ // unmaed
				if(lastQueryResult!=null){
					result = lastQueryResult;
				}
			} else { // named
				try{
					result = executeQuery("select * from "+name(), maxResult);
				} catch(Exception e){  // if table not found
					if(lastQueryResult!=null){
						result = lastQueryResult;
					}
				}
			}
		}
		
		return result;
	}
	
	public boolean isExecuted(){
		return executed;
	}
	
	private Result executeQuery(String query, int max) throws ZException{
		initialize();
		if(query==null) return null;
		
		
		Connection con = null;
		try {
			con = getConnection();
			// add resources			
			List<URI> resources = getResources();

			for(URI res : resources){
				Statement stmt = con.createStatement();
				logger().info("add resource "+res.toString()); 
				if(res.getPath().endsWith(".jar")){
					stmt.executeQuery("add JAR "+new File(res.toString()).getAbsolutePath());
				} else {
					stmt.executeQuery("add FILE "+new File(res.toString()).getAbsolutePath());
					
				}
				stmt.close();
			}
			
			// execute query
			ResultSet res = null;
			Statement stmt = con.createStatement();
			logger().info("executeQuery("+query+")");
			res = stmt.executeQuery(query);
			
			Result r = new Result(res, maxResult);
			r.load();
			stmt.close();
			return r;
		} catch (Throwable e) {
			try {
				if(con!=null){
					con.close();
				}
			} catch (Throwable e1) {
				logger().error("error on closing connection", e1);
			}
			throw new ZException(e);
		} 
	}
	

	public ScriptEngine getRubyScriptEngine(){
		return factory.getScriptEngine();
	}
	
	public static void configure() throws ZException{
		ZeppelinConfiguration conf;
		try {
			conf = ZeppelinConfiguration.create();
		} catch (ConfigurationException e) {
			conf = new ZeppelinConfiguration();
		}

		configure(conf);
	}
	public static void configure(ZeppelinConfiguration conf) throws ZException{		
		try {
			Class.forName(conf.getString(ConfVars.HIVE_DRIVER));
		} catch (ClassNotFoundException e1) {
			throw new ZException(e1);
		}
		Z.conf = conf;		
		Z.factory = new JRubyScriptEngineFactory();
		
		if(fs==null){
			try {
				fs = FileSystem.get(new org.apache.hadoop.conf.Configuration());
			} catch (IOException e) {
				throw new ZException(e);
			}
		}
	}
	
	private static Connection getConnection() throws SQLException{
		return new HiveConnection(hiveConf());
	}
	
	private static HiveConf hiveConf(){
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	    if (classLoader == null) {
	      classLoader = Z.class.getClassLoader();
	    }
	    
		URL url = classLoader.getResource("hive-site.xml");
		HiveConf hiveConf = null;
		hiveConf = new HiveConf();
		if(url==null){
			// set some default configuration if no hive-site.xml provided
			hiveConf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, Z.conf().getString(ConfVars.ZEPPELIN_LOCAL_WAREHOUSE));
		}
		return hiveConf;		
	}
	
	private static ZeppelinConfiguration conf;
	private static JRubyScriptEngineFactory factory;
	private static FileSystem fs;
	
	public static ZeppelinConfiguration conf(){
		return conf;
	}

	public static FileSystem fs(){
		return fs;
	}
	
}
