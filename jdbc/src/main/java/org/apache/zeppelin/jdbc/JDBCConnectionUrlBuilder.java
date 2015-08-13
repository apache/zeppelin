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

/**
 * JDBC connection url builder for Zeppelin.
 *
 * @author Andres  Celis t-ancel@microsoft.com
 *
 */

// add case and connection url method to support other jdbc backends
public class JDBCConnectionUrlBuilder {

  private String connectionUrl;  

  public JDBCConnectionUrlBuilder(String driverType, String host, String port, 
                                  String dbName, String windowsAuth) {
    // determine format
    switch(driverType) {
        case "sqlserver":
          buildSqlserverConnectionUrl(host, port, dbName, windowsAuth);
          break;
        case "postgresql":
          buildPostgresqlConnectionUrl(host, port, dbName);
          break;
        case "mysql":
          buildMysqlConnectionUrl(host, port, dbName);
          break;
        default:
          this.connectionUrl = null;
    }
  }
  
  private void buildSqlserverConnectionUrl(String host, String port, 
                                           String dbName, String windowsAuth) {
    this.connectionUrl = "jdbc:sqlserver://";
    this.connectionUrl += (host.equals("")) ? "localhost" : host;
    this.connectionUrl += (port.equals("")) ? ":1433;" : ":" + port + ";"; 
    this.connectionUrl += (dbName.equals("")) ? "" : "database=" + dbName + ";";
    // assume false or empty is SQL authentication
    this.connectionUrl += (windowsAuth.equals("true")) ? "integratedsecurity=true;" : "";
  }

  private void buildPostgresqlConnectionUrl(String host, String port, String dbName) {
    this.connectionUrl = "jdbc:postgresql://";
    this.connectionUrl += (host.equals("")) ? "localhost" : host;
    this.connectionUrl += (port.equals("")) ? "" : ":" + port; 
    this.connectionUrl += (dbName.equals("")) ? "" : "/" + dbName;
  }

  private void buildMysqlConnectionUrl(String host, String port, String dbName) {
    this.connectionUrl = "jdbc:mysql://";
    this.connectionUrl += host;
    this.connectionUrl += (port.equals("")) ? "" : ":" + port; 
    this.connectionUrl += (dbName.equals("")) ? "" : "/" + dbName;
  }

  public String getConnectionUrl() {
    return this.connectionUrl;
  }
}
