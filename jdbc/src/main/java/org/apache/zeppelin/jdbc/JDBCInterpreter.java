/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

package org.apache.zeppelin.jdbc;

import org.apache.zeppelin.interpreter.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.List;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC interpreter for Zeppelin.
 *
 * @author Andres  Celis t-ancel@microsoft.com
 *
 */

public class JDBCInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(JDBCInterpreter.class);
  int commandTimeOut = 600000;
  Connection conn = null;
  ResultSet rs = null;
  String connectionUrl;
  HashMap<String, Statement> stmts;
  
  static {
    Interpreter.register(
        "jdbc",
        "jdbc",
        JDBCInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("jdbc.host",
                "localhost",
                "your hostname")
            .add("jdbc.user",
                    "",
                    "your ID")
            .add("jdbc.password",
                    "",
                    "your password")
            .add("jdbc.port",
                    "1433",
                    "your port")
            .add("jdbc.driver.name",
                    "",
                    "your JDBC driver name (e.g., sqlserver, postgresql, mysql)")
            .add("jdbc.driver.classname",
                    "",
                    "your jdbc driver class name (e.g., com.microsoft.sqlserver.jdbc." 
                    + "SQLServerDriver, org.postgresql.Driver, com.mysql.jdbc.Driver)")
            .add("jdbc.driver.url",
                    "",
                    "url to your JDBC driver (e.g.," 
                    + " jar:file:/<zeppelin_home>/interpreter/jdbc/JDBCdriver.jar!/)")
            .add("jdbc.database.name",
                    "",
                    "your database")
            .add("jdbc.windows.auth",
                    "",
                    "true/false, sql authentication if false (optional)")
            .build());
  }

 
  public JDBCInterpreter(Properties property) {
    super(property);
  }


  private String loadJDBCDriver(String url, String classname) {
    try {
      URL u = new URL(url);
      URLClassLoader ucl = new URLClassLoader(new URL[] { u });
      Driver d = (Driver) Class.forName(classname, true, ucl).newInstance();
      DriverManager.registerDriver(new DriverShim(d));
    }
    catch (Exception e) {
      logger.error("Error loading driver: ", e);
      return e.getMessage();
    }
    return null;
  }

  @Override
  public void open() {
    String host = "";
    String port = "";
    String user = "";
    String password = "";
    String driverName = "";
    String driverClassName = "";
    String driverUrl = "";
    String dbName = "";
    String windowsAuth = "";
    stmts = new HashMap<String, Statement>();

    // get Properties
    Properties intpProperty = getProperty();
    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String value = (String) intpProperty.get(key);

      switch (key) {
          case "jdbc.host":
            host = value;
            break;
          case "jdbc.port":
            port = value;
            break;
          case "jdbc.user":
            user = value;
            break;
          case "jdbc.password":
            password = value;
            break;
          case "jdbc.driver.name":
            driverName = value;
            break;
          case "jdbc.driver.classname":
            driverClassName = value;
            break;
          case "jdbc.driver.url":
            driverUrl = value;
            break;
          case "jdbc.database.name":
            dbName = value;
            break;
          case "jdbc.windows.auth":
            windowsAuth = value;
            break;
          default: 
            logger.info("else key : /" +  key + "/");
            break;
      }
    }
    
    // enforce populated properties
    if (driverClassName.equals("") || driverUrl.equals("") || driverName.equals("")) {
      logger.info("Must specify JDBC driver in interpreter settings");
    } else {
      // try loading driver, report errors
      String msg = loadJDBCDriver(driverUrl, driverClassName);
      if (msg != null) {
        logger.info("Cannot load JDBC driver: " + msg);
      }
    }
    
    // build connection string
    JDBCConnectionUrlBuilder jdbcUrl = new JDBCConnectionUrlBuilder(
                                      driverName, host, port, dbName, windowsAuth);
    connectionUrl = jdbcUrl.getConnectionUrl();
    if (connectionUrl == null) {
      logger.info("Connection URL format unknown for: " + driverName);
    }
    
    logger.info("Connect to " + connectionUrl);

    try {

      // connect to JDBC backend
      Properties info = new Properties();
      info.setProperty("user", user);
      info.setProperty("password", password);
      conn = DriverManager.getConnection(connectionUrl, info);
      // connection achieved
      logger.info("Connection achieved: " + connectionUrl);
    } catch ( SQLException e ) {
      logger.error("Connection failed: ", e);
    }
  }

  @Override
  public void close() {
    try {
      conn.close();
    } catch ( SQLException e ) {
      logger.error("Close connection failed: ", e);
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext context) {
    logger.info("Run SQL command '" + cmd + "'");
    Statement stmt;
    String pId = context.getParagraphId();
    try {
      stmt = conn.createStatement();
      stmts.put(pId, stmt);
      
      // execute the query
      rs = stmt.executeQuery(cmd);

      // format result as zeppelin table
      StringBuilder queryResult = new StringBuilder();
      queryResult.append("%table ");
      Vector<String> columnNames = new Vector<String>();

      if (rs != null) {
        ResultSetMetaData columns = rs.getMetaData();

        for ( int i = 1; i <= columns.getColumnCount(); ++i ) {
          if ( i != 1 ) {
            queryResult.append("\t");
          }
          queryResult.append(columns.getColumnName(i));
          columnNames.add(columns.getColumnName(i));
        }
        queryResult.append("\n");

        logger.info(columnNames.toString());

        while ( rs.next() ) {
          for ( int i = 0; i < columnNames.size(); ++i) {
            if ( i != 0 ) {
              queryResult.append("\t");
            }
            queryResult.append(rs.getString(columnNames.get(i)));
          }
          queryResult.append("\n");
        }
      }

      // disconnect
      if (stmt != null) stmt.close();
      stmts.remove(pId);
      
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, queryResult.toString());
    } catch ( SQLException e ) {
      logger.error("Can not run " + cmd, e);
      stmts.remove(pId);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    Statement stmt = stmts.get(context.getParagraphId());
    if (stmt != null) {  
      try {  
        stmt.cancel();
      }  
      catch (SQLException ex) {
        logger.info("Connection: " + connectionUrl);
        logger.info("Cancel failed on " + stmt);
        logger.info("Caused by: " + ex.getMessage());
      }  
      finally {  
        stmts.remove(context.getParagraphId());
      }
    }  
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = 10;
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        JDBCInterpreter.class.getName() + this.hashCode(), maxConcurrency);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
