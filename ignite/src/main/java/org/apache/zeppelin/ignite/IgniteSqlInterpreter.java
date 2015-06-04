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
 */
package org.apache.zeppelin.ignite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.ignite.IgniteJdbcDriver;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class IgniteSqlInterpreter extends Interpreter {
  static {
    Interpreter.register(
        "ignitesql",
        "ignite",
        IgniteSqlInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("url", "localhost:11211/", "url for jdbc driver")
            .build());
  }

  Logger logger = LoggerFactory.getLogger(IgniteSqlInterpreter.class);
  private Connection conn;

  public IgniteSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    IgniteJdbcDriver jdbcDriver = new IgniteJdbcDriver();
    try {
      logger.info("connect to jdbc:ignite://" + getProperty("url"));
      conn = jdbcDriver.connect("jdbc:ignite://" + getProperty("url"), getProperty());
    } catch (SQLException e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void close() {
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException e) {
        throw new InterpreterException(e);
      }
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    StringBuilder msg = new StringBuilder("%table ");

    try {
      Statement stmt = conn.createStatement();
      ResultSet res = stmt.executeQuery(st);

      ResultSetMetaData md = res.getMetaData();
      try {
        for (int i = 1; i < md.getColumnCount() + 1; i++) {
          if (i == 1) {
            msg.append(md.getColumnName(i));
          } else {
            msg.append("\t" + md.getColumnName(i));
          }
        }
        msg.append("\n");
        while (res.next()) {
          for (int i = 1; i < md.getColumnCount() + 1; i++) {
            msg.append(res.getString(i));
            if (i != md.getColumnCount()) {
              msg.append("\t");
            }
          }
          msg.append("\n");
        }
      } finally {
        try {
          res.close();
          stmt.close();
        } finally {
          stmt = null;
        }
      }
    } catch (SQLException e) {
      throw new InterpreterException(e);
    }

    return new InterpreterResult(Code.SUCCESS, msg.toString());
  }



  @Override
  public void cancel(InterpreterContext context) {
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
  public List<String> completion(String buf, int cursor) {
    return new LinkedList<String>();
  }

  private IgniteInterpreter getIgniteInterpreter() {
    for (Interpreter intp : getInterpreterGroup()) {
      if (intp.getClassName().equals(IgniteInterpreter.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          if (p instanceof LazyOpenInterpreter) {
            p.open();
          }
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (IgniteInterpreter) p;
      }
    }
    return null;
  }

}
