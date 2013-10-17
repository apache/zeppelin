package com.nflabs.zeppelin.zql;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public abstract class Z {
	
	Logger logger = Logger.getLogger(Z.class);
	Z prev;
	Z next;
	
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
	
	public abstract String getQuery() throws ZException;
	public abstract List<URI> getResources() throws ZException;
	
	public List<ResultSet> execute() throws ZException{		
		Connection con;
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
			
			List<ResultSet> results = new LinkedList<ResultSet>();
			
			// execute query
			String query = getQuery();
			Statement stmt = con.createStatement();
			String[] queries = split(query, ';');
			
			for(String q : queries){
				if(q==null || q.trim().length()==0) continue;			
				logger.info("executeQuery("+q+")");
				results.add(stmt.executeQuery(q));	
			}
			stmt.close();
			return results;
		} catch (SQLException e) {
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
	
	
	
	public static String [] split(String str, char splitter){
		String escapeSeq = "\"',;${}";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "${" };
		String [] blockEnd = new String[]{ "\"", "'", "}" };
		
		
		List<String> splits = new ArrayList<String>();
		
		String curString ="";
		int ignoreBlockIndex = -1;
		boolean escape = false;  // true when escape char is found
		int lastEscapeOffset = -1;
		for(int i=0; i<str.length();i++){
			char c = str.charAt(i);

			// escape char detected
			if(c==escapeChar && escape == false){
				escape = true;				
				continue;
			}
			
			// escaped char comes
			if(escape==true){
				if(escapeSeq.indexOf(c)<0){
					curString += escapeChar;
				}
				curString += c;
				escape = false;
				lastEscapeOffset = i;
				continue;
			}
			

			if(ignoreBlockIndex>=0){ // inside of block
				curString += c;
				
				// check if block is finishing
				if(curString.substring(lastEscapeOffset+1).endsWith(blockEnd[ignoreBlockIndex])){
					ignoreBlockIndex = -1;
					continue;
				}
								
			} else { // not in the block
				// check if it is pipe
				if(c==splitter){
					splits.add(curString);
					curString = "";
					lastEscapeOffset = -1;
					continue;
				}
				
				// add char to current string
				curString += c;
				
				// check if block is started
				for(int b=0; b<blockStart.length;b++){
					if(curString.substring(lastEscapeOffset+1).endsWith(blockStart[b])==true){
						ignoreBlockIndex = b; // block is started
					}
				}
			}
		}
		if(curString.length()>0)
			splits.add(curString);
		return splits.toArray(new String[]{});
		
	}
}
