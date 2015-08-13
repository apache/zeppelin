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

import java.util.Properties;
import java.util.logging.Logger;
import java.sql.*;

/**
 * JDBC interpreter for Zeppelin.
 *
 * @author Andres  Celis t-ancel@microsoft.com
 *
 */
public class DriverShim implements Driver {
  private Driver driver;
  DriverShim(Driver d) {
    this.driver = d;
  }
  public boolean acceptsURL(String u) throws SQLException {
    return this.driver.acceptsURL(u);
  }
  public Connection connect(String u, Properties p) throws SQLException {
    return this.driver.connect(u, p);
  }
  public int getMajorVersion() {
    return this.driver.getMajorVersion();
  }
  public int getMinorVersion() {
    return this.driver.getMinorVersion();
  }
  public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
    return this.driver.getPropertyInfo(u, p);
  }
  public Logger getParentLogger() {
    return null;
  }
  public boolean jdbcCompliant() {
    return this.driver.jdbcCompliant();
  }
}
