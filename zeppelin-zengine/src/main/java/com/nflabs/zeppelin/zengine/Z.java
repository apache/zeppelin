package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.IOException;
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
import com.nflabs.zeppelin.util.Util;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public abstract class Z {	
	Logger logger = Logger.getLogger(Z.class);
	Z prev;
	Z next;
	private ResultSet result;
	
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
	
	private void setPrev(Z prev){
		this.prev = prev;
	}
	
	private void setNext(Z next){
		this.next = next;
	}
	
	public abstract String name(); // table or view name
	public abstract String getQuery() throws ZException;
	public abstract List<URI> getResources() throws ZException;
	public abstract String getCleanQuery() throws ZException;
	
	public ResultSet getResult(){
		return result;
	}
	
	public void clean() throws ZException{
		if(result==null) return;
		
		try {
			result.close();
		} catch (SQLException e) {
			logger.error("Error on close ResultSet", e);
		}		
		
		String q = getCleanQuery();
		executeQuery(q);
		
		if(prev()!=null){
			prev().clean();
		}
		
		result = null;
	}
	
	public ResultSet execute() throws ZException{
		if(result!=null){  // if it is already calculated
			return result;
		}
		
		if(prev()!=null){
			prev().execute();
		}
		
		String query = getQuery();
		result = executeQuery(query);
		return result;
	}
	
	private ResultSet executeQuery(String query) throws ZException{
		if(query==null) return null;
		
		Connection con = null;
		try {
			con = getConnection();
			// add resources			
			List<URI> resources = getResources();

			for(URI res : resources){
				Statement stmt = con.createStatement();
				logger.info("add resource "+res.toString()); 
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
			logger.info("executeQuery("+query+")");
			res = stmt.executeQuery(query);				
			stmt.close();
			return res;
		} catch (SQLException e) {
			try {
				con.close();
			} catch (SQLException e1) {
				logger.error("error on closing connection", e1);
			}
			throw new ZException(e);
		} 
	}
	

	public ScriptEngine getRubyScriptEngine(){
		return  factory.getScriptEngine();
	}
	
	public static void init() throws ZException{
		ZeppelinConfiguration conf;
		try {
			conf = ZeppelinConfiguration.create();
		} catch (ConfigurationException e) {
			conf = new ZeppelinConfiguration();
		}

		init(conf);
	}
	public static void init(ZeppelinConfiguration conf) throws ZException{		
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
			} catch (SQLException e) {
				e.printStackTrace();
			}
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
