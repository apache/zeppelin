package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
		executeQuery(q);
		
		if(prev()!=null){
			prev().release();
		}
	}
	
	public Z execute() throws ZException{
		initialize();

		if(prev()!=null){
			prev().execute();
		}
		
		String query = getQuery();
		ResultSet res = executeQuery(query);
		if(name()==null){
			try {
				lastQueryResult = new Result(res, maxResult);
			} catch (ResultDataException e) {
				throw new ZException(e);
			}
		}
		webEnabled = isWebEnabled();
		executed = true;
		return this;
	}
	
	private boolean isTableExists(String name){
		try {
			Result desc = new Result(executeQuery("describe "+name), 10000);
			desc.load();
			if(desc.getRows().size()<5) return false;
			if(((String)desc.getRows().get(0)[0]).contains("not exist")) return false;
			return true;
		} catch (ZException e) {
			return false;
		} catch (ResultDataException e) {
			return false;
		} catch (SQLException e) {
			return false;
		} 
	}
	
	public Result result() throws ZException{
		if(executed==false){
			throw new ZException("Can not get result because of this is not executed");
		}
		try {
			if(result==null){
				if(name()==null){ // unmaed
					if(lastQueryResult!=null){
						result = lastQueryResult;
						result.load();
					}
				} else { // named
					try{
						result = new Result(executeQuery("select * from "+name()), maxResult);
						result.load();
					} catch(Exception e){
						if(lastQueryResult!=null){
							result = lastQueryResult;
							result.load();
						}
					}
				}

			}
			return result;			
		} catch (ResultDataException e) {
			throw new ZException(e);
		} catch (SQLException e) {
			throw new ZException(e);
		}
	}
	
	public boolean isExecuted(){
		return executed;
	}
	
	private ResultSet executeQuery(String query) throws ZException{
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
			stmt.close();
			return res;
		} catch (Throwable e) {
			try {
				if(con!=null){
					disconnect();
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
	
	public static void disconnect(){
		if(conn!=null){
			try {
				conn.close();
			} catch (Throwable e) {
				e.printStackTrace();
			}
			conn = null;
		}
	}

	private static Connection getConnection() throws SQLException{
		if(conn==null){
			conn = DriverManager.getConnection(conf().getString(ConfVars.HIVE_URI));
		} else {
			if(conn.isClosed()){
				conn = DriverManager.getConnection(conf().getString(ConfVars.HIVE_URI));	
			}
		}
		return conn;
	}
	
	private static Connection conn;
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
