/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.jdbc;

import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.zeppelin.user.UsernamePassword;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * UserConfigurations for JDBC impersonation.
 */
public class JDBCUserConfigurations {
  private final Map<String, Statement> paragraphIdStatementMap;
  private PoolingDriver poolingDriver;
  private Properties properties;
  private Boolean isSuccessful;

  public JDBCUserConfigurations() {
    paragraphIdStatementMap = new HashMap<>();
  }

  public void initStatementMap() throws SQLException {
    for (Statement statement : paragraphIdStatementMap.values()) {
      statement.close();
    }
    paragraphIdStatementMap.clear();
  }

  public void initConnectionPoolMap() throws SQLException {
    this.poolingDriver = null;
    this.isSuccessful = null;
  }

  public void setProperty(Properties properties) {
    this.properties = (Properties) properties.clone();
  }

  public Properties getProperty() {
    return this.properties;
  }

  public void cleanUserProperty() {
    this.properties.remove("user");
    this.properties.remove("password");
  }

  public void setUserProperty(UsernamePassword usernamePassword) {
    this.properties.setProperty("user", usernamePassword.getUsername());
    this.properties.setProperty("password", usernamePassword.getPassword());
  }

  public void saveStatement(String paragraphId, Statement statement) throws SQLException {
    paragraphIdStatementMap.put(paragraphId, statement);
  }

  public void cancelStatement(String paragraphId) throws SQLException {
    paragraphIdStatementMap.get(paragraphId).cancel();
  }

  public void removeStatement(String paragraphId) {
    paragraphIdStatementMap.remove(paragraphId);
  }

  public void saveDBDriverPool(PoolingDriver driver) throws SQLException {
    this.poolingDriver = driver;
    this.isSuccessful = false;
  }

  public PoolingDriver removeDBDriverPool() throws SQLException {
    this.isSuccessful = null;
    PoolingDriver tmp = poolingDriver;
    this.poolingDriver = null;
    return tmp;
  }

  public boolean isConnectionInDBDriverPool() {
    return this.poolingDriver != null;
  }

  public void setConnectionInDBDriverPoolSuccessful() {
    this.isSuccessful = true;
  }
}
