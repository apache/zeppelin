/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.zeppelin.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;

import java.util.Vector;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MysqlConnection
 *
 * @author Hyungu Roh hyungu.roh@navercorp.com
 *
 */

public class MysqlConnection implements DBConnection {
  Logger logger = LoggerFactory.getLogger(MysqlConnection.class);

  String host;
  String port;
  String user;
  String passwd;

  public MysqlConnection(String host, String port, String user, String passwd) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.passwd = passwd;
  }

  Connection mysqlConnection;
  Exception exceptionOnConnect;
  Statement currentStatement;
  ResultSet resultSet; 

  @Override
  public void open() {
    String driver = "com.mysql.jdbc.Driver";
    String url = "jdbc:mysql://";
    url += host;
    url += port != "" ? ":" + port : "";
    url += "/?user=" + user;

    try {
      Class.forName(driver);
      mysqlConnection = DriverManager.getConnection(url, user, passwd);
    } catch ( ClassNotFoundException | SQLException e ) {
      logger.error("Can not open connection", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void close() {
    try {
      mysqlConnection.close();
      currentStatement.close();
      resultSet.close();
    } catch ( SQLException e ) {
      logger.error("Can not close connection", e);
    }

    mysqlConnection = null;
    exceptionOnConnect = null;
    currentStatement = null;
    resultSet = null;
  }

  @Override
  public InterpreterResult executeSql(String sql) {
    try {
      if (exceptionOnConnect != null) {
        return new InterpreterResult(Code.ERROR, exceptionOnConnect.getMessage());
      }
      currentStatement = mysqlConnection.createStatement();
      StringBuilder msg = null;
      if (StringUtils.containsIgnoreCase(sql, "EXPLAIN ")) {
        //return the explain as text, make this visual explain later
        msg = new StringBuilder();
      }
      else {
        msg = new StringBuilder("%table ");
      }
      resultSet = currentStatement.executeQuery(sql);
      try {
        ResultSetMetaData md = resultSet.getMetaData();
        for (int i = 1; i < md.getColumnCount() + 1; i++) {
          if (i == 1) {
            msg.append(md.getColumnName(i));
          } else {
            msg.append("\t" + md.getColumnName(i));
          }
        }
        msg.append("\n");
        while (resultSet.next()) {
          for (int i = 1; i < md.getColumnCount() + 1; i++) {
            msg.append(resultSet.getString(i) + "\t");
          }
          msg.append("\n");
        }
      } catch ( NullPointerException e ) {
  
      }

      InterpreterResult rett = new InterpreterResult(Code.SUCCESS, msg.toString());
      return rett;
    }
    catch (SQLException ex) {
      logger.error("Can not run " + sql, ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
  }

  @Override
  public void cancel() {
    if (currentStatement != null) {
      try {
        currentStatement.cancel();
      }
      catch (SQLException ex) {
      }
      finally {
        currentStatement = null;
      }
    }
  }
}

