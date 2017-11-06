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
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * UserConfigurations for JDBC impersonation.
 */
public class JDBCUserConfigurations {
  private final Map<String, Statement> paragraphIdStatementMap;
  private final Map<String, PoolingDriver> poolingDriverMap;
  private final HashMap<String, Properties> propertiesMap;
  private HashMap<String, Boolean> isSuccessful;

  public JDBCUserConfigurations() {
    paragraphIdStatementMap = new HashMap<>();
    poolingDriverMap = new HashMap<>();
    propertiesMap = new HashMap<>();
    isSuccessful = new HashMap<>();
  }

  public void initStatementMap() throws SQLException {
    for (Statement statement : paragraphIdStatementMap.values()) {
      statement.close();
    }
    paragraphIdStatementMap.clear();
  }

  public void initConnectionPoolMap() throws SQLException {
    poolingDriverMap.clear();
    isSuccessful.clear();
  }

  public void setPropertyMap(String key, Properties properties) {
    Properties p = (Properties) properties.clone();
    propertiesMap.put(key, p);
  }

  public Properties getPropertyMap(String key) {
    return propertiesMap.get(key);
  }

  public void cleanUserProperty(String propertyKey) {
    propertiesMap.get(propertyKey).remove("user");
    propertiesMap.get(propertyKey).remove("password");
  }

  public void setUserProperty(String propertyKey, UsernamePassword usernamePassword) {
    propertiesMap.get(propertyKey).setProperty("user", usernamePassword.getUsername());
    propertiesMap.get(propertyKey).setProperty("password", usernamePassword.getPassword());
  }

  public void saveStatement(String key, Statement statement) throws SQLException {
    paragraphIdStatementMap.put(key, statement);
  }

  public void cancelStatement(String key) throws SQLException {
    paragraphIdStatementMap.get(key).cancel();
  }

  public void removeStatement(String key) {
    paragraphIdStatementMap.remove(key);
  }

  public void saveDBDriverPool(String key, PoolingDriver driver) throws SQLException {
    poolingDriverMap.put(key, driver);
    isSuccessful.put(key, false);
  }
  public PoolingDriver removeDBDriverPool(String key) throws SQLException {
    isSuccessful.remove(key);
    return poolingDriverMap.remove(key);
  }

  public boolean isConnectionInDBDriverPool(String key) {
    return poolingDriverMap.containsKey(key);
  }

  public void setConnectionInDBDriverPoolSuccessful(String key) {
    isSuccessful.put(key, true);
  }

  public boolean isConnectionInDBDriverPoolSuccessful(String key) {
    if (isSuccessful.containsKey(key)) {
      return isSuccessful.get(key);
    }
    return false;
  }

}
