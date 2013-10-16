package com.nflabs.zeppelin.zql;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public abstract class Z {
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
	
	public abstract String getQuery();
	public abstract List<URI> getResources();
	
	public ResultSet execute() throws SQLException{		
		Connection con = getConnection(); 
		// add resources			
		List<URI> resources = getResources();

		for(URI res : resources){
			Statement stmt = con.createStatement();
			if(res.getPath().endsWith(".jar")){
				stmt.executeQuery("add JAR "+new File(res.toString()).getAbsolutePath());
			} else {
				stmt.executeQuery("add FILE "+new File(res.toString()).getAbsolutePath());
			}
			stmt.close();
		}
		
		// execute query
		String query = getQuery();
		
		Statement stmt = con.createStatement();
		ResultSet result = stmt.executeQuery(query);
		stmt.close();
		return result; 
	}

	
	
	public static void init(ZeppelinConfiguration conf) throws ClassNotFoundException{		
		Class.forName(conf.getString(ConfVars.HIVE_DRIVER));
		Z.conf = conf;		
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
			conn = DriverManager.getConnection(conf.getString(ConfVars.HIVE_URI));
		}
		return conn;
	}
	
	private static Connection conn;
	private static ZeppelinConfiguration conf;
	

}
