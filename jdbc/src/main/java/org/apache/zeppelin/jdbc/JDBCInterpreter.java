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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC interpreter for Zeppelin.
 *
 * @author Hyungu Roh hyungu.roh@navercorp.com
 *
 */

public class JDBCInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(JDBCInterpreter.class);
  int commandTimeOut = 600000;

  static {
    Interpreter.register("jdbc", JDBCInterpreter.class.getName());
  }

  DBConnection jdbcConnection;
 
  public JDBCInterpreter(Properties property) {
    super(property);
    Properties currentProperty = getProperty();
    DBConnectionFactory DBFactory = new DBConnectionFactory(currentProperty);
    this.jdbcConnection = DBFactory.getDBConnection();
  }

  @Override
  public void open() {
    jdbcConnection.open();
  }

  @Override
  public void close() {
    jdbcConnection.close();
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Run SQL command '" + cmd + "'");
    return jdbcConnection.executeSql(cmd);
  }

  @Override
  public void cancel(InterpreterContext context) {
    jdbcConnection.cancel();
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
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        JDBCInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
