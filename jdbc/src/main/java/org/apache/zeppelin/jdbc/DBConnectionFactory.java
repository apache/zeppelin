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

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;

/**
 * DBConnectionFactory
 *
 * @author Hyungu Roh hyungu.roh@navercorp.com
 *
 */

public class DBConnectionFactory {
  Logger logger = LoggerFactory.getLogger(DBConnectionFactory.class);

  String jdbc = "";
  String host = "";
  String port = "";
  String user = "";
  String password = "";

  public DBConnectionFactory(Properties currentProperty) {
    for (Object k : currentProperty.keySet()) {
      String key = (String) k;
      String value = (String) currentProperty.get(key);

      if ( key.equals("jdbc") ) {
        this.jdbc = value.toLowerCase();
      } else if ( key.equals("host") ) {
        this.host = value;
      } else if ( key.equals("password") ) {
        this.password = value;
      } else if ( key.equals("user") ) {
        this.user = value;
      } else if ( key.equals("port") ) {
        this.port = value;
      } else {
        logger.info("else key : " +  key);
      }
    }
  }

  public DBConnection getDBConnection() {
    DBConnection currentConnection;

    // add other jdbc
    if ( jdbc.equals("mysql") ) {
      currentConnection = new MysqlConnection(host, port, user, password);
    } else {
      currentConnection = null;
    }

    return currentConnection;
  }
}

